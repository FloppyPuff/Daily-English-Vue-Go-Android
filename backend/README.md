# Daily English · Go API

Go 后端，保持与前端 `frontend/src/lib` 相同的状态协议。

## 本地运行

```bash
go test ./...
DATA_DIR=./data go run .
```

默认监听 `127.0.0.1:8788`。生产环境变量与 systemd 模板见仓库根目录
`deploy/`，部署步骤见 `docs/deployment.md`。

## 目录

```
main.go             配置、监听、优雅退出
internal/state/     状态 JSON 校验、version 1/2/3 迁移
internal/auth/      scrypt、token、邮箱/密码规则
internal/store/     SQLite schema 与查询
internal/api/       8 个 API 路由、会话、同源检查
```

## 构建

```bash
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
  go build -trimpath -ldflags="-s -w" -o daily-english-go .
```

纯 Go SQLite 驱动，无 CGO；二进制可独立部署。

## 兼容性

- 已有 `scrypt:16384:8:1:salt:key` 密码哈希
- 已有 `sessions.token_hash`（SHA-256）
- `states.payload`（version 3 JSON）与 revision 乐观锁
- Web Cookie / 原生 Bearer token 两种会话方式

接口细节见 `docs/api.md`。
