# Daily English Glass（Android）

Daily English 的原生 Android 客户端，Compose + Material 3 的 **Quiet Glass** 视觉支线。
与 `frontend/`（Vue PWA）共用同一个 `backend/` API 和同一份 `content/` 语料。

- Application ID：`com.dailyenglish.quietglass`
- Kotlin namespace：`com.dailyenglish.nativeapp`
- 桌面名：`Daily English Glass`
- 版本：`1.2.4-quietglass`（versionCode 6）
- 最低系统：Android 7.0（API 24），compileSdk / targetSdk 36

## 设计

- 暖白与墨绿渐变背景，低透明鼠尾草与暖沙光晕。
- 悬浮胶囊底部导航 + 圆形玻璃顶栏按钮。
- 实色鼠尾草卡片、1dp 高光描边、柔和阴影；专注面板保持完全不透明。
- 认证、专注面板、设置与统计统一玻璃语言。
- 不引入新图形依赖，不使用 `RenderEffect` 真实背景模糊，因此 API 24 起表现一致。

## 构建

要求：JDK 17、Android SDK（`compileSdk 36`）。首次构建会自动下载 Gradle 9.1 与依赖。

本地 SDK 路径写进 `local.properties`（已被忽略，不要提交）：

```properties
sdk.dir=D\:\\Software\\Tool\\AndroidStudioSDK
```

### 配置 API 地址

仓库里没有生产地址。构建时注入，三选一：

```bash
# 1) 命令行属性
./gradlew assembleDebug -PapiBaseUrl=https://your-host:8443/

# 2) 环境变量
API_BASE_URL=https://your-host:8443/ ./gradlew assembleDebug
```

```properties
# 3) 本机长期配置：~/.gradle/gradle.properties（不在仓库内）
apiBaseUrl=https://your-host:8443/
```

不注入时默认值是 `https://daily-english.invalid/`，请求会直接失败——这是有意的，
避免误连到别人的服务器。地址必须以 `/` 结尾（Retrofit baseUrl 要求）。

### 命令

```bash
cd android
./gradlew testDebugUnitTest     # JVM 单元测试
./gradlew assembleDebug         # 调试 APK
./gradlew assembleRelease       # 发布 APK（未配置签名时为 unsigned）
```

Windows：把 `./gradlew` 换成 `.\\gradlew.bat`，并先设置
`$env:JAVA_HOME = "<Android Studio 路径>\\jbr"`。

产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 内容同步

`syncContent` 任务（挂在 `preBuild` 上）从仓库根的 `../content` 复制
`words.json`、`sentences.json`、`papers/**` 到 `app/src/main/assets/content/`。
该目录已被 git 忽略：语料只有一份，不要手改 assets 里的副本。

## 发布签名

四个值齐全时 `assembleRelease` 会自动签名，缺一即输出未签名 APK：

| Gradle 属性 | 环境变量 |
|---|---|
| `keystoreFile` | `KEYSTORE_FILE` |
| `keystorePassword` | `KEYSTORE_PASSWORD` |
| `keyAlias` | `KEY_ALIAS` |
| `keyPassword` | `KEY_PASSWORD` |

```bash
./gradlew assembleRelease \
  -PkeystoreFile=/secrets/release.jks -PkeystorePassword=... \
  -PkeyAlias=... -PkeyPassword=... -PapiBaseUrl=https://your-host:8443/
```

密钥永远不进仓库（`*.jks`、`*.keystore` 已忽略）。CI 里用 GitHub Secrets，
见 `../docs/ci-cd.md`。

## 与后端的契约

- 移动端调用 `/api/*` 时带 `x-client: daily-english-mobile`，服务端因此接受
  `Authorization: Bearer` 而不是 Cookie。
- 状态写入使用 `revision` 乐观锁，冲突返回 409 并附带服务端副本，由客户端合并。
- 学习进度以 version 3 状态 JSON 表示，与 Web 端备份格式互通。

细节见 `../docs/api.md`。

## 测试

```bash
./gradlew testDebugUnitTest          # 学习引擎、同步、状态校验、语料解析
./gradlew connectedDebugAndroidTest  # 需要设备或模拟器：Room、SessionStore、WorkManager
```

`ContentDtoTest` 读真实语料并断言 7291 词 / 253 句 / 44 套真题，
因此改动 `content/` 后必须跑一次。
