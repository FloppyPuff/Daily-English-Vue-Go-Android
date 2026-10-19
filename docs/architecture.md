# 架构

## 总览

```
浏览器 / PWA ─┐
             │ HTTPS（同源，无 CORS）
Android 客户端 ┘
  │
  ▼
nginx
  ├── /            → frontend/dist（Vue 静态文件）
  └── /api/        → Go API（127.0.0.1:8788）
                         ▼
                    SQLite progress.db
```

Web 走同源 Cookie，Android 走 Bearer token；两者调用同一套 `/api`，
读写同一份状态 JSON，因此进度在两端之间是互通的。

## 前端

- Vue 3 + Vue Router（hash 路由），Vite 构建。
- 学习状态是一份版本化 JSON（当前 version 3），由 `frontend/src/lib/core.js`
  负责复习间隔、队列、统计、备份校验和合并。
- 页面只负责交互与排版；同步层 `sync.js` 负责防抖保存、重试和 409 合并。
- 内容在构建前从 `content/` 同步到 `frontend/public/data/`，不重复提交一份。
- PWA 由 `vite-plugin-pwa` 生成 Service Worker；数据 JSON 预缓存，单套真题按需缓存。
- 19:00–07:00 按本地时间切换暗色主题，首屏由 `index.html` 内联脚本先行应用。

## 安卓客户端

- Kotlin + Jetpack Compose（Material 3），单模块 `:app`，`minSdk 24`。
- 依赖注入 Hilt，本地库 Room，偏好 DataStore，后台同步 WorkManager，
  网络 Retrofit + OkHttp + kotlinx.serialization。
- 领域逻辑在 `core/learning`、`core/sync`，与前端 `lib/core.js` 是同一套规则的
  两份实现：复习间隔、队列、统计口径、状态校验必须保持一致，改动要两端同步。
- Room 是本地真相；`SyncManager` 把它映射成 version 3 状态 JSON 走 `/api/state`，
  因此备份文件与 Web 端互通，409 冲突同样在客户端合并。
- 认证使用 Bearer token，登录请求带 `x-client: daily-english-mobile`；
  token 存在 `SessionStore`（EncryptedSharedPreferences），不进 Room。
- 语料在构建时由 Gradle `syncContent` 从仓库根的 `../content` 复制进
  `app/src/main/assets/content/`，该目录不入版本库。
- API 源地址由 `-PapiBaseUrl=` / `API_BASE_URL` 注入，仓库内不含生产地址；
  未注入时指向 `https://daily-english.invalid/`，请求必然失败，以此暴露配置遗漏。
- 视觉上是 Quiet Glass 支线：半透明卡片 + 悬浮胶囊导航，不使用 `RenderEffect`
  真实模糊，因此 API 24 起表现一致。

## 后端

- Go 标准库 `net/http`，单二进制，systemd 运行。
- SQLite 通过 `modernc.org/sqlite` 访问（纯 Go，无 CGO），WAL 模式。
- 四张表：
  - `users`：邮箱 + scrypt 密码哈希
  - `sessions`：SHA-256(token) + 过期时间
  - `states`：每用户一份状态 JSON + revision
  - `login_attempts`：账号/IP 的失败计数与锁定时间
- 密码：scrypt `N=16384, r=8, p=1`，16 字节随机盐，格式为
  `scrypt:16384:8:1:<salt>:<key>`。
- Web 使用 HttpOnly、SameSite=Lax 的 `de_session` Cookie；原生客户端使用
  Bearer token（仅在 `x-client: daily-english-mobile` 时随登录响应返回）。
- 写请求比较完整 `Host:Port` 同源；`TRUST_PROXY=1` 时才采信
  `X-Forwarded-For`，用于账号/IP 锁定。
- 登录连续失败 8 次锁定 15 分钟。
- 状态写入采用 revision 乐观锁：客户端提交它最后看到的 revision，不匹配时
  服务端返回 409 + 当前服务器副本，客户端在本地合并后重试。

## 状态 JSON

顶层字段：

```json
{
  "version": 3,
  "settings": { "exam": "cet6", "dailyGoal": 20, "sentenceGoal": 3, "focusMinutes": 25 },
  "records": {},
  "sentenceRecords": {},
  "papers": {},
  "drafts": {},
  "savedWords": [],
  "savedSentences": [],
  "events": []
}
```

- `records` 按单词 id 共享复习间隔，CET-6 与考研合并学习。
- `events` 记录 word/sentence/paper/focus，用于统计；`exam` 保留学习方向。
- 状态校验兼容 version 1/2 迁移与旧 demo 单词 id；详见
  `backend/internal/state` 和 `frontend/src/lib/core.js`。

## 安全边界

- 密码和会话 token 都不以明文入库。
- 状态 JSON 在写入和读回时都经过校验；最多 5 MB、100,000 条事件。
- API 请求体上限 6 MB；同源检查覆盖完整 Host（含端口）。
- SQLite 只由单个后端进程访问；部署说明默认只监听 127.0.0.1。
- 生产地址、密钥与凭据都不在仓库里：安卓 API 源走构建参数，部署走
  GitHub environment secrets 与服务器上的 `/etc/daily-english/env`（600）。

## 构建与发布流水线

一份语料（`content/`）→ 三端各自在构建时同步：前端 `predev`/`prebuild` 复制到
`public/data`，安卓 `syncContent` 复制到 assets，后端不打包语料。

```
push / PR ──► CI ──► frontend / backend / android / scripts 四个 job 并行
手动 Deploy ──► build-release.sh ──► tar.gz ──► SSH ──► remote-deploy.sh
                                                        ├─ 备份
                                                        ├─ 替换 + 重启
                                                        ├─ 健康检查
                                                        └─ 失败自动回滚
手动 Android Release ──► assembleRelease（可签名）──► GitHub Release 附件
```

部署脚本走 SSH stdin，服务器上不需要预先存放任何脚本，因此执行的版本永远和
构建的提交一致。细节、secrets 清单与回滚步骤见 [ci-cd.md](ci-cd.md)。
