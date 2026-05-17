import type { ChatSession, NoteListItem, SourceChunk } from '@/api/types';

export const mockNotes: NoteListItem[] = [
  {
    id: 1,
    title: 'MySQL 索引详解',
    chunkCount: 42,
    charCount: 18230,
    tokenCount: 5847,
    createdAt: '2026-05-12T09:24:00+08:00',
  },
  {
    id: 2,
    title: 'MySQL 事务与隔离级别',
    chunkCount: 28,
    charCount: 12044,
    tokenCount: 3912,
    createdAt: '2026-05-13T15:08:00+08:00',
  },
  {
    id: 3,
    title: 'MySQL 存储引擎与锁',
    chunkCount: 35,
    charCount: 15470,
    tokenCount: 4823,
    createdAt: '2026-05-14T20:51:00+08:00',
  },
  {
    id: 4,
    title: 'Redis 数据结构与持久化',
    chunkCount: 31,
    charCount: 13885,
    tokenCount: 4421,
    createdAt: '2026-05-15T11:32:00+08:00',
  },
  {
    id: 5,
    title: 'JVM 内存模型与 GC',
    chunkCount: 47,
    charCount: 21099,
    tokenCount: 6712,
    createdAt: '2026-05-16T08:15:00+08:00',
  },
];

const mvccSources: SourceChunk[] = [
  {
    noteId: 2,
    chunkId: 211,
    title: 'MySQL 事务与隔离级别',
    headingPath: 'MySQL 事务 > MVCC > 实现机制',
    score: 0.9418,
    content:
      'MVCC（Multi-Version Concurrency Control）依赖三个核心组件：\n\n1. 隐藏字段：每条记录附带 trx_id（最近一次修改的事务 ID）和 roll_pointer（指向 undo log 链表的指针）。\n2. undo log：保存历史版本，回滚段中的旧版本通过 roll_pointer 串成链表。\n3. ReadView：事务在快照读时生成的一致性视图，包含活跃事务列表 m_ids、低水位 min_trx_id、高水位 max_trx_id、生成视图的事务 creator_trx_id。\n\n通过 ReadView 判断某个版本是否对当前事务可见：若 trx_id < min_trx_id 则可见；trx_id >= max_trx_id 则不可见；min_trx_id <= trx_id < max_trx_id 时若 trx_id 在 m_ids 中则不可见，否则可见。',
  },
  {
    noteId: 2,
    chunkId: 218,
    title: 'MySQL 事务与隔离级别',
    headingPath: 'MySQL 事务 > MVCC > 与隔离级别的关系',
    score: 0.8732,
    content:
      'READ COMMITTED 与 REPEATABLE READ 隔离级别都依赖 MVCC：\n\n- RC：每次快照读都重新生成 ReadView，因此能看到其他事务最新提交的数据，导致不可重复读。\n- RR：事务首次快照读时生成 ReadView，整个事务复用同一个 ReadView，从而保证可重复读。\n\nInnoDB 在 RR 级别下结合 Next-Key Lock 解决幻读问题，但仅针对当前读（SELECT ... FOR UPDATE / LOCK IN SHARE MODE / UPDATE / DELETE）。普通 SELECT 在 RR 下走快照读，不会加锁。',
  },
  {
    noteId: 3,
    chunkId: 305,
    title: 'MySQL 存储引擎与锁',
    headingPath: 'MySQL 锁 > 行级锁 > Next-Key Lock',
    score: 0.7641,
    content:
      'Next-Key Lock = Record Lock + Gap Lock。它锁定一条索引记录以及该记录之前的 gap，因此能够阻止其他事务在该范围内插入新记录，是 InnoDB 在 RR 隔离级别下解决幻读的关键机制。\n\n注意：Next-Key Lock 只在使用唯一索引等值查询且命中记录时退化为 Record Lock；其他情况（范围查询、等值未命中）仍是 Next-Key 形态。',
  },
];

const indexSources: SourceChunk[] = [
  {
    noteId: 1,
    chunkId: 102,
    title: 'MySQL 索引详解',
    headingPath: 'MySQL 索引 > B+ 树 > 为什么是 B+ 树',
    score: 0.9105,
    content:
      'MySQL 选择 B+ 树而不是其它结构，主要考虑磁盘 IO：\n\n- 矮胖树：B+ 树扇出大，3-4 层就能容纳千万级别记录，每次查询最多 3-4 次磁盘 IO。\n- 范围查询友好：所有数据都在叶子节点，且叶子节点之间通过双向链表连接，范围扫描非常高效。\n- 顺序读：相比 B 树，B+ 树非叶子节点不存数据，单页能容纳更多键，进一步降低树高。',
  },
  {
    noteId: 1,
    chunkId: 117,
    title: 'MySQL 索引详解',
    headingPath: 'MySQL 索引 > 聚簇索引 vs 二级索引',
    score: 0.8512,
    content:
      'InnoDB 中表数据本身就是按主键聚簇存储的（聚簇索引），叶子节点存的是完整的行数据。\n\n二级索引（非聚簇索引）的叶子节点存的是主键值，通过二级索引查询非索引字段需要"回表"——先在二级索引找到主键，再到聚簇索引取出整行数据。\n\n覆盖索引可以避免回表：当查询字段完全被某个二级索引覆盖时，直接从二级索引返回数据，IO 显著减少。',
  },
];

export const mockSessions: ChatSession[] = [
  {
    id: 'session-1',
    title: 'MVCC 实现机制',
    noteId: null,
    turns: [
      {
        id: 1,
        question: 'MySQL 的 MVCC 依赖哪些机制实现?',
        answer:
          'MVCC 主要依赖三个核心机制 [1]：隐藏字段 trx_id 和 roll_pointer 用于追踪版本与回滚链；undo log 保存历史版本，串成链表；ReadView 在快照读时生成一致性视图。\n\nReadView 通过比较 trx_id 与 min_trx_id / max_trx_id / 活跃事务列表来判断版本可见性 [1]。在 REPEATABLE READ 隔离级别下，事务首次快照读时生成的 ReadView 会被整个事务复用，从而保证可重复读 [2]。',
        sources: mvccSources,
        loading: false,
      },
      {
        id: 2,
        question: '那 Next-Key Lock 在这里起什么作用?',
        answer:
          'Next-Key Lock 是 InnoDB 在 RR 隔离级别下解决幻读的关键 [1]。它由 Record Lock 和 Gap Lock 组成，锁定一条索引记录及其前面的间隙，从而阻止其他事务在该范围内插入新记录。\n\n需要注意的是，Next-Key Lock 只对当前读生效（SELECT ... FOR UPDATE 等），普通 SELECT 走 MVCC 快照读不会加锁。',
        sources: [mvccSources[2]],
        loading: false,
      },
    ],
  },
  {
    id: 'session-2',
    title: 'B+ 树为什么适合数据库索引',
    noteId: 1,
    turns: [
      {
        id: 1,
        question: '为什么 MySQL 选择 B+ 树作为索引结构?',
        answer:
          'MySQL 选择 B+ 树主要因为它适合磁盘 IO 场景 [1]：\n\n1. 树高低（矮胖）：扇出大，3-4 层即可容纳千万级数据。\n2. 非叶子节点不存数据：单页能容纳更多键，进一步降低树高。\n3. 叶子节点链表：范围查询非常高效。\n\n相比之下，InnoDB 的聚簇索引把整行数据放在主键 B+ 树的叶子节点，二级索引则需要"回表" [2]。',
        sources: indexSources,
        loading: false,
      },
    ],
  },
];

const PRESET_SOURCES_BY_KEYWORD: Array<{ keywords: string[]; sources: SourceChunk[] }> = [
  { keywords: ['mvcc', '可见性', 'readview', 'undo'], sources: mvccSources },
  { keywords: ['next-key', '锁', '幻读', '间隙锁'], sources: [mvccSources[2]] },
  { keywords: ['索引', 'b+', 'b树', '回表', '覆盖'], sources: indexSources },
];

export function pickMockSources(question: string): SourceChunk[] {
  const lower = question.toLowerCase();
  for (const entry of PRESET_SOURCES_BY_KEYWORD) {
    if (entry.keywords.some((k) => lower.includes(k))) {
      return entry.sources;
    }
  }
  return mvccSources.slice(0, 2);
}

export function buildMockAnswerText(question: string, sources: SourceChunk[]): string {
  const trimmed = question.trim();
  if (sources.length === 0) {
    return '根据当前笔记内容无法确定。';
  }
  const top = sources.slice(0, Math.min(3, sources.length));
  const refs = top.map((_, i) => `[${i + 1}]`).join('');
  const headings = top
    .map((c, i) => `${i + 1}. ${c.headingPath?.trim() || c.title}`)
    .join('\n');
  return `针对"${trimmed}"，从笔记中检索到以下相关片段 ${refs}：\n\n${headings}\n\n（这是占位回答，未接入 LLM。点击编号引用可在右侧查看完整 chunk。）`;
}

export function summarizeQuestion(question: string, max = 14): string {
  const flat = question.replace(/\s+/g, ' ').trim();
  if (!flat) return '新会话';
  return flat.length > max ? flat.slice(0, max) + '…' : flat;
}
