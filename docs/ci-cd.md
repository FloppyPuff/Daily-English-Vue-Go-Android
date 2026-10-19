# CI/CD

三条 GitHub Actions 工作流覆盖前端、后端与安卓客户端。CI 全自动，CD 与安卓发布都要人点。

| 工作流 | 文件 | 触发 | 作用 |
|---|---|---|---|
| CI | `.github/workflows/ci.yml` | push / PR 到 `main`，或手动 | 前端测试+构建、后端 gofmt/vet/test/交叉编译、安卓单测+debug APK、部署脚本 shellcheck |
| Deploy | `.github/workflows/deploy.yml` | 仅手动，需输入 `DEPLOY` | 打包 → SSH 上传 → 服务器端备份/替换/重启/健康检查，失败自动回滚 |
| Android Release | `.github/workflows/android-release.yml` | 手动，或推 `android-v*` 标签 | 构建 release APK（可签名）→ 挂到 GitHub Release，得到稳定下载地址 |

```
push / PR ──► CI ──► 三个 job 并行 ──► 产物（web-dist / backend-linux-amd64 / apk / 测试报告）

手动 Deploy ──► build job（tar.gz）──► deploy job（environment: production，可加人工审批）
                                        └─ scp tar.gz → ssh 'sudo bash -s -- tar.gz' < remote-deploy.sh
                                             ├─ 备份 web + 二进制到 /var/backups/daily-english/<时间戳>-<rev>
                                             ├─ 两遍 rsync 换 web（先加新文件，再删旧文件）
                                             ├─ install 新二进制 → systemctl restart
                                             ├─ 健康检查（本地 8788 + 可选公网）
                                             └─ 不通过 → 回滚 web + 二进制 → 重启 → 再检查
```

## 一次性准备

### 1. 创建 production 环境（人工审批闸门）

仓库 → Settings → Environments → New environment → 名字必须填 `production`
（`deploy.yml` 里写死了这个名字）。可选但推荐：勾选 **Required reviewers** 加上你自己，
这样每次部署都要在 Actions 页面再点一次 Approve。

Secrets 加在**这个环境**下（Environment secrets），不要加在仓库级——环境级 secrets 只有
通过审批的运行才能读到。

### 2. 部署专用 SSH 密钥

在本机（pwsh）生成一对只用于部署的密钥，不要复用你日常登录的密钥：

```powershell
ssh-keygen -t ed25519 -C "daily-english-deploy" -f "$env:USERPROFILE\.ssh\daily_english_deploy" -N '""'
Get-Content "$env:USERPROFILE\.ssh\daily_english_deploy.pub"
```

把公钥追加到服务器上部署账号的 `~/.ssh/authorized_keys`（`chmod 600`）。
私钥内容整段贴进 `DEPLOY_SSH_KEY` secret。

如果部署账号不是 root，给它一条免密 sudo。最省事的写法：

```
# /etc/sudoers.d/daily-english-deploy   (chmod 440, 用 visudo 编辑)
deployer ALL=(root) NOPASSWD: /usr/bin/bash
```

**这条等于把该账号变成 root。** 因为它只能通过 `production` 环境审批后才用得上，
风险边界是"GitHub secrets 泄露 = 服务器 root"。想收紧的话，把 `deploy/remote-deploy.sh`
固定安装到 `/usr/local/sbin/daily-english-deploy`，sudoers 只放行那一个路径，
代价是脚本版本不再自动跟随提交。

### 3. Secrets 一览

`production` 环境下：

| 名称 | 必填 | 说明 |
|---|---|---|
| `DEPLOY_HOST` | 是 | 主机名或 IP |
| `DEPLOY_PORT` | 是 | SSH 端口 |
| `DEPLOY_USER` | 是 | SSH 用户名 |
| `DEPLOY_SSH_KEY` | 是 | 私钥全文（含 BEGIN/END 行） |
| `DEPLOY_KNOWN_HOSTS` | 否 | `ssh-keyscan -p <port> <host>` 的输出；填了就会校验主机密钥，不填则首次连接自动信任 |
| `HEALTH_PUBLIC` | 否 | 例如 `https://<host>:8443/api/health`；服务器端和 runner 端各探一次 |

仓库级（安卓发布用）：

| 名称 | 必填 | 说明 |
|---|---|---|
| `ANDROID_API_BASE_URL` | 标签触发时必填 | 默认 API 源，末尾要有 `/` |
| `ANDROID_KEYSTORE_BASE64` | 否 | `base64 -w0 release.jks` 的输出；不填就产出未签名 APK |
| `ANDROID_KEYSTORE_PASSWORD` | 与上一项同时 | |
| `ANDROID_KEY_ALIAS` | 与上一项同时 | |
| `ANDROID_KEY_PASSWORD` | 与上一项同时 | |

生成签名密钥（**离线保存，丢了就没法给老用户升级**）：

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 \
  -validity 10000 -alias daily-english
base64 -w0 release.jks   # Windows: certutil -encode release.jks out.b64
```

### 4. 服务器前置条件

`tar`、`rsync`、`curl`、`systemctl`；`daily-english-go.service` 已按 `docs/deployment.md` 装好；
`/var/backups/daily-english` 可写（脚本会自建）。nginx 配置由人维护，流水线不碰。

## 日常使用

### 提交代码

推到 `main` 或开 PR，CI 自动跑。四个 job 相互独立，红哪个修哪个。产物在 run 页面底部：
`web-dist`、`backend-linux-amd64`、`android-debug-apk-no-api`、`android-test-report`。

CI 里的 debug APK **没有配 API 地址**（指向 `https://daily-english.invalid/`），
只用于验证能编译，不要拿来装。

### 发布 Web / 后端

Actions → Deploy → Run workflow → 选分支、选 `component`（`all` / `web` / `backend`）、
`confirmation` 填 `DEPLOY` → Run。若配了 Required reviewers，还要再点一次 Approve。

日志里会依次出现：打包 → 上传 → 备份 → 换 web → 换二进制 → 重启 → 健康检查 → revision 与备份路径。

### 发布 APK

Actions → Android Release → Run workflow → 填 `api_base_url`（例如 `https://<host>:8443/`）→ Run。
完成后到仓库 **Releases** 页面下载 `Daily-English-Glass-<版本>-release.apk` 和 `.sha256`。
这个链接是稳定的，比往服务器 web 目录扔安装包、或在聊天里传文件都可靠。

也可以推标签触发：`git tag android-v1.2.1 && git push origin android-v1.2.1`
（此时 API 地址取 `ANDROID_API_BASE_URL` 变量）。

## 不用 CI 也能发（本地等价命令）

```bash
# 打包
deploy/build-release.sh --out dist                # 全量
deploy/build-release.sh --out dist --skip-web     # 只发后端

# 上传并安装（脚本走 stdin，服务器上不需要预先放任何东西）
scp dist/daily-english-<rev>.tar.gz user@host:/tmp/
ssh user@host 'sudo bash -s -- /tmp/daily-english-<rev>.tar.gz' < deploy/remote-deploy.sh

# 带公网健康检查
ssh user@host 'sudo HEALTH_PUBLIC=https://host:8443/api/health bash -s -- /tmp/x.tar.gz' < deploy/remote-deploy.sh
```

Windows 下在 Git Bash 里跑；`build-release.sh` 需要 Node 22+ 与 Go 1.27+。

安卓本地构建见 `android/README.md`（API 地址用 `-PapiBaseUrl=` 或 `~/.gradle/gradle.properties`）。

## 回滚

三层，从快到慢：

1. **自动**：健康检查不过，`remote-deploy.sh` 自己把 web 和二进制换回上一版并重启，日志里写
   `rollback complete`。这种情况不需要人动手，但要看日志确认为什么失败。
2. **手动回退到任意历史版本**：服务器上保留最近 5 份备份。

   ```bash
   ls -1dt /var/backups/daily-english/*/
   B=/var/backups/daily-english/<时间戳>-<rev>
   sudo rsync -a --delete "$B/web/" /var/www/daily-english/web/
   sudo install -m 755 "$B/daily-english-go" /opt/daily-english-go/daily-english-go
   sudo systemctl restart daily-english-go
   curl -fsS http://127.0.0.1:8788/api/health
   ```
3. **代码回退**：`git revert` 出问题的提交，再跑一次 Deploy。不要用 `git push -f`。

数据库（`DATA_DIR`，默认 `/var/lib/daily-english`）在任何一层都不被流水线触碰。
状态表有 `revision` 乐观锁，回退二进制不会破坏数据。

## 故障排查

| 现象 | 原因 / 处理 |
|---|---|
| Deploy job 直接 skipped | `confirmation` 没填 `DEPLOY`（job 上有 `if` 条件） |
| `secret DEPLOY_* is not set` | secrets 加错了层级，要加在 `production` 环境下 |
| `Permission denied (publickey)` | 私钥格式不对（要整段含 BEGIN/END）、`authorized_keys` 权限不是 600、或 `DEPLOY_USER` 写错 |
| `sudo: a terminal is required` / 卡住要密码 | sudoers 没配 NOPASSWD；`sudo bash -s` 会把脚本从 stdin 读走，密码提示无处可输 |
| `Host key verification failed` | `DEPLOY_KNOWN_HOSTS` 里的记录和服务器不一致（换过主机密钥），重新 `ssh-keyscan` |
| `missing required tool: rsync` | 服务器没装 rsync：`apt install rsync` |
| 健康检查一直失败但服务在跑 | `HEALTH_LOCAL` 端口和 `/etc/daily-english/env` 里的 `PORT` 不一致 |
| CI 安卓 job 找不到 android-36 | 平台安装步骤失败，看 `Ensure Android platform` 的日志；runner 镜像变动时可显式 `sdkmanager --install "platforms;android-36"` |
| CI 安卓 job 卡在下载依赖 | `settings.gradle.kts` 里阿里云镜像优先，海外 runner 偶发慢；可在 CI 分支里去掉镜像 |
| `gradlew: Permission denied` | 可执行位丢了：`git update-index --chmod=+x android/gradlew`（CI 的 `Check executable bits` 会提前报这个） |

## 安全约定

- 密钥、密码、token 只存在于 GitHub Secrets 与服务器上的 `/etc/daily-english/env`（600），
  永远不进仓库、不进日志、不贴聊天。
- 工作流里的 secret 一律通过 `env:` 传给脚本，不做 `${{ secrets.X }}` 字符串插值，
  避免 secret 内容被当成 shell 代码执行。
- 顶层 `permissions: contents: read`；只有发布 Release 的那个 job 单独提到 `contents: write`。
- `remote-deploy.sh` 默认**不**替换 systemd unit（线上 unit 通常带 `EnvironmentFile`，
  仓库里的模板会覆盖掉它）。确实要换时显式加 `UPDATE_UNIT=1`，并先 diff。
- Deploy 与 Android Release 都开了 `concurrency`，同一时间只允许一个部署在跑，
  且不会被后来的运行取消（`cancel-in-progress: false`）。
