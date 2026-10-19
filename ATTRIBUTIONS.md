# 内容来源与署名 / Content attributions

这个网站里的学习内容不是原创题目。四个公开语料源经过清洗后生成
`content/`，本文件说明每个来源、它的许可证，以及我们在生成时做了哪些取舍。
按各许可证要求，请在再分发时保留本文件。

## 1. ECDICT 英汉词典（词表主体）

- 来源：`skywind3000/ECDICT` 数据集，经 npm 包 [`ecdict` 0.0.4](https://www.npmjs.com/package/ecdict)（`yinyanfr/ecdict`，MIT）分发；
  实际读取的是包内 `assets/ecdict.csv`。
- 许可证：MIT（版权 © 2022 Yan）。
- 用途：`content/words.json` 的单词释义、音标、词性与真题词频标注。
- 处理：按考研大纲词表与 CET-6 高频表筛选，只保留有中文释义的词条；未收录 ECDICT 的例句层。

MIT 许可片段：

> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
> documentation files (the "Software"), to deal in the Software without restriction…

## 2. CET-6 高频词汇表

- 来源：[`wangjiuyi543-ui/cet6-vocabulary`](https://github.com/wangjiuyi543-ui/cet6-vocabulary) 中的
  `cet6_high_frequency_words.csv`（2020–2025 真题词频统计）。
- 许可证：MIT（已通过 GitHub API 核对该仓库 `license` 字段）。
- 用途：给 ECDICT 词条打上 `cet6` 考试标签与词频排序，并补充部分词条的真题例句
  （`example_sentence_from_exam`）。
- 处理：例句单元格取自扫描件，构建时会丢弃带中文注释、OCR 乱码、填空下划线、未闭合引号等损坏样本
  （约 370 条），只保留可读句子。

## 3. CET-6 历年翻译真题语料

- 来源：[`xzyj50609/cet6-translation-writing-corpus`](https://github.com/xzyj50609/cet6-translation-writing-corpus)
  的 `01_历年翻译真题_2015-2025/六级翻译全量题库_73篇.md`（许可证文件已随下载留存在 `.cache/src/CET6_CORPUS_LICENSE`）。
- 许可证：MIT（版权 © 2026 xzyj50609）。
- 用途：`content/sentences.json` 里的 73 条汉译英题目。
- 已知边界：该语料只给中文段落，**没有官方参考英文译文**，所以网站对这部分提供自评而不是自动评分。

## 4. 考研英语真题（1998–2025）

- 来源：[`LIziak112/structured-kaoyan-english`](https://github.com/LIziak112/structured-kaoyan-english)
  结构化数据集（1998–2025 英语一 / 英语二，44 卷，2152 题）。
- 许可证：**分层授权（Layered License）**，GitHub 识别为 `NOASSERTION`，原文见仓库 `LICENSE.md`，与本项目关系如下。
  - 第一层「试题数据」（题干、篇章、选项、标准答案）——作者明确不主张权利，可自由使用。本站只取这一层。
  - 第二层「解析数据」（`questions[].explanation`、教辅整理全文翻译与考点精析）——著作权归原整理者/出版方，
    CC BY-NC 4.0，**禁止商用**。本站**不收录**这一层：构建脚本只白名单读取
    `number / stem / options / answer` 四个字段，`explanation` 与解析 Markdown 从不进入生成产物。
  - 第三层「项目原创贡献」（字段规范与文档）——CC BY 4.0。
- 处理与边界：
  - 真题里的插图（源仓库 `assets/`）未复制进本站，个别依赖图片才能作答的小组因此不收录。
  - 阅读 Part B（七选五等新题型）有 7 组只有答案字母、没有 A–G 选项句子，这类小组在界面上标为
    「只留原文精读」，不提供作答（避免给出无法判断对错的题）。
  - 个别年份的源数据缺少题型字段（`type`）或写成泛化的 `translation`；构建时会依据 Section 标题、
    篇章与答案形态归一化，避免整组题被白名单丢弃。
  - 数据由扫描件识别与大模型辅助转换而来，题号、答案与个别选项仍可能有错漏；界面上保留了来源年份与
    卷次标注，方便对照纸质真题复核。

## 5. 本项目手写内容

- 为补齐词库例句与考研长难句手写的条目，属于本项目原创，已并入 `content/`，可随站点一同使用。

## 再生成

本仓库直接包含生成后的 `content/` 数据；上游来源链接、许可证和处理边界见上。
内容构建脚本与原始 `.cache/` 不随本包发布。若你需要从上游重新生成，请按各来源仓库的许可
获取原始文件，保留本署名文件，并确保只使用各来源允许再分发的数据层。
