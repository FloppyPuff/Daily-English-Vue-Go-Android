# 部署

以下示例使用：

- 域名：`example.com`
- 静态目录：`/var/www/daily-english/web`
- Go 二进制：`/opt/daily-english-go/daily-english-go`
- 数据目录：`/var/lib/daily-english`
- 服务用户：`daily-english`
- API：`127.0.0.1:8788`

## 1. 构建

```bash
cd frontend
npm ci
npm run build

cd ../backend
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
  go build -trimpath -ldflags="-s -w" -o daily-english-go .
```

## 2. 服务器准备

```bash
useradd --system --home /var/lib/daily-english --shell /usr/sbin/nologin daily-english
install -d -o daily-english -g daily-english -m 700 /var/lib/daily-english
install -d -o root -g root -m 755 /opt/daily-english-go
install -d -o root -g root -m 755 /var/www/daily-english/web
install -m 755 daily-english-go /opt/daily-english-go/daily-english-go
cp -a ../frontend/dist/. /var/www/daily-english/web/
```

## 3. 环境文件

```bash
install -d -m 755 /etc/daily-english
install -m 600 deploy/env.example /etc/daily-english/env
```

按实际部署修改 `/etc/daily-english/env`。`COOKIE_SECURE=1` 和
`TRUST_PROXY=1` 适用于 HTTPS + nginx；本地 HTTP 开发时设为 0。

## 4. systemd

```bash
cp deploy/daily-english-go.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now daily-english-go
systemctl status daily-english-go
```

## 5. nginx

复制 `deploy/nginx.conf.example` 到站点配置，替换域名和证书路径：

```bash
nginx -t
systemctl reload nginx
```

必须保留：

- `proxy_set_header Host $http_host;`（同源检查比较完整 Host）
- `location /api/` 到 Go 端口
- `manifest.webmanifest` 的 `application/manifest+json` MIME
- 静态 `root` 指向前端 `dist`

## 6. 验证

```bash
curl -fsS https://example.com/api/health
curl -fsS -o /dev/null -w '%{http_code}\n' https://example.com/
curl -fsS -o /dev/null -w '%{http_code}\n' https://example.com/api/me   # 未登录 401
```

注册一个测试账号，确认登录、状态写入与同步：

```bash
curl -fsS -c /tmp/de.cookies -H 'content-type: application/json' \
  -d '{"email":"test@example.com","password":"Test-Passw0rd"}' \
  https://example.com/api/register
curl -fsS -b /tmp/de.cookies https://example.com/api/state
```

测试完删除测试账号或使用测试数据库。

## 7. 备份与升级

- 备份数据库：使用 SQLite 的 `VACUUM INTO` 生成一致性副本，不要直接复制运行中的 WAL 文件。
- 备份 `/var/www/daily-english/web`、`/etc/nginx` 与 `/etc/daily-english/env`。
- 升级：替换二进制和静态目录 → `systemctl restart daily-english-go` → `nginx -t && reload`。
- 前端使用 hash 路由，更新静态文件不需要服务端 fallback。

## 8. 自动化升级

上面第 1、7 节的手工步骤已经脚本化：

```bash
deploy/build-release.sh --out dist                    # 打包 dist + linux/amd64 二进制
scp dist/daily-english-<rev>.tar.gz user@host:/tmp/
ssh user@host 'sudo bash -s -- /tmp/daily-english-<rev>.tar.gz' < deploy/remote-deploy.sh
```

`remote-deploy.sh` 会把当前的 web 目录与二进制备份到
`/var/backups/daily-english/<时间戳>-<rev>`（保留最近 5 份），替换后重启并做健康检查，
检查不通过自动回滚。它默认**不**改动 systemd unit 与 nginx 配置：unit 通常带
`EnvironmentFile`，仓库里的模板只是示例，确需替换时显式加 `UPDATE_UNIT=1`。

GitHub Actions 版本（手动触发 + 环境审批 + 产物/Release）见 [ci-cd.md](ci-cd.md)。
