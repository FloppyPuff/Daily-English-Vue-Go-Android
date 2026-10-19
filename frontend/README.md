# Daily English · Frontend

Vue 3 + Vite 的 Web/PWA 前端。

## 开发

```bash
npm ci
npm run dev
```

`predev` 会把 `../content` 同步到 `public/data`。Vite 将 `/api` 代理到
`127.0.0.1:8788`，先启动仓库的 Go 后端即可登录/同步。

## 构建

```bash
npm run build
```

输出 `dist/`。使用 hash 路由，静态托管不需要额外的 SPA fallback。

## 测试

```bash
npm test
```

## 结构

```
src/lib/         状态机、内容索引、同步、存储、语音
src/stores/      应用状态与账号/计划/计时
src/components/  壳层、词卡、句子卡、专注面板等
src/views/       今日、词汇、句子、真题、复习、笔记、统计、设置、认证
src/styles/      设计 tokens 与基础排版
tests/           领域测试与壳层冒烟测试
```

PWA、暗夜模式与状态协议说明见仓库根目录 `README.md` 与 `docs/architecture.md`。
