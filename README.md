# Daily English

一个面向 CET-6 与考研英语的日常学习站：合并词库与句子、间隔复习、真题练习、翻译自评、
账号同步、PWA 离线与自动暗夜模式。

Web 前端、Go 后端与原生 Android 客户端整合在同一个仓库里，共用一份语料、一套 API
和一条 CI/CD 流水线。

## 功能

- **合并词库**：7,291 个大纲词，CET-6 与考研共用一份学习进度
- **句子与真题**：253 句长难句/翻译段落，44 套考研英语真题
- **学习闭环**：闪卡、拼写、词库浏览、到期复习、句子自评、真题批改
- **账号同步**：邮箱注册/登录、跨设备进度同步、409 合并、备份导入导出
- **PWA**：离线缓存、安装到桌面、Service Worker 自动更新
- **界面**：纸页与页边标注风格，响应式侧栏/底部导航，19:00–07:00 自动暗夜

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5、Vue Router、Vite 7、vite-plugin-pwa |
| 后端 | Go 1.27、标准库 net/http |
| 安卓 | Kotlin 2.3、Jetpack Compose、Hilt、Room、Retrofit、Gradle 9 |
| 数据库 | SQLite（服务端 `modernc.org/sqlite` 纯 Go；客户端 Room） |
| 认证 | scrypt 密码哈希、HttpOnly Cookie / Bearer token |
| 部署 | 单二进制 + systemd + nginx 静态托管与 `/api` 反代 |
| CI/CD | GitHub Actions：三路 CI、手动生产部署、安卓发布 |

## 目录

```
frontend/        Vue 3 前端（PWA）
backend/         Go API 与 SQLite
android/         原生 Android 客户端（Quiet Glass）
content/         生成后的词库、句子与真题 JSON（三端共用）
deploy/          systemd / nginx 模板、打包与部署脚本
.github/         CI、部署与安卓发布工作流
docs/            架构、部署、API 与 CI/CD 说明
ATTRIBUTIONS.md  内容来源与许可证
LICENSE          代码 MIT 许可证
```

## 本地运行

要求：Node.js 22+、Go 1.27+。

**1. 后端**

```bash
cd backend
go run .
```

默认监听 `127.0.0.1:8788`，数据目录 `./data`，首次运行会创建空数据库。
如需自定义：

```bash
DATA_DIR=./data HOST=127.0.0.1 PORT=8788 go run .
```

Windows PowerShell：

```powershell
$env:DATA_DIR = "./data"
go run .
```

**2. 前端**

另开终端：

```bash
cd frontend
npm ci
npm run dev
```

打开 `http://127.0.0.1:5178`。`predev` 会自动把 `../content` 同步到
`frontend/public/data`；Vite 把 `/api` 代理到本机 `8788`。

首次打开可以注册账号，或选择游客模式（仅保存在当前浏览器）。

**3. 安卓（可选）**

要求 JDK 17+ 与 Android SDK。API 地址不写在仓库里，构建时注入：

```bash
cd android
./gradlew assembleDebug -PapiBaseUrl=https://your-host:8443/
```

`syncContent` 会把 `../content` 复制进 assets，无需手工同步。
详见 [android/README.md](android/README.md)。

## 构建与部署

前端：

```bash
cd frontend
npm ci
npm run build        # 输出 dist/
```

后端：

```bash
cd backend
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
  go build -trimpath -ldflags="-s -w" -o daily-english-go .
```

部署模板：

- [deploy/daily-english-go.service](deploy/daily-english-go.service)
- [deploy/nginx.conf.example](deploy/nginx.conf.example)
- [deploy/env.example](deploy/env.example)
- [docs/deployment.md](docs/deployment.md)

一条命令打出可直接安装的发布包（前端 dist + linux/amd64 二进制 + systemd 模板）：

```bash
deploy/build-release.sh --out dist
scp dist/daily-english-<rev>.tar.gz user@host:/tmp/
ssh user@host 'sudo bash -s -- /tmp/daily-english-<rev>.tar.gz' < deploy/remote-deploy.sh
```

`remote-deploy.sh` 会先备份线上版本，再替换、重启、健康检查，检查不过自动回滚。
流水线版本（手动触发、带审批、产物与 Release）见 [docs/ci-cd.md](docs/ci-cd.md)。

核心拓扑：

```
浏览器/PWA ──HTTPS──> nginx ──/api/──> Go :8788
Android   ────────────┘                 └──SQLite progress.db
                         └──静态──> frontend/dist
```

## API

共 8 个 method + path 路由，对应 7 个路径：

`GET /api/health`、`POST /api/register`、`POST /api/login`、
`POST /api/logout`、`GET /api/me`、`GET|PUT /api/state`、
`POST /api/password`。

详细请求/响应、状态 JSON 与 revision 冲突处理见
[docs/api.md](docs/api.md)。

## 测试

```bash
cd backend && go test ./...
cd frontend && npm test
cd android && ./gradlew testDebugUnitTest
```

前端测试覆盖状态迁移、计划、同步合并、初始启动与主要路由；后端测试覆盖
state 校验、scrypt 兼容、SQLite revision、会话、同源、锁定与 API 全流程；
安卓测试覆盖学习引擎、笔记本、真题、状态序列化、同步与真实语料解析。
三端在 CI 里每次提交都会跑一遍。

## 内容与许可

- **代码**：MIT，见 [LICENSE](LICENSE)。
- **学习内容**：来自 ECDICT、CET-6 词表、CET-6 翻译语料与考研真题的公开整理，
  按各自许可证使用；真题只收录试题/答案层，不收录解析层。
- 再分发或部署前请阅读 [ATTRIBUTIONS.md](ATTRIBUTIONS.md)，并保留署名。
- 仓库不包含任何生产数据库、账号、密钥或个人部署信息。

## 范围

本仓库包含 Web/PWA 前端、Go 后端与原生 Android 客户端（Quiet Glass 支线），
以及三者的 CI/CD 流水线。仓库不包含生产地址、密钥、账号或数据库；
安卓构建的 API 地址、部署主机的凭据都由使用者在构建参数或 CI Secrets 里注入。
