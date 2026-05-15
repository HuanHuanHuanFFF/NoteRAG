# Retrieval Baseline Report

## 1. 背景

本次实验用于对比 NoteRAG 自定义 Markdown heading-aware chunk 策略与 Spring AI TokenTextSplitter baseline 在技术笔记检索场景下的表现。

实验目标不是比较最终 LLM 回答质量，而是只评估第一阶段 retrieval 的排序与召回效果。

## 2. 测试文档

测试文档来自 JavaGuide MySQL 面试题文档。

| 指标 | 数值 |
|---|---:|
| charCount | 40562 |
| tokenCount | 22108 |
| query 数量 | 15 |

query 覆盖范围包括：

- MySQL 基础
- 字段类型
- 存储引擎
- 索引
- 事务
- 锁
- SQL 性能分析

## 3. 对比方案

### 3.1 NoteRAG heading-aware chunk

自定义 Markdown chunk 策略：

- 按 Markdown heading / section 结构切分
- 保留 headingPath
- 保留 chunkIndex、charCount、tokenCount 等元数据
- 面向章节级 source 定位

| 指标 | 数值 |
|---|---:|
| chunkCount | 71 |
| avg tokens / chunk | 约 311.4 |

### 3.2 Spring AI matched-size baseline

Spring AI TokenTextSplitter baseline：

- 调整 chunk size，使 chunk 数量接近自定义方案
- 不注入 headingPath
- 不做 Markdown heading-aware 处理

| 指标 | 数值 |
|---|---:|
| chunkCount | 69 |
| avg tokens / chunk | 约 320.4 |

两组 chunk 数量接近，因此可以视为 matched-size 对比。

## 4. 评估指标

本次使用人工标注的核心 source 作为相关性标准。

| 指标 | 含义 |
|---|---|
| Hit@1 | Top1 是否命中核心 source |
| Hit@3 | Top3 是否包含核心 source |
| Recall@5 | Top5 是否包含核心 source |
| Recall@10 | Top10 是否包含核心 source |
| MRR@5 | Top5 内第一个核心 source 的倒数排名均值 |

## 5. 实验结果

| 方案 | chunks | Hit@1 | Recall@3 / Hit@3 | Recall@5 | Recall@10 | MRR@5 |
|---|---:|---:|---:|---:|---:|---:|
| Spring AI TokenTextSplitter baseline | 69 | 80.0% | 93.3% | 100% | 100% | 0.883 |
| NoteRAG heading-aware chunk | 71 | 93.3% | 100% | 100% | 100% | 0.967 |
## 6. 结果分析

在相近 chunk 粒度下，两种方案的 Recall@5 / Recall@10 均达到 100%，说明核心内容基本都能被第一阶段向量检索召回。

主要差异体现在排序质量和 source 可解释性上：

- NoteRAG 的 Hit@1 从 80.0% 提升到 93.3%
- NoteRAG 的 Hit@3 从 93.3% 提升到 100%
- NoteRAG 的 MRR@5 从 0.883 提升到 0.967
- NoteRAG 保留 headingPath，source 能定位到具体 Markdown 章节
- Spring AI baseline 的 chunk 经常跨多个小节，且 headingPath 为空，展示层 source 可读性较弱

因此，本轮实验更能说明：

> 在 Markdown 技术笔记场景下，heading-aware chunk 不一定显著提升 Recall@K，但可以提升 TopK 排序质量和章节级 source 定位质量。

## 7. 结论

本次实验表明，在 JavaGuide MySQL 文档上，将 Spring AI TokenTextSplitter 调整到与自定义方案接近的 chunk 粒度后，NoteRAG heading-aware chunk 在 Hit@1、Hit@3 和 MRR@5 上均优于 baseline。

后续可以在更多文档和更多 query 上扩展评估集，并继续观察 rerank 对 Hit@1 和 MRR 的提升效果。