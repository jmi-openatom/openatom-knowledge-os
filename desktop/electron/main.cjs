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
  const response = await fetch(`${issuer}/oauth/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(params),
  })
  if (!response.ok) {
    const body = await response.text()
    throw new Error(`OAuth 令牌请求失败（${response.status}）：${body || '未知错误'}`)
  }
  const json = await response.json()
  // OAuth server wraps response in a "data" field
  return json.data || json
}

async function fetchUser(accessToken) {
  const response = await fetch(`${issuer}/oauth/userinfo`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!response.ok) throw new Error('OAuth 用户信息同步失败')
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

    const server = http.createServer(async (request, response) => {
      try {
        const url = new URL(request.url || '/', redirectUri)
        
        // 处理 /login 路径：重定向到 OAuth 服务器的登录页面
        if (url.pathname === '/login') {
          console.log('[OAuth] 收到 /login 请求，重定向到 OAuth 服务器')
          const oauthLoginUrl = `${issuer.replace('/api/v1', '')}/login${url.search}`
          response.writeHead(302, { 'Location': oauthLoginUrl })
          response.end()
          return
        }
        
        // 只处理 OAuth 回调路径，其他请求不响应（让浏览器等待或超时）
        if (url.pathname !== '/auth/callback') {
          console.log('[OAuth] 收到非回调请求:', url.pathname, '- 忽略此请求')
          // 不返回任何内容，让浏览器继续等待 OAuth 服务器的响应
          return
        }
        
        console.log('[OAuth] 收到回调请求，查询参数:', url.search)
        if (url.searchParams.get('state') !== state) {
          throw new Error('OAuth state 校验失败，请重新登录')
        }
        const error = url.searchParams.get('error')
        if (error) throw new Error(`OAuth 授权失败：${error}`)
        const code = url.searchParams.get('code')
        if (!code) throw new Error('OAuth 回调缺少授权码')

        const tokens = await exchangeToken({
          grant_type: 'authorization_code',
          client_id: clientId,
          code,
          redirect_uri: redirectUri,
          code_verifier: codeVerifier,
        })
        const user = tokens.user || await fetchUser(tokens.access_token)
        console.log('[OAuth] Token response keys:', Object.keys(tokens))
        console.log('[OAuth] User info from OAuth:', JSON.stringify(user, null, 2))
        const session = {
          ...tokens,
          user,
          obtained_at: Date.now(),
        }
        saveSession(session)
        response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
        response.end('<!doctype html><meta charset="utf-8"><title>登录成功</title><style>body{font-family:system-ui;padding:48px;text-align:center}h1{font-size:24px}</style><h1>登录成功</h1><p>可以关闭此窗口并返回 OpenAtom Knowledge OS。</p>')
        server.close()
        mainWindow?.show()
        mainWindow?.focus()
        resolve({ user, expiresIn: tokens.expires_in })
      } catch (error) {
        response.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' })
        response.end(error instanceof Error ? error.message : '登录失败')
        server.close()
        reject(error)
      } finally {
        pendingLogin = null
      }
    })

    server.on('error', (error) => {
      pendingLogin = null
      reject(new Error(`无法启动 OAuth 回调监听端口 ${callbackPort}：${error.message}`))
    })

    server.listen(callbackPort, '127.0.0.1', async () => {
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
      console.log('[OAuth] 正在使用系统浏览器打开授权页面...')
      
      // 使用 macOS 的 open 命令在默认浏览器中打开
      if (process.platform === 'darwin') {
        exec(`open "${authUrl}"`, (error) => {
          if (error) {
            console.error('[OAuth] 打开浏览器失败:', error)
            // 如果 open 命令失败，尝试 shell.openExternal
            shell.openExternal(authUrl).catch(err => {
              console.error('[OAuth] shell.openExternal 也失败了:', err)
              console.log('[OAuth] 请手动复制上面的 URL 并在浏览器中访问')
            })
          } else {
            console.log('[OAuth] 已在默认浏览器中打开授权页面')
          }
        })
      } else {
        // Windows/Linux 使用 shell.openExternal
        shell.openExternal(authUrl).catch(err => {
          console.error('[OAuth] 打开浏览器失败:', err)
          console.log('[OAuth] 请手动复制上面的 URL 并在浏览器中访问')
        })
      }
    })

    setTimeout(() => {
      if (server.listening) {
        server.close()
        pendingLogin = null
        reject(new Error('OAuth 登录已超时，请重新尝试'))
      }
    }, 5 * 60 * 1000)
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
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'default',
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

ipcMain.handle('auth:login', startPkceLogin)
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
