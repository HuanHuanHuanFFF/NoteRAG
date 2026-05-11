# 2026-04-30 增量总结：ConcurrentHashMap 细节补充与 Redis 主线

> 范围说明：本文件只整理 **HashMap 对比总结之后** 到当前对话的新内容。  
> 不包含上一份 HashMap / ConcurrentHashMap 对比主体内容。  
> 主要包括：ConcurrentHashMap 的补充理解，以及 Redis 从数据结构到 IO 多路复用的主线。

---

## 一、ConcurrentHashMap：补充理解

### 1. ConcurrentHashMap 不是数据库，没有 MVCC 快照读

ConcurrentHashMap 的一致性不能按数据库 MVCC 来理解。

数据库的一致性读关心的是：

> 一个事务能否看到某个稳定历史快照。

ConcurrentHashMap 关心的是：

> 多线程并发读写时，内部结构不能被读坏；已经完成的单 key 更新，对后续相关读取要有可见性保证。

所以它提供的是并发容器层面的线程安全，不是事务级快照。

更准确的说法：

- 单个 key 的 `get/put/remove` 这些操作是线程安全的。
- `get` 通常不加锁，可以和更新并发执行。
- 遍历、`size()`、`isEmpty()` 这类整体观察是弱一致的。
- 它不保证你看到的是“整张 Map 在某一瞬间的完整快照”。

可以复述为：

> ConcurrentHashMap 不是数据库，没有 MVCC 那种事务快照读。它保证单个操作线程安全，以及单 key 更新对后续读取的可见性；但整体遍历和 size 这种视角是弱一致的，可能看到并发变化中的部分结果。

---

### 2. 桶为空时的 CAS 插入

当某个桶为空时，多个线程可能同时想往这个桶里放第一个节点。

如果直接普通赋值：

```java
table[i] = newNode;
```

可能发生覆盖。

ConcurrentHashMap 会使用 CAS 思路：

```text
如果 table[i] 现在仍然是 null
    就把 newNode 放进去
否则
    说明别人已经抢先放了，当前线程失败并重试
```

CAS 全称是：

> Compare And Swap，比较并交换。

它适合处理“把某个位置从旧值改成新值”这类简单原子更新。

可以理解成：

> 桶为空时，不一定要先加锁；可以用 CAS 原子抢占这个空桶位置。

---

### 3. Java 中的原子操作：CAS 和 synchronized

Java 里常见两类并发控制。

#### CAS / 原子类

例如：

```java
AtomicInteger count = new AtomicInteger(0);
count.compareAndSet(0, 1);
```

含义：

```text
如果当前值还是 0
就改成 1
否则失败
```

适合单个变量或单个位置的简单原子更新。

#### synchronized

`synchronized` 用来保护一段复合逻辑。

基本语法：

```java
synchronized (锁对象) {
    // 需要互斥执行的代码
}
```

示例：

```java
private final Object lock = new Object();

public void add() {
    synchronized (lock) {
        count++;
    }
}
```

含义：

> 进入 `{}` 这段代码前，线程必须先拿到 `lock` 这个对象关联的 monitor 锁；执行完代码块后释放锁。

关键理解：

> synchronized 不是把对象“物理锁住”，也不是让别人完全不能看这个对象。  
> 它只是规定：所有进入同一把锁保护的 synchronized 代码块的线程，必须排队。

如果其他线程完全绕开这把锁直接访问共享变量，锁不会自动拦住它。

---

### 4. 为什么锁对象通常写成 private final

常见写法：

```java
private final Object lock = new Object();
```

原因：

- `private`：外部拿不到这把锁，避免别人乱锁。
- `final`：锁对象不会被换掉，避免一会儿锁 A，一会儿锁 B。

锁对象本身可以没有业务意义，只是用来当“门锁”。

---

### 5. 方法上的 synchronized

普通实例方法：

```java
public synchronized void add() {
    count++;
}
```

近似等价于：

```java
public void add() {
    synchronized (this) {
        count++;
    }
}
```

静态方法：

```java
public static synchronized void add() {
}
```

锁的是类对象：

```java
synchronized (MyClass.class) {
}
```

---

### 6. 桶不为空时为什么要锁桶

如果桶不为空，说明桶里已经有链表或树结构。

此时写操作可能要修改：

- 节点 value
- 节点 next 指针
- 链表尾部
- 红黑树的节点关系

所以 ConcurrentHashMap 需要保护这个桶内部结构。

可以粗略理解为：

```java
synchronized (桶头节点) {
    修改这个桶里的链表 / 树
}
```

注意：

> 锁的是当前 key 命中的桶，不是整张表，也不是旁边的桶。

如果：

```text
key1 -> table[3]
key2 -> table[99]
```

两个线程可以并发修改不同桶。

如果两个线程都命中：

```text
table[3]
```

那就必须在这个桶内部同步。

核心思想：

> 冲突在哪个桶，就只保护哪个桶。

---

### 7. 读线程会不会被锁住

不一定。

ConcurrentHashMap 的 `get` 通常不加锁。

它可以沿着已经安全发布出来的节点读取：

```text
定位桶
  ↓
沿链表 / 树查找 key
  ↓
返回 value
```

这不是数据库快照读。

它可能看到：

- 修改前的值
- 修改后的值
- 并发过程中某个安全可见状态

但它不应该看到：

- 被破坏的链表结构
- 乱指针
- 内部结构崩坏

可以总结为：

> ConcurrentHashMap 的读是弱一致的，但内部结构必须是安全可遍历的。  
> 安全不等于一定最新。

---

### 8. 链表修改时为什么还能遍历

链表结构相对简单。

例如原来：

```text
A -> B -> null
```

写线程追加 C：

```text
A -> B -> C -> null
```

读线程可能早一点读到：

```text
B.next == null
```

于是这次看不到 C。

也可能晚一点读到：

```text
B.next == C
```

于是能看到 C。

这两种都可以。

关键不是一定看到最新，而是：

> 读线程沿着已经安全发布的 next 指针走，不会读到被改坏的链表结构。

---

### 9. 红黑树桶为什么更复杂

链表只要沿 `next` 走。

但红黑树写入或删除时可能发生：

- 左旋
- 右旋
- 根节点变化
- 父子关系变化
- 颜色变化

如果读线程正按树结构查找，写线程同时旋转，读路径可能被破坏。

所以 ConcurrentHashMap 的树化桶会通过 `TreeBin` 这类结构做额外控制。

不用展开源码，只需记住：

> 链表桶读路径比较简单，读线程可以沿已安全发布的 next 弱一致遍历。  
> 红黑树桶因为旋转会改变树结构，所以需要额外的 TreeBin 控制，避免读线程直接撞上正在变化的危险结构。

---

## 二、Redis 常用数据结构

Redis 对外提供的数据结构不是“底层结构名”，而是面向业务的几种数据模型。

常见包括：

- String
- List
- Hash
- Set
- ZSet / Sorted Set

---

### 1. String

String 是最基础、最通用的 value 类型。

可以存：

- 普通字符串
- 数字字符串
- token
- 二进制数据
- 计数器

例如计数器：

```text
INCR counter
DECR counter
```

可以复述为：

> String 是 Redis 最通用的值类型，也可以用来做计数器。

---

### 2. List

List 是有序列表，支持从两端 push/pop。

适合：

- 简单队列
- 栈
- 最近消息列表
- 简单时间线
- 最近操作记录

示例：

```text
LPUSH task_queue task1
RPOP task_queue
```

表示左边进，右边出，可以做简单队列。

如果是更完整的消息队列语义，Redis 还有 Streams。  
但基础面试里说 List 可以做简单队列是可以的。

---

### 3. Hash

Redis 外层是：

```text
key -> value
```

如果 value 是 Hash，内部又是：

```text
field -> value
```

示例：

```text
user:1001
  name -> "Alice"
  age  -> "20"
  city -> "Tokyo"
```

命令示例：

```bash
HSET user:1001 name "Alice" age 20 city "Tokyo"
```

注意区分：

- `user:1001` 是 Redis 外层 key。
- `name`、`age`、`city` 是 Hash 内部 field。

`xxx:xxx:xxx` 是 Redis key 的命名习惯，不是 Hash 的内部结构。

可以复述为：

> Redis Hash 是一个 Redis key 对应一个小 Map，这个小 Map 里面再存多个 field-value。适合存对象字段，也适合局部字段更新。

---

### 4. Redis key 一般怎么设计

常见方式：

```text
业务名:对象ID
```

例如：

```text
user:1001
order:8888
product:9527
```

层级多一点：

```text
user:profile:1001
order:detail:8888
article:like:123
```

外层 key 通常用：

> 业务类型 + ID

Hash 内部 field 用对象字段名。

---

### 5. Set

Set 是无序去重集合。

适合：

- 去重
- 点赞用户集合
- 标签集合
- 好友集合
- 交集 / 并集 / 差集

示例：

```text
article:123:liked_users -> {1001, 1002, 1003}
```

判断是否点赞：

```bash
SISMEMBER article:123:liked_users 1001
```

共同好友可以用两个好友集合求交集。

可以复述为：

> Set 适合无序去重和集合运算。

---

### 6. ZSet / Sorted Set

ZSet 是带分数的有序集合：

```text
member + score
```

典型场景：

- 排行榜
- 按时间排序的最近浏览
- 带权重的排序集合
- 范围查询

ZSet 底层常说：

> 哈希表 + 跳表

但要有边界：

- 大数据量 / 普通编码下，常见是 hash table + skiplist。
- 小数据量可能用 listpack 等紧凑编码优化。

---

## 三、ZSet 为什么是“哈希表 + 跳表”

ZSet 同时需要两种能力：

1. 按 member 快速定位。
2. 按 score 有序访问。

单独用哈希表或单独用跳表，都只能满足一半。

---

### 1. 只用哈希表为什么不行

哈希表适合：

```text
member -> score
```

它可以快速回答：

```text
某个 member 的 score 是多少？
```

也能快速判断 member 是否存在。

但它没有顺序。

如果要查排行榜前 10，只用哈希表就只能全量扫描再排序。

所以：

> 哈希表解决“找得快”，但不解决“排得好”。

---

### 2. 只用跳表为什么也不够

跳表适合：

- 按 score 排序
- 范围查询
- 排名
- 前 K 查询

但跳表主要按 score 组织。

如果只用跳表，根据 member 找某个元素就不方便。

所以：

> 跳表解决“排得好”，但不解决“按 member 找得快”。

---

### 3. 两者分工

可以这样理解：

```text
dict: member -> score
skiplist: 按 score + member 有序组织节点
```

哈希表负责：

- 判断 member 是否存在
- 快速获取旧 score
- 支持按 member 的快速操作

跳表负责：

- score 排序
- 范围查询
- 排名
- 前 K

更新 score 时：

```text
通过 dict 找到旧 score
  ↓
用 old score + member 去跳表中删除旧节点
  ↓
插入 new score + member 的新位置
  ↓
更新 dict 中的 score
```

面试短句：

> 哈希表解决“按 member 找得快”，跳表解决“按 score 排得好”。ZSet 两个能力都要，所以两个结构都维护。

---

## 四、跳表结构

### 1. 最小模型

跳表可以理解为：

> 一条按 score 有序的底层链表 + 多层稀疏索引。

示意：

```text
Level 3:        2 -------- 4
Level 2:        2 ---- 4 ---- 6
Level 1:  1 ---- 2 ---- 3 ---- 4 ---- 5 ---- 6
```

最底层保存所有节点。  
上层是通过随机概率从下层抽样出来的快速通道。

查找时：

```text
先在高层快速跳
跳不动了就下降一层
再继续跳
最后落到底层
```

---

### 2. Redis 跳表节点不是靠显式 down 指针

教学图里经常画成上下左右都有指针。

但 Redis 实现更像：

```text
node {
  member
  score
  backward
  level[0].forward
  level[1].forward
  level[2].forward
  ...
}
```

也就是说：

> 一个节点对象自己带多层 forward 指针。

它不是每一层都创建一个独立节点，再用 down 指针连起来。

---

### 3. “下降一层”是什么意思

下降一层不是：

```text
node.down
```

而是：

```text
当前节点不变
层号 i--
改看当前节点更低一层的 level[i].forward
```

例如节点 2 有三层：

```text
node 2:
  level[2].forward -> null
  level[1].forward -> 4
  level[0].forward -> 3
```

查找时，如果在 level 2 跳不动了，就改看同一个节点 2 的 level 1 forward。

关键句：

> 下降一层 = 停在同一个节点，改用更低一层的 forward 指针继续找。

---

### 4. Redis 跳表节点的指针

Redis 跳表节点可以粗略理解为：

- 每个节点有随机层高。
- 每层有一个 `forward` 指针。
- 节点有 `backward` 指针，方便从后往前遍历。
- 每层还有 `span`，用于排名计算。

不需要记源码，但要理解：

> Redis 跳表不是普通双向链表。它的节点能同时拥有多层 forward 指针。

---

### 5. 层数怎么来

跳表的节点层数是随机生成的。

Redis 常见参数：

```text
最大层数：32
晋升概率：0.25
```

直觉：

```text
第 1 层：所有节点都有
第 2 层：约 1/4 节点有
第 3 层：约 1/16 节点有
第 4 层：约 1/64 节点有
...
```

所以越高层越稀疏。

这就是随机化索引。

---

### 6. ZSet 里的跳表按什么排序

ZSet 的节点按：

```text
score 排序
score 相同，再按 member 字典序排序
```

所以每个元素在跳表里的位置是确定的。

---

### 7. 前 K 查询

因为底层是有序链表：

- 前 K 小：从头部方向顺序取 K 个。
- 前 K 大：可以从尾部配合 backward 指针反向取 K 个。

所以跳表很适合排行榜、范围查询、排名查询。

---

## 五、Redis 为什么用这些结构，而不是都交给数据库/后端

数据库和后端当然也能做这些事情。

Redis 的意义不是“数据库不能做”，而是：

> 把高频、简单、需要快的数据操作放在内存里做。

---

### 1. Redis vs 数据库

数据库更适合：

- 最终正确性
- 长期保存
- 事务
- 复杂查询
- 关系建模

Redis 更适合：

- 缓存
- 临时状态
- 高频简单操作
- 低延迟访问
- 热点数据
- 排行榜
- 计数器
- 验证码 / token / 限流

可以理解成：

> 数据库负责准，Redis 负责快。

---

### 2. Redis vs 后端内存 HashMap

后端进程自己用 HashMap 也能缓存，但有问题。

#### 多实例不共享

如果有三台 Java 服务：

```text
Java A
Java B
Java C
```

每台机器自己的 HashMap 都不同。

Redis 是独立服务，多个后端实例可以共享：

```text
Java A \
Java B  -> Redis
Java C /
```

#### 进程重启会丢

Java 进程重启，内存数据没了。

Redis 虽然也是内存数据库，但它可以配置持久化，也更适合作为独立缓存/状态服务。

#### 过期、原子操作自己写麻烦

Redis 天然支持：

- EXPIRE
- INCR
- SETNX
- SADD
- ZADD
- ZRANGE

后端自己实现容易复杂且出错。

---

## 六、Redis 正确性与缓存策略

### 1. 基本原则

> Redis 通常不是最终真相，数据库才是。  
> Redis 负责快，数据库负责准。

正确性的关键不是让 Redis 永远不旧，而是：

> Redis 失效时能回源数据库重建；数据库变更后能让 Redis 失效或更新。

---

### 2. Cache Aside 模式

读流程：

```text
先查 Redis
  ↓
有：直接返回
  ↓
没有：查数据库
  ↓
查到后写入 Redis
  ↓
返回
```

这是最常见的旁路缓存模式。

---

### 3. 数据改了怎么办

常见做法：

```text
更新 MySQL
  ↓
删除 Redis 缓存
```

为什么常删缓存，而不是直接更新缓存？

因为缓存可能是：

- 对象缓存
- 列表缓存
- 集合缓存
- 聚合结果缓存
- 排行榜片段缓存

直接更新容易漏。

所以更常见：

> 数据库改了，删缓存；下次读时再从数据库重建。

简单字段也可以同步更新 Redis，例如 Hash 某个 field。

---

### 4. 交集类数据怎么保证

例如共同好友：

```text
user:1:friends
user:2:friends
```

如果 Redis 里没有集合：

```text
Redis miss
  ↓
从数据库查 user1 好友列表
  ↓
写入 Redis Set
  ↓
从数据库查 user2 好友列表
  ↓
写入 Redis Set
  ↓
再用 Redis 做交集
```

通常缓存的是“基础集合”，不一定缓存交集结果。

关系变更时：

- 简单场景：`SADD/SREM` 同步更新 Redis。
- 复杂场景：`DEL user:1:friends`，下次重建。

---

### 5. 最近浏览

最近浏览分两类。

#### 临时展示型

可以直接写 Redis：

```text
用户浏览商品
  ↓
LPUSH / ZADD 到 Redis
  ↓
设置 TTL 或裁剪长度
```

不一定先查数据库。

#### 持久化型

如果网站要求：

- 跨设备还能看到
- 退出登录后再登录还能看到
- 几天后还在
- 用于推荐 / 画像 / 统计

则应该落数据库。

合理结构：

```text
数据库 = 最终记录
Redis = 最近一段时间的快速读取 / 快速更新缓存
```

流程：

```text
用户浏览商品
  ↓
写 Redis，快速更新最近浏览列表
  ↓
同步或异步写数据库
  ↓
查询时先读 Redis
  ↓
Redis miss 再查数据库并回填
```

---

### 6. 最近浏览用 ZSet 更自然

如果需要：

- 去重
- 按最近时间排序
- 同一个商品再次浏览时更新位置

ZSet 更适合。

结构：

```text
key: user:1001:recent_views
member: 商品ID
score: 浏览时间戳
```

示例：

```text
ZADD user:1001:recent_views 1714300000 product:888
ZREVRANGE user:1001:recent_views 0 99
```

好处：

- 按时间排序
- member 去重
- 重复浏览更新 score
- 查询最近 N 个方便

数据库可以用：

```text
user_browse_history
- user_id
- item_id
- browse_time
```

如果只保留用户对某商品最近一次浏览，可以对：

```text
(user_id, item_id)
```

做唯一约束，重复浏览时更新 `browse_time`。

---

### 7. TTL 怎么设置

TTL 没有统一标准，要看数据类型。

#### 最近浏览

如果数据库已持久化，Redis 只是缓存：

```text
Redis TTL：几小时 ～ 几天
数据库保留：30 天 / 90 天 / 更久
```

关键：

> Redis TTL 不等于业务数据保留时长。

#### 好友集合

好友集合属于关系数据缓存。

可以：

```text
长 TTL + 变更时删除缓存
```

例如：

```text
TTL：30 分钟 / 1 小时 / 几小时
好友关系变更：DEL user:1001:friends
```

也可以不主要依赖 TTL，靠主动失效或同步更新：

```text
SADD / SREM
或者 DEL 后重建
```

#### 验证码 / 限流 / 临时 token

这类 TTL 是业务语义本身：

```text
验证码：5 分钟
限流 key：1 秒 / 1 分钟
token：按登录态设计
```

---

## 七、Redis 为什么快

Redis 快不是单一原因，而是一条因果链。

核心目标：

> 大量请求来了，怎么尽快读写一小份数据？

Redis 的设计取舍：

```text
高频简单数据访问
  ↓
主要数据在内存里，减少磁盘随机 IO
  ↓
单个命令通常很短
  ↓
命令执行主要单线程，避免锁竞争和线程切换
  ↓
但单线程不能傻等某个连接
  ↓
使用 IO 多路复用同时管理大量连接
  ↓
配合贴近业务的数据结构
  ↓
适合缓存、计数器、排行榜、集合判断等场景
```

面试版：

> Redis 快主要是因为它面向高频、简单的数据访问场景。数据主要在内存里，读写路径大多不依赖磁盘随机 IO；命令执行模型主要是单线程，避免了复杂锁竞争和线程上下文切换；网络层使用 IO 多路复用，一个线程可以同时管理大量连接，谁就绪就处理谁；再加上 String、Hash、Set、ZSet 等数据结构贴近业务，很多操作可以在 Redis 内部高效完成。

注意：

> 单线程不是 Redis 快的唯一原因。  
> 单线程减少了锁竞争，但前提是 Redis 命令短、内存操作快。  
> 如果命令很慢，单线程反而会阻塞后续请求。

---

## 八、Redis 单线程与串行执行

### 1. Redis 真的是单线程吗

不完全是。

更准确：

> Redis 不是整个程序只有一个线程。  
> 平时说 Redis 单线程，主要指核心命令执行和数据结构操作主要由主线程串行完成。

Redis 也可能有：

- 持久化后台线程/进程相关工作
- 异步释放大对象
- 后台任务
- Redis 6 之后的网络 IO 多线程

Redis 6 后网络 IO 多线程主要分担：

- 读请求
- 写响应
- 协议解析相关部分工作

核心命令执行仍主要是主线程串行。

可以复述为：

> Redis 的“单线程”重点是命令执行单线程，不是整个 Redis 进程只有一个线程。

---

### 2. 什么叫串行执行

串行执行就是：

> 一次只执行一个任务，前一个任务执行完，后一个任务才能开始。

例如同时来了三个命令：

```text
A: SET name huan
B: INCR count
C: GET name
```

Redis 主线程按顺序执行：

```text
先执行 A
A 执行完
再执行 B
B 执行完
再执行 C
```

不是三个命令被三个线程同时执行。

这样带来的好处：

> 避免多个线程同时修改 Redis 内部数据结构，减少锁竞争，也让很多命令天然具备原子性。

例如两个 `INCR count`：

```text
第一个 INCR: 0 -> 1
第二个 INCR: 1 -> 2
```

最终结果正确。

---

## 九、IO 多路复用

### 1. 它解决什么问题

Redis 命令执行主要是单线程。

如果它傻等某个客户端：

```text
等 A 发请求
处理 A
再等 B
```

那么 A 一直不发数据，Redis 就会卡住。

IO 多路复用解决的是：

> 一个线程不要阻塞等待某一个连接，而是同时监听很多连接，谁准备好了就处理谁。

---

### 2. 多路复用是什么意思

- 多路：很多 socket 连接。
- 复用：复用一个线程 / 一个事件循环去管理它们。

一句话：

> IO 多路复用 = 一个线程同时管理多个 IO 连接。

它不是让一个线程同时执行多个命令，而是：

> 不让线程被某个没准备好的连接卡住。

---

### 3. Redis 的事件循环

可以粗略理解为：

```text
等待事件
  ↓
某些客户端 socket 可读
  ↓
读取请求
  ↓
执行 Redis 命令
  ↓
把响应写回客户端
  ↓
继续等待事件
```

---

## 十、select / poll / epoll

### 1. socket 和 fd

在操作系统中，socket 也是文件描述符 fd。

Redis 监听的不是“客户端”这个抽象概念，而是很多 fd。

例如：

```text
client A socket fd
client B socket fd
client C socket fd
```

哪个 fd 可读，说明有请求来了。  
哪个 fd 可写，说明可以写响应。

---

### 2. 可读 / 可写是什么意思

#### 可读

```text
客户端发请求
  ↓
网卡收到数据
  ↓
内核把数据放入 socket 接收缓冲区
  ↓
fd 变成可读
```

#### 可写

```text
Redis 要写响应
  ↓
socket 发送缓冲区有空间
  ↓
fd 变成可写
```

IO 多路复用本质上是在问内核：

> 这些 fd 里面，哪些现在可以读/写了？

---

### 3. select

流程：

```text
用户态准备一堆 fd
  ↓
把 fd 集合拷贝到内核
  ↓
内核挨个检查这些 fd 是否就绪
  ↓
如果都没就绪，线程睡眠
  ↓
某个 fd 就绪后唤醒
  ↓
内核把结果拷贝回用户态
  ↓
用户程序再遍历找出具体就绪 fd
```

问题：

- 每次都要传 fd 集合。
- 内核要扫描。
- 返回后用户程序也要扫描。
- 有数量限制和效率问题。

类比：

> 每次全班点名。

---

### 4. poll

`poll` 用数组结构描述 fd 和关注事件。

例如：

```c
struct pollfd {
    int fd;
    short events;
    short revents;
};
```

流程和 select 类似：

```text
用户态传 pollfd 数组
  ↓
内核遍历数组
  ↓
检查每个 fd 是否就绪
  ↓
没就绪就睡眠
  ↓
有事件后唤醒
  ↓
把结果写回数组
```

改进：

- 结构比 select 灵活。
- fd 数量限制少一些。

但核心问题还在：

> 每次还是要遍历一批 fd。

---

### 5. epoll

epoll 把事情拆成两步。

#### 第一步：注册 fd

```text
epoll_ctl()
```

告诉内核：

```text
我要监听 fd 3
我要监听 fd 4
我要监听 fd 5
```

内核维护一个 epoll 对象。

#### 第二步：等待就绪事件

```text
epoll_wait()
```

内核维护就绪队列。

当某个 socket 收到数据：

```text
网卡收到数据
  ↓
内核放入 socket 接收缓冲区
  ↓
fd 变成可读
  ↓
内核把 fd 放入 epoll 就绪队列
```

`epoll_wait()` 返回已经就绪的 fd。

类比：

> 先登记名单，谁有事谁举手，老师只看举手的人。

---

### 6. 三者对比

```text
select:
每次传 fd 集合，内核扫描，用户回来也要扫描。

poll:
每次传 fd 数组，内核扫描，结构更灵活。

epoll:
fd 先注册到内核，内核维护就绪队列，epoll_wait 返回已就绪 fd。
```

最短记忆：

> select/poll 是“每次全班点名”，epoll 是“先登记，谁有事谁举手”。

---

### 7. 为什么 epoll 适合大量连接

高并发服务常见特点：

```text
连接很多
但同一瞬间真正活跃的连接不多
```

比如 10 万连接，这一刻只有 100 个发请求。

select / poll 可能仍然围着大量 fd 扫。  
epoll 可以直接返回那 100 个就绪 fd。

所以：

> 大量连接、活跃比例较低时，epoll 更适合。

但不要说 epoll 永远 O(1) 神器。

更准确：

> epoll 避免了每次传入全部 fd，也避免了每次线性扫描全部监听 fd；但它内部仍然有数据结构和事件队列维护成本。

---

## 十一、内核是什么，为什么要交给内核看

### 1. 内核是什么

内核是操作系统里真正管理硬件和系统资源的核心部分。

应用程序运行在用户态，不能直接随便操作硬件。

应用想做这些事：

- 读网卡数据
- 往 socket 写数据
- 读写磁盘
- 创建线程/进程
- 分配内存

都要通过系统调用请求内核。

关系：

```text
应用程序
  ↓ 系统调用
操作系统内核
  ↓
硬件 / 网络 / 磁盘 / 内存
```

内核负责：

- 管理 CPU
- 管理内存
- 管理文件系统
- 管理网络协议栈
- 管理进程线程
- 管理 socket
- 管理硬件中断

可以理解成：

> 应用程序和硬件之间的总管。

---

### 2. 为什么 socket 状态由内核掌握

客户端给 Redis 发请求：

```text
客户端发 TCP 包
  ↓
网卡收到数据
  ↓
触发中断 / 内核处理
  ↓
内核 TCP 协议栈解析
  ↓
数据放进对应 socket 接收缓冲区
  ↓
这个 socket 变成可读
```

这里的关键：

> 有没有数据到达，是内核先知道的，不是 Redis 先知道的。

Redis 是用户态程序。

它不知道网卡什么时候收到包，也不知道哪个 socket 缓冲区有数据。

所以它只能问内核：

> 我关心这些 fd，哪个现在可以读/写？

---

### 3. 为什么应用程序不能自己看

如果每个应用都能直接操作硬件，会很危险：

- 可能乱读别的进程数据
- 可能破坏网络状态
- 可能抢硬件资源
- 系统安全和稳定性会崩

所以操作系统区分：

#### 用户态

普通程序运行的地方：

- Redis
- Java 程序
- 浏览器

权限低，不能直接碰硬件。

#### 内核态

操作系统核心运行的地方。

权限高，可以管理硬件和资源。

应用只能通过系统调用请求内核帮忙。

---

### 4. 和 Redis 连起来

Redis 主线程负责执行命令。  
内核负责盯网络事件。

完整链路：

```text
Redis 单线程执行命令
  ↓
不能傻等某个客户端
  ↓
socket 状态由内核掌握
  ↓
把很多 fd 交给内核监听
  ↓
select / poll / epoll 返回就绪 fd
  ↓
Redis 只处理已经准备好的连接
```

一句话：

> Redis 主线程负责处理命令，内核负责监听网络事件；IO 多路复用就是让内核帮 Redis 同时盯很多 socket，谁就绪了 Redis 再处理谁。

---

## 十二、当前 Redis 后续待补点

目前 Redis 已补：

- 常用数据结构
- Hash 的 key / field 结构
- List / Set 用途
- ZSet 为什么用跳表 + 哈希表
- 跳表结构
- Redis 为什么快
- 单线程、串行执行
- IO 多路复用
- select / poll / epoll
- 内核与 socket 事件

后续还可以继续补：

1. Redis 持久化：RDB / AOF
2. Redis 过期删除策略
3. Redis 内存淘汰策略
4. 缓存一致性
5. 缓存穿透 / 击穿 / 雪崩
6. Redis 分布式锁
7. 主从复制 / 哨兵 / Cluster
8. 大 key / 热 key

建议下一步：

> Redis 持久化：RDB 和 AOF。

---

## 十三、收尾状态（2026-05-01）

这次先只下沉了 Redis 数据结构主线，已沉淀到：

- [[10_topics/Redis 常见数据结构分别适合什么场景]]
- [[10_topics/ZSet 为什么同时需要哈希表和跳表]]
- [[10_topics/Redis 跳表结构怎么理解]]

这次故意继续留在 `00_inbox` 的内容：

- ConcurrentHashMap 的补充细节，后续更适合进 `06_fragments`
- Redis 为什么快
- Redis 单线程、串行执行、IO 多路复用、`select/poll/epoll`
- Redis 与数据库 / 后端内存的取舍
- Cache Aside、TTL、最近浏览、共同好友这些缓存设计与业务例子
- Redis 持久化、过期删除、内存淘汰等后续主线

当前判断：

- Redis 数据结构主线：第一轮主干蒸馏完成
- 2026-04-29 整批：未完成

下一步建议：

> 先做 Redis 性能模型主线，再处理 ConcurrentHashMap 补充到 `06_fragments`。

---

## 十四、Redis 收尾状态（2026-05-03）

这次补完了 Redis 剩余主线，新增沉淀到：

- [[10_topics/Redis 为什么快]]
- [[10_topics/Redis 单线程、串行执行、IO 多路复用怎么协作]]
- [[10_topics/Redis 与数据库、后端本地内存分别适合承担什么角色]]
- [[05_scaffolds/Redis 面试速答骨架]]
- [[06_fragments/Redis 零散补充]]

当前 Redis 已沉淀的 `10_topics` 包括：

- [[10_topics/Redis 常见数据结构分别适合什么场景]]
- [[10_topics/ZSet 为什么同时需要哈希表和跳表]]
- [[10_topics/Redis 跳表结构怎么理解]]
- [[10_topics/Redis 为什么快]]
- [[10_topics/Redis 单线程、串行执行、IO 多路复用怎么协作]]
- [[10_topics/Redis 与数据库、后端本地内存分别适合承担什么角色]]

故意继续留在 `00_inbox` 的内容：

- ConcurrentHashMap 的补充细节，后续单独进 `06_fragments`
- Redis 持久化：RDB / AOF
- Redis 过期删除策略
- Redis 内存淘汰策略
- 缓存穿透 / 击穿 / 雪崩
- Redis 分布式锁
- 主从复制 / 哨兵 / Cluster
- 大 key / 热 key

当前判断：

- Redis 数据结构主线：完成
- Redis 性能模型主线：完成
- Redis 与数据库 / 后端本地内存角色分工：完成
- Redis scaffold 收尾：完成
- Redis fragment 收尾：完成
- 2026-04-29 整批：仍未完成，因为 ConcurrentHashMap 补充还未处理

下一步建议：

> 单独处理 ConcurrentHashMap 补充，把它吸收到 `06_fragments`，不要新建新的 `10_topics`。

---

## 十五、ConcurrentHashMap fragment 收尾状态（2026-05-03）

这次只处理 ConcurrentHashMap 补充，没有再处理 Redis，也没有新建 `10_topics` / `05_scaffolds`。

已吸收到：

- [[06_fragments/HashMap 与并发 Map 零散补充]]

本次吸收的内容：

- ConcurrentHashMap 不是数据库，没有 MVCC 快照读
- 空桶时 CAS 插入
- Java 里的 CAS 和 `synchronized` 的分工
- 锁对象为什么常写成 `private final`
- 方法上的 `synchronized` 锁什么
- 非空桶时为什么锁桶
- `get` 通常不加锁，但弱一致不等于一定最新
- 链表桶为什么更容易弱一致遍历
- 红黑树桶为什么更复杂，`TreeBin` 只作为边界提醒

故意继续留在 `00_inbox` 的内容：

- ConcurrentHashMap 源码逐行细节
- Java 7 / Java 8 实现差异大全
- TreeBin 更深实现
- 红黑树旋转、染色、规则细节
- Redis 持久化：RDB / AOF
- Redis 过期删除策略
- Redis 内存淘汰策略
- 缓存穿透 / 击穿 / 雪崩
- Redis 分布式锁
- 主从复制 / 哨兵 / Cluster
- 大 key / 热 key

当前判断：

- Redis 数据结构主线：完成
- Redis 性能模型主线：完成
- Redis scaffold 收尾：完成
- Redis fragment 收尾：完成
- ConcurrentHashMap fragment 收尾：完成
- 2026-04-29 整批：完成
