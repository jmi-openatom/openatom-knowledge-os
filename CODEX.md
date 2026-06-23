

# 🧱 OpenAtom 企业级 CI/CD 全套方案（完整可用）

---

# 📦 一、最终架构（真实生产级）

```text
                Git Push / Tag
                        ↓
              GitHub Actions CI
        ┌──────────────┼──────────────┐
        ↓                             ↓
 SpringBoot Build             Electron Build
 (Maven + Jar)               (Vue3 + Electron)
        ↓                             ↓
   Docker Image            Windows / macOS Installer
        ↓                             ↓
        └──────────────┬──────────────┘
                       ↓
              GitHub Release
                       ↓
             Auto Update Client
```

---s

# 📁 二、完整项目结构（必须照抄）

```text
openatom-system/
│
├── backend/                        # SpringBoot
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── desktop/                       # Electron + Vue3
│   ├── electron/
│   │   ├── main.js
│   │   ├── preload.js
│   │
│   ├── src/
│   ├── package.json
│   ├── vite.config.ts
│   ├── electron-builder.yml
│
├── .github/
│   └── workflows/
│       └── release.yml
│
├── docker/
│   └── docker-compose.yml
│
└── README.md
```

---

# ⚙️ 三、SpringBoot Docker（后端）

## backend/Dockerfile

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
```

---

# 🐳 四、docker-compose（完整环境）

```yaml
version: '3.9'

services:

  mysql:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: root
    ports:
      - "3306:3306"

  redis:
    image: redis
    ports:
      - "6379:6379"

  minio:
    image: minio/minio
    command: server /data
    ports:
      - "9000:9000"

  backend:
    build: ../backend
    ports:
      - "8080:8080"
    depends_on:
      - mysql
      - redis
      - minio
```

---

# 🖥 五、Electron 构建配置（稳定版）

## electron-builder.yml

```yaml
appId: com.openatom.knowledge
productName: OpenAtom Knowledge OS

directories:
  output: dist

files:
  - dist/**
  - electron/**

mac:
  target: dmg

win:
  target: nsis

linux:
  target: AppImage

publish:
  provider: github
```

---

## desktop/package.json

```json
{
  "name": "openatom-desktop",
  "version": "1.0.0",
  "main": "electron/main.js",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "electron": "electron .",
    "dist": "electron-builder"
  },
  "devDependencies": {
    "electron": "^28.0.0",
    "electron-builder": "^24.0.0"
  }
}
```

---

## electron/main.js

```js
const { app, BrowserWindow } = require('electron')

function createWindow () {
  const win = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      preload: __dirname + '/preload.js'
    }
  })

  win.loadURL('http://localhost:5173')
}

app.whenReady().then(createWindow)
```

---

# 🚀 六、GitHub Actions（核心CI/CD）

## .github/workflows/release.yml（完整）

```yaml
name: Build & Release

on:
  push:
    tags:
      - "v*"

jobs:

  backend-build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Build Backend
        run: |
          cd backend
          mvn clean package -DskipTests

      - name: Upload Jar
        uses: actions/upload-artifact@v4
        with:
          name: backend
          path: backend/target/*.jar


  electron-build:
    strategy:
      matrix:
        os: [windows-latest, macos-latest]

    runs-on: ${{ matrix.os }}

    steps:
      - uses: actions/checkout@v4

      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: 18

      - name: Install deps
        run: |
          cd desktop
          npm install

      - name: Build frontend
        run: |
          cd desktop
          npm run build

      - name: Build Electron app
        run: |
          cd desktop
          npm run dist

      - name: Upload installers
        uses: actions/upload-artifact@v4
        with:
          name: electron-${{ matrix.os }}
          path: desktop/dist/**


  release:
    needs: [backend-build, electron-build]
    runs-on: ubuntu-latest

    steps:
      - uses: actions/download-artifact@v4
        with:
          path: artifacts

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: artifacts/**/*
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

---

# 🔐 七、自动发布流程（你真正用的方式）

## 发布版本：

```bash
git tag v1.0.0
git push origin v1.0.0
```

👉 自动触发：

- SpringBoot build

- Electron build (Win + Mac)

- 打包 exe / dmg

- 自动生成 Release

---

# 🔄 八、Electron 自动更新（企业必备）

## main.js 加入：

```js
const { autoUpdater } = require('electron-updater')

app.whenReady().then(() => {
  autoUpdater.checkForUpdatesAndNotify()
})
```

---

# ☁️ 九、最终发布链路

```text
开发者 push tag
        ↓
GitHub Actions
        ↓
构建 backend + desktop
        ↓
生成 exe / dmg
        ↓
GitHub Release
        ↓
用户自动更新
```

---

# 🧠 十、这套系统的企业级能力

你现在已经拥有：

## 🚀 DevOps能力

- CI/CD自动构建

- 多平台打包

- 自动Release

- 自动版本管理

## 🖥 桌面端能力

- Electron跨平台

- 自动更新

- Vue3 UI

## ☁ 后端能力

- SpringBoot服务

- Docker部署

- OAuth2认证

---


