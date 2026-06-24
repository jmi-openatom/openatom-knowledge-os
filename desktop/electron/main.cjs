const { app, BrowserWindow, ipcMain, safeStorage, shell, Menu } = require('electron')
const { autoUpdater } = require('electron-updater')
const crypto = require('node:crypto')
const fs = require('node:fs')
const http = require('node:http')
const path = require('node:path')
const { exec } = require('node:child_process')

const isDev = !app.isPackaged
const isMac = process.platform === 'darwin'
const isAutoUpdateEnabled = !isDev && !isMac
const issuer = process.env.OPENATOM_OIDC_ISSUER || 'https://oauth.jmi-openatom.cn/api/v1'
const clientId = process.env.OPENATOM_OIDC_CLIENT_ID || 'openatom-knowledge-desktop'
const callbackPort = Number(process.env.OPENATOM_OIDC_CALLBACK_PORT || 47832)
const redirectUri = `http://127.0.0.1:${callbackPort}/auth/callback`

let mainWindow
let pendingLogin
let previewWindow = null
let adminWindow = null

function createPreviewWindow(fileId) {
  // If a preview window already exists, just navigate it to the new file
  if (previewWindow && !previewWindow.isDestroyed()) {
    previewWindow.focus()
    previewWindow.webContents.send('preview:navigate', { fileId })
    return
  }

  previewWindow = new BrowserWindow({
    width: 1024,
    height: 768,
    minWidth: 640,
    minHeight: 480,
    title: '文件预览',
    autoHideMenuBar: true,
    backgroundColor: '#fafafa',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  if (isDev) {
    previewWindow.loadURL(`http://127.0.0.1:5173/#/preview/${fileId}`)
  } else {
    previewWindow.loadFile(path.join(__dirname, '../dist/index.html'), { hash: `/preview/${fileId}` })
  }

  previewWindow.on('closed', () => {
    previewWindow = null
  })
}

function createAdminWindow() {
  if (adminWindow && !adminWindow.isDestroyed()) {
    adminWindow.focus()
    return
  }

  const sessionData = readSession()
  const token = sessionData?.access_token || ''
  const adminUrl = 'https://www.jmi-openatom.cn/admin'

  const { session: electronSession } = require('electron')
  const adminPartition = 'persist:admin'
  const adminSession = electronSession.fromPartition(adminPartition)

  adminWindow = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 800,
    minHeight: 600,
    title: '管理后台',
    autoHideMenuBar: true,
    backgroundColor: '#fff',
    webPreferences: {
      partition: adminPartition,
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  if (token) {
    // 1. Set cookies on admin domain
    const cookieUrl = 'https://www.jmi-openatom.cn'
    const cookieNames = ['token', 'access_token', 'auth_token', 'jwt', 'Authorization']
    for (const name of cookieNames) {
      const value = name === 'Authorization' ? `Bearer ${token}` : token
      adminSession.cookies.set({ url: cookieUrl, name, value, path: '/', secure: true, httpOnly: false, expirationDate: Math.floor(Date.now() / 1000) + 86400 })
        .catch((err) => console.warn(`[Admin] Cookie '${name}' failed:`, err.message))
    }

    // 2. Inject Authorization header on all API requests
    adminSession.webRequest.onBeforeSendHeaders(
      { urls: ['https://www.jmi-openatom.cn/*', 'https://*.jmi-openatom.cn/*'] },
      (details, callback) => {
        details.requestHeaders['Authorization'] = `Bearer ${token}`
        callback({ requestHeaders: details.requestHeaders })
      }
    )

    // 3. After page loads, inject token into localStorage / sessionStorage
    adminWindow.webContents.on('did-finish-load', () => {
      const injectScript = `
        (function() {
          try {
            var token = ${JSON.stringify(token)};
            var user = ${JSON.stringify(sessionData?.user || null)};
            // Try common localStorage keys used by SPA admin panels
            var storageKeys = ['token', 'access_token', 'auth_token', 'jwt_token', 'Authorization'];
            storageKeys.forEach(function(key) {
              var val = key === 'Authorization' ? 'Bearer ' + token : token;
              localStorage.setItem(key, val);
              sessionStorage.setItem(key, val);
            });
            // Try setting user info
            if (user) {
              localStorage.setItem('user', JSON.stringify(user));
              sessionStorage.setItem('user', JSON.stringify(user));
            }
            // Try common auth store patterns
            try {
              var authData = { access_token: token, token_type: 'Bearer', user: user };
              localStorage.setItem('auth', JSON.stringify(authData));
              localStorage.setItem('authData', JSON.stringify(authData));
              localStorage.setItem('session', JSON.stringify(authData));
            } catch(e) {}
            console.log('[OpenAtom Desktop] Auth token injected');
          } catch(e) {
            console.warn('[OpenAtom Desktop] Token injection failed:', e);
          }
        })();
      `
      adminWindow.webContents.executeJavaScript(injectScript).catch(() => {})
    })

    // 4. Also inject on every navigation (for SPA routing)
    adminWindow.webContents.on('did-navigate-in-page', () => {
      adminWindow.webContents.executeJavaScript(`
        try {
          var t = ${JSON.stringify(token)};
          ['token','access_token','auth_token','jwt_token'].forEach(k => {
            if (!localStorage.getItem(k)) localStorage.setItem(k, t);
          });
        } catch(e) {}
      `).catch(() => {})
    })
  }

  adminWindow.loadURL(adminUrl)

  adminWindow.on('closed', () => {
    adminWindow = null
  })
}

function base64Url(buffer) {
  return Buffer.from(buffer)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
}

function sessionPath() {
  return path.join(app.getPath('userData'), 'oauth-session.bin')
}

function saveSession(session) {
  const plain = Buffer.from(JSON.stringify(session), 'utf8')
  const payload = safeStorage.isEncryptionAvailable()
    ? safeStorage.encryptString(plain.toString('utf8'))
    : plain
  fs.writeFileSync(sessionPath(), payload)
}

function readSession() {
  try {
    const payload = fs.readFileSync(sessionPath())
    const plain = safeStorage.isEncryptionAvailable()
      ? safeStorage.decryptString(payload)
      : payload.toString('utf8')
    return JSON.parse(plain)
  } catch {
    return null
  }
}

function clearSession() {
  try {
    fs.rmSync(sessionPath(), { force: true })
  } catch {
    // Ignore a missing or locked session file during logout.
  }
}

async function exchangeToken(params) {
  console.log('[OAuth] 正在请求令牌:', `${issuer}/oauth/token`)
  let response
  try {
    response = await fetch(`${issuer}/oauth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(params),
    })
  } catch (networkError) {
    console.error('[OAuth] 网络请求失败:', networkError)
    throw new Error(`无法连接 OAuth 服务器 (${issuer})：${networkError.message}`)
  }
  if (!response.ok) {
    const body = await response.text()
    console.error('[OAuth] 令牌请求失败:', response.status, body)
    throw new Error(`OAuth 令牌请求失败（${response.status}）：${body || '未知错误'}`)
  }
  const json = await response.json()
  // OAuth server wraps response in a "data" field
  return json.data || json
}

async function fetchUser(accessToken) {
  console.log('[OAuth] 正在获取用户信息...')
  let response
  try {
    response = await fetch(`${issuer}/oauth/userinfo`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
  } catch (networkError) {
    console.error('[OAuth] 获取用户信息失败:', networkError)
    throw new Error(`无法连接 OAuth 用户信息接口：${networkError.message}`)
  }
  if (!response.ok) {
    const body = await response.text()
    console.error('[OAuth] 用户信息请求失败:', response.status, body)
    throw new Error(`OAuth 用户信息同步失败（${response.status}）`)
  }
  const json = await response.json()
  // OAuth server wraps response in a "data" field
  return json.data || json
}

async function startPkceLogin() {
  if (pendingLogin) return pendingLogin

  pendingLogin = new Promise((resolve, reject) => {
    const codeVerifier = base64Url(crypto.randomBytes(48))
    const codeChallenge = base64Url(crypto.createHash('sha256').update(codeVerifier).digest())
    const state = base64Url(crypto.randomBytes(24))
    const nonce = base64Url(crypto.randomBytes(24))

    let resolved = false

    const server = http.createServer(async (request, response) => {
      const reqUrl = request.url || '/'
      console.log(`[OAuth] 收到请求: ${request.method} ${reqUrl} from ${request.socket.remoteAddress}`)

      // Add CORS headers for all responses (helps with cross-origin redirects on Windows)
      response.setHeader('Access-Control-Allow-Origin', '*')
      response.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
      response.setHeader('Access-Control-Allow-Headers', '*')
      if (request.method === 'OPTIONS') {
        response.writeHead(204)
        response.end()
        return
      }

      try {
        const url = new URL(reqUrl, redirectUri)
        
        // 处理 /login 路径：重定向到 OAuth 服务器的登录页面
        if (url.pathname === '/login') {
          console.log('[OAuth] 收到 /login 请求，重定向到 OAuth 服务器')
          const oauthLoginUrl = `${issuer.replace('/api/v1', '')}/login${url.search}`
          response.writeHead(302, { 'Location': oauthLoginUrl })
          response.end()
          return
        }
        
        // 只处理 OAuth 回调路径
        if (url.pathname !== '/auth/callback') {
          console.log('[OAuth] 非回调请求:', url.pathname, '- 忽略')
          response.writeHead(404, { 'Content-Type': 'text/plain' })
          response.end('Not found')
          return
        }
        
        console.log('[OAuth] ✅ 收到回调请求，查询参数:', url.search)
        if (url.searchParams.get('state') !== state) {
          throw new Error('OAuth state 校验失败，请重新登录')
        }
        const error = url.searchParams.get('error')
        if (error) throw new Error(`OAuth 授权失败：${error}`)
        const code = url.searchParams.get('code')
        if (!code) throw new Error('OAuth 回调缺少授权码')

        console.log('[OAuth] 收到授权码，正在交换令牌...')
        const tokens = await exchangeToken({
          grant_type: 'authorization_code',
          client_id: clientId,
          code,
          redirect_uri: redirectUri,
          code_verifier: codeVerifier,
        })
        console.log('[OAuth] ✅ 令牌交换成功')
        const user = tokens.user || await fetchUser(tokens.access_token)
        console.log('[OAuth] ✅ 用户信息获取成功:', user?.name || user?.id)
        const session = {
          ...tokens,
          user,
          obtained_at: Date.now(),
        }
        saveSession(session)
        response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
        response.end('<!doctype html><meta charset="utf-8"><title>登录成功</title><style>body{font-family:system-ui;padding:48px;text-align:center}h1{font-size:24px}</style><h1>登录成功</h1><p>可以关闭此窗口并返回 OpenAtom Knowledge OS。</p>')
        server.close()
        if (mainWindow) {
          mainWindow.show()
          mainWindow.focus()
        }
        resolved = true
        resolve({ user, expiresIn: tokens.expires_in })
      } catch (error) {
        console.error('[OAuth] ❌ 登录失败:', error)
        const msg = error instanceof Error ? error.message : '登录失败'
        response.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' })
        response.end(msg)
        server.close()
        resolved = true
        reject(error)
      } finally {
        pendingLogin = null
      }
    })

    server.on('error', (error) => {
      console.error('[OAuth] ❌ 服务器错误:', error)
      if (!resolved) {
        pendingLogin = null
        reject(new Error(`无法启动 OAuth 回调监听端口 ${callbackPort}：${error.message}`))
      }
    })

    // Listen on all interfaces (both IPv4 and IPv6) for maximum compatibility
    // This fixes Windows where localhost may resolve to ::1 (IPv6)
    server.listen(callbackPort, '::', async () => {
      const addr = server.address()
      console.log(`[OAuth] 回调服务器已启动，监听: ${JSON.stringify(addr)}`)

      const params = new URLSearchParams({
        response_type: 'code',
        client_id: clientId,
        redirect_uri: redirectUri,
        scope: 'openid profile email roles permissions',
        state,
        nonce,
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
      })
      const authUrl = `${issuer}/oauth/authorize?${params}`
      console.log('[OAuth] 授权 URL:', authUrl)
      console.log('[OAuth] 正在打开系统浏览器...')
      
      try {
        await shell.openExternal(authUrl)
        console.log('[OAuth] ✅ 已打开系统浏览器')
      } catch (err) {
        console.error('[OAuth] ❌ 打开浏览器失败:', err)
        // On Windows, try cmd.exe start as fallback
        if (process.platform === 'win32') {
          exec(`start "" "${authUrl}"`, (error) => {
            if (error) {
              console.error('[OAuth] ❌ cmd start 也失败:', error)
            } else {
              console.log('[OAuth] ✅ 已通过 cmd start 打开浏览器')
            }
          })
        }
      }
    })

    setTimeout(() => {
      if (!resolved && server.listening) {
        console.error('[OAuth] ❌ 登录超时（90秒）')
        server.close()
        pendingLogin = null
        reject(new Error('OAuth 登录已超时，请重新尝试'))
      }
    }, 90_000)
  })

  return pendingLogin
}

async function refreshSession() {
  const session = readSession()
  if (!session?.refresh_token) return null
  const tokens = await exchangeToken({
    grant_type: 'refresh_token',
    client_id: clientId,
    refresh_token: session.refresh_token,
  })
  const user = tokens.user || session.user || await fetchUser(tokens.access_token)
  const next = { ...tokens, user, obtained_at: Date.now() }
  saveSession(next)
  return { user, expiresIn: tokens.expires_in }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1492,
    height: 1054,
    minWidth: 1120,
    minHeight: 720,
    show: false,
    frame: isMac ? undefined : false,
    titleBarStyle: isMac ? 'hiddenInset' : 'hidden',
    trafficLightPosition: isMac ? { x: 12, y: 10 } : undefined,
    autoHideMenuBar: true,
    backgroundColor: '#fafafa',
    icon: path.join(__dirname, '..', 'resources', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  // Remove default menu bar (File/Edit/View/Window/Help) on Windows/Linux
  if (process.platform !== 'darwin') {
    Menu.setApplicationMenu(null)
  }

  if (isDev) {
    mainWindow.loadURL('http://127.0.0.1:5173')
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'))
  }

  mainWindow.once('ready-to-show', () => mainWindow.show())
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })
}

ipcMain.handle('preview:open', (_event, fileId) => {
  if (!fileId) return
  createPreviewWindow(fileId)
})
ipcMain.handle('admin:open', () => {
  createAdminWindow()
})

// Window control IPC handlers
ipcMain.handle('window:minimize', () => {
  mainWindow?.minimize()
})
ipcMain.handle('window:maximize', () => {
  if (mainWindow?.isMaximized()) {
    mainWindow.unmaximize()
  } else {
    mainWindow?.maximize()
  }
})
ipcMain.handle('window:isMaximized', () => {
  return mainWindow?.isMaximized() ?? false
})
ipcMain.handle('window:close', () => {
  mainWindow?.close()
})

ipcMain.handle('auth:login', () => {
  // Always reset pending state so a fresh login attempt starts
  pendingLogin = null
  return startPkceLogin()
})
ipcMain.handle('auth:login-reset', () => {
  pendingLogin = null
  console.log('[OAuth] 登录状态已重置，可重新尝试')
  return true
})
ipcMain.handle('auth:logout', () => {
  clearSession()
  return true
})
ipcMain.handle('auth:get-session', () => {
  const session = readSession()
  return session ? { user: session.user, expiresIn: session.expires_in, obtainedAt: session.obtained_at } : null
})
ipcMain.handle('auth:get-access-token', () => readSession()?.access_token || null)
ipcMain.handle('auth:refresh', refreshSession)
ipcMain.handle('updater:check', async () => {
  if (isDev) return { status: 'disabled-in-development' }
  if (isMac) return { status: 'disabled-on-macos' }
  return autoUpdater.checkForUpdates()
})
ipcMain.handle('updater:install', () => {
  if (isAutoUpdateEnabled) autoUpdater.quitAndInstall()
})
ipcMain.handle('app:get-version', () => app.getVersion())

autoUpdater.autoDownload = true
autoUpdater.autoInstallOnAppQuit = true

autoUpdater.on('checking-for-update', () => mainWindow?.webContents.send('updater:status', { status: 'checking' }))
autoUpdater.on('update-available', (info) => mainWindow?.webContents.send('updater:status', { status: 'available', info }))
autoUpdater.on('update-not-available', () => mainWindow?.webContents.send('updater:status', { status: 'current' }))
autoUpdater.on('error', (error) => mainWindow?.webContents.send('updater:status', { status: 'error', message: error.message }))
autoUpdater.on('download-progress', (progress) => mainWindow?.webContents.send('updater:status', {
  status: 'downloading',
  percent: Math.round(progress.percent),
  transferred: progress.transferred,
  total: progress.total,
}))
autoUpdater.on('update-downloaded', (info) => mainWindow?.webContents.send('updater:status', { status: 'downloaded', info }))

app.whenReady().then(() => {
  createWindow()
  if (isAutoUpdateEnabled) autoUpdater.checkForUpdates()
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow()
})
