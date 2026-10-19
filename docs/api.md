# API

基地址：同源 `/api`。所有响应为 JSON（204 除外），带
`Cache-Control: no-store`。写请求必须同源；GET/HEAD 不受同源限制。

## 认证

- Web：登录/注册响应设置 `de_session` Cookie（HttpOnly、SameSite=Lax、60 天）。
- 原生客户端：请求头 `x-client: daily-english-mobile` 时，登录/注册响应额外返回
  `token`；后续请求使用 `Authorization: Bearer <token>`。
- 未登录访问受保护路由返回 `401 {"error":"请先登录"}`。

## 路由

### `GET /api/health`

```json
{ "ok": true, "users": 2, "uptimeSeconds": 123 }
```

### `POST /api/register`

```json
{ "email": "you@example.com", "password": "At-least-8-1" }
```

密码至少 8 位，且同时包含字母和数字。成功返回 `201` 和用户邮箱；
邮箱重复返回 `409`；注册关闭时返回 `503`。

### `POST /api/login`

```json
{ "email": "you@example.com", "password": "At-least-8-1" }
```

成功 `200`；错误统一返回 `401 {"error":"邮箱或密码不正确"}`。
连续失败 8 次后返回 `429`，并带 `Retry-After` 秒数。

### `POST /api/logout`

可选会话；清除 Cookie，返回 `204`。

### `GET /api/me`

```json
{ "user": { "email": "you@example.com" } }
```

### `GET /api/state`

```json
{ "revision": 3, "payload": { "version": 3, "...": "..." } }
```

没有状态时 `revision` 为 0，`payload` 为默认空计划。存储内容损坏时读回默认
空计划，不会让接口崩溃。

### `PUT /api/state`

```json
{ "revision": 3, "payload": { "version": 3, "...": "..." } }
```

- `revision` 必须是非负整数；不是最后一次读到的版本时返回 `409`。
- `409` 响应带服务器当前的 `revision` 和 `payload`，客户端合并后重试。
- 成功返回 `{"revision": 4}`。
- 状态必须是对象，最大 5 MB、最多 100,000 条事件；校验失败返回 `400`。

### `POST /api/password`

```json
{ "current": "Old-Passw0rd", "next": "New-Passw0rd" }
```

当前密码错误返回 `401`；新密码不符合规则返回 `400`。成功后吊销其他设备会话，
当前会话保留。

## 错误格式

```json
{ "error": "可读的中文原因" }
```

常见状态码：`400` 参数/状态格式错误，`401` 未登录或凭据错误，
`403` 跨站写请求，`404` 路由不存在，`409` 邮箱重复/状态冲突，
`413` 请求体过大，`429` 登录锁定，`500` 服务异常，`503` 注册关闭。
