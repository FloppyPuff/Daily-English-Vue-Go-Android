# Content

前端构建前由 `frontend/scripts/sync-content.mjs` 同步到 `frontend/public/data/`。

| 文件 | 内容 |
|---|---|
| `words.json` | 7,291 个大纲词（CET-6 6,916 / 考研 6,246，交集 5,871） |
| `sentences.json` | 253 句长难句、翻译段落与汉译英题目 |
| `papers/index.json` | 44 套考研英语试卷索引 |
| `papers/*.json` | 各套试卷的篇章、题目与答案（不含解析层） |

来源、许可证和处理边界见仓库根目录 [ATTRIBUTIONS.md](../ATTRIBUTIONS.md)。
这些数据不属于代码的 MIT 许可。
