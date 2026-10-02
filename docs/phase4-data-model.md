# DailySchedule · Phase 4 数据模型

| 项 | 内容 |
| --- | --- |
| 版本 | v1.0 |
| 日期 | 2026-09-07 |
| 项目根目录 | `D:\DailySchedule` |
| 上游 | `phase2-prd.md`、`phase3-information-architecture.md` |
| 状态 | 待评审 |
| 下游 | Phase 5 技术架构 |

---

## 1. 设计原则（先立规矩）

| # | 原则 | 含义 |
| --- | --- | --- |
| 1 | **只有两张事实表** | `focus_sessions` 与 `expenses`。`projects` / `categories` 是维度表，不是事实表 |
| 2 | **不建汇总表** | 不建 `daily_summary` / `monthly_stats`。所有统计由查询计算，杜绝冗余与不一致 |
| 3 | **双时钟** | `elapsedRealtime` 算时长（单调，抗系统改时间）；`wallClock` 算归属日期与展示 |
| 4 | **金额用整数分** | 永不使用 Float / Double 存钱 |
| 5 | **软删除** | 会话与消费不物理删除，标 `DISCARDED`，保留审计与误删恢复能力 |
| 6 | **删除项目不删历史** | `projectId` 外键 `SET_NULL`，历史会话永久保留 |
| 7 | **全 App 只有一个日期口径** | 所有周期统计通过 `DayBoundary` 换算出的 `[startMs, endMs)` 区间查询 |

---

## 2. 实体关系

```
projects ──1:N──> focus_sessions
projects ──1:N──> expenses        (projectId 可空，可选关联)
categories ──1:N──> expenses
```

外键行为：

| 关系 | onDelete | 理由 |
| --- | --- | --- |
| `focus_sessions.projectId → projects.id` | `SET_NULL` | 删项目不能删历史投入 |
| `expenses.projectId → projects.id` | `SET_NULL` | 同上 |
| `expenses.categoryId → categories.id` | `RESTRICT` | 分类是消费的必填维度，禁止留下无分类的账 |

> `categories` 的 `RESTRICT` 意味着：**分类只能停用，不能删除**。删除前必须把该分类下的消费转移到别的分类。

---

## 3. 字段级定义

### 3.1 `projects`

```kotlin
@Entity(
    tableName = "projects",
    indices = [Index("sortOrder"), Index("isArchived")]
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                 // 唯一性由 UI 层约束，DB 不加唯一索引（允许重名）
    val iconName: String,             // 存图标名字符串，不存资源 id（可导出、可迁移）
    val colorHex: String,             // "#RRGGBB"
    val isArchived: Boolean = false,
    val dailyTargetMinutes: Int? = null,   // null = 未设目标
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 3.2 `focus_sessions`（核心表）

```kotlin
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("startWallClockMs"),
        Index(value = ["projectId", "startWallClockMs"]),
        Index("status")
    ]
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val projectId: Long?,                 // 可空：项目被删后置 NULL

    // ── 状态 ──
    val status: String,                   // RUNNING / PAUSED / COMPLETED / DISCARDED
    val needsReview: Boolean = false,     // 待确认标记（见 §4.2）

    // ── 双时钟 ──
    val startElapsedMs: Long,             // SystemClock.elapsedRealtime()：算时长
    val startWallClockMs: Long,           // System.currentTimeMillis()：算归属日期 / 展示
    val endElapsedMs: Long? = null,
    val endWallClockMs: Long? = null,

    // ── 暂停累计 ──
    val accumulatedPauseMs: Long = 0,
    val pauseStartElapsedMs: Long? = null, // 非 null 即表示当前处于暂停中

    // ── 模式（番茄 = 带目标时长的正计时）──
    val mode: String = "STOPWATCH",       // STOPWATCH / POMODORO
    val targetDurationMs: Long? = null,

    // ── 结果 ──
    val durationMs: Long? = null,         // 权威时长，见 §5
    val note: String? = null,

    val createdAt: Long,
    val updatedAt: Long
)
```

### 3.3 `expenses`

```kotlin
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = SET_NULL),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = RESTRICT)
    ],
    indices = [
        Index("occurredAt"),
        Index(value = ["categoryId", "occurredAt"]),
        Index("projectId")
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,                // 整数分，永不用浮点
    val currency: String = "CNY",
    val categoryId: Long,
    val projectId: Long? = null,          // 可选关联（P1 才做 UI）
    val type: String = "EXPENSE",         // 预留收入：EXPENSE / INCOME
    val note: String? = null,
    val occurredAt: Long,                 // 用户可改，默认写入时刻
    val createdAt: Long,
    val updatedAt: Long
)
```

### 3.4 `categories`

```kotlin
@Entity(tableName = "categories", indices = [Index("sortOrder")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isPreset: Boolean = false,        // 预置分类不可删除，可停用
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0
)
```

**预置种子数据（首次启动写入）**：餐饮 / 交通 / 购物 / 娱乐 / 学习 / 医疗 / 其他。

### 3.5 偏好设置 —— **不建表，用 DataStore**

| Key | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `day_start_hour` | Int | `4` | 日切时刻。凌晨 4 点前的记录归属前一天 |
| `week_start_day` | Int | `1` | 1 = 周一 |
| `last_used_project_id` | Long? | `null` | 计时页默认选中 |
| `default_pomodoro_minutes` | Int | `25` | 番茄默认时长 |
| `schema_version_ack` | Int | `1` | 导入时用于判断是否需要迁移 |

---

## 4. 状态机与枚举

### 4.1 会话状态机

```
                    ┌──────────────────────────┐
                    │                          │ resume
   start            ▼                          │
  ─────────>  [ RUNNING ] ────pause────> [ PAUSED ]
                    │                          │
                    │ stop                     │ stop
                    ▼                          ▼
              [ COMPLETED ] <──────────────────┘
                    │
                    │ 用户删除
                    ▼
              [ DISCARDED ]   （软删除，不物理删）
```

| 转换 | 触发 | 副作用 |
| --- | --- | --- |
| → `RUNNING` | 点开始 | 写 `startElapsedMs` / `startWallClockMs`；启动前台服务 |
| `RUNNING` → `PAUSED` | 点暂停 | 写 `pauseStartElapsedMs` |
| `PAUSED` → `RUNNING` | 点继续 | `accumulatedPauseMs += now - pauseStartElapsedMs`；置空 `pauseStartElapsedMs` |
| → `COMPLETED` | 点结束 | 写 `end*`；计算并写入 `durationMs` |
| → `DISCARDED` | 用户删除 | 保留行，不参与任何统计 |

### 4.2 `needsReview` 为什么不是状态（**对 PRD 的一处修正**）

PRD 与决策日志里曾把 `PENDING_REVIEW` 写成 status 的第五个枚举值。**这里修正为独立布尔标志。**

| 理由 | 说明 |
| --- | --- |
| 避免状态爆炸 | 会话可能"仍在运行但超长"，也可能"已结束但数据可疑"。若做成状态，需要 `RUNNING_PENDING` / `COMPLETED_PENDING` 两个复合态 |
| 语义正交 | "是否在运行"和"是否需要人看一眼"是两件事，强行合并会让状态机变脏 |
| 查询更简单 | 待确认卡片 = `WHERE needsReview = 1`，与 status 无关 |

**判定规则**（在冷启动 + 每 15 分钟检查一次）：

```kotlin
fun shouldFlag(session: FocusSessionEntity, now: Long): Boolean =
    session.status in setOf(RUNNING, PAUSED) &&
    (now - session.startElapsedMs) > ABNORMAL_THRESHOLD_MS   // 8 小时
```

### 4.3 应用内枚举（Kotlin 层，DB 存字符串）

| 枚举 | 取值 |
| --- | --- |
| `SessionStatus` | `RUNNING` / `PAUSED` / `COMPLETED` / `DISCARDED` |
| `SessionMode` | `STOPWATCH` / `POMODORO` |
| `ExpenseType` | `EXPENSE` / `INCOME`（MVP 只写 `EXPENSE`） |

> DB 存字符串而非序号。**序号一旦重排，历史数据全部错位。** 字符串用 Room 的 `@TypeConverter` 或直接存 String + 常量校验。

---

## 5. `durationMs` 是否违反"单一事实来源"

**不违反，但需要在文档里说清楚，否则后面会被当成冗余字段误删。**

| 场景 | durationMs 的来源 |
| --- | --- |
| 正常结束 | `endElapsed - startElapsed - accumulatedPause` |
| 用户手工修正 | **用户填写的值**，无法从时间戳重算 |

**结论**：`durationMs` 是**权威时长**，`start*/end*` 是**原始证据**。二者并存，用于：

1. 统计查询直接 `SUM(durationMs)`，SQL 干净且可索引；
2. 提供 `verifyDuration(session)` 在自测/导出时比对二者是否一致，不一致即提示。

**真正被禁止的冗余是"汇总表"**——不建 `daily_summary`，不缓存"本月总时长"。这一点没有妥协。

---

## 6. 时长计算与双时钟

### 6.1 为什么需要两个时钟

| 时钟 | API | 特性 | 用途 |
| --- | --- | --- | --- |
| 单调时钟 | `SystemClock.elapsedRealtime()` | 自开机起累加，**含深度睡眠**，不受系统时间/时区修改影响 | **算时长** |
| 墙上时钟 | `System.currentTimeMillis()` | 可能被用户或 NTP 修改，可跨时区 | **算归属日期、展示、导出** |

只用墙上时钟：用户改时间 → 时长变成负数或暴涨。
只用单调时钟：无法知道这段学习属于哪一天。

**两者都存，各司其职。**

### 6.2 计算规则

```kotlin
fun elapsedOf(s: FocusSessionEntity, nowElapsed: Long): Long {
    val end = s.endElapsedMs ?: nowElapsed                    // 未结束 → 用当前
    val pause = s.accumulatedPauseMs +
        (s.pauseStartElapsedMs?.let { nowElapsed - it } ?: 0) // 暂停中也要扣
    return (end - s.startElapsedMs - pause).coerceAtLeast(0)
}
```

**当前正在跑的会话**：`endElapsedMs == null`，每次 UI 刷新时用 `nowElapsed` 实时算。不写库。

### 6.3 首页"今日学习"的口径（易错点）

```
今日学习 = SUM(已完成会话的 durationMs)  +  当前活动会话的实时 elapsed
           └── 来自 DB                    └── 来自内存计算，不入统计缓存
```

**正在跑的会话必须计入首页数字**，否则用户会觉得"我明明在学，为什么是 0"。

---

## 7. 日期边界（DayBoundary）

### 7.1 核心工具

```kotlin
class DayBoundary(private val dayStartHour: Int) {

    /** 时间戳 → 业务日期。04:00 之前算前一天 */
    fun businessDateOf(wallClockMs: Long): LocalDate

    /** 业务日期 → 该日的 [startMs, endMs) */
    fun rangeOf(date: LocalDate): LongRange

    /** 本周 / 本月 / 本年的 [startMs, endMs) */
    fun weekRangeOf(date: LocalDate): LongRange
    fun monthRangeOf(date: LocalDate): LongRange
    fun yearRangeOf(date: LocalDate): LongRange
}
```

### 7.2 铁律

> **所有周期性统计查询，一律接收 `[startMs, endMs)` 两个 Long 参数。
> SQL 中禁止出现 `date('now')` / `strftime` / `LocalDate.now()`。**

**为什么不用 SQL 的日期函数**：SQLite 的 `datetime(...,'-4 hours')` 虽然能实现偏移，但一旦日切时刻可配置、周起始日可配置，SQL 会变得不可读且无法单测。把区间计算放在 Kotlin 层，可以用纯函数单测覆盖所有边界。

### 7.3 归属规则（D2-5）

**按会话的开始时间归属**：`startWallClockMs ∈ [startMs, endMs)` 即计入该日。

跨午夜的会话**不拆分**，整段归属开始日。

### 7.4 必须覆盖的单测用例

| 用例 | 输入 | 期望 |
| --- | --- | --- |
| 普通白天 | 09-07 14:00 | 归属 09-07 |
| 凌晨（日切前） | 09-07 03:30 | 归属 **09-06** |
| 日切临界 | 09-07 04:00:00 | 归属 09-07 |
| 日切前一毫秒 | 09-07 03:59:59.999 | 归属 09-06 |
| 跨午夜会话 | 09-07 23:30 开始，09-08 01:00 结束 | 整段归属 **09-07** |
| 月末 | 09-30 23:00 | 归属 09-30，月汇总计入 9 月 |
| 年末 | 12-31 23:59 | 年汇总计入当年 |
| 跨年周 | 12-31（周一） | 周边界正确 |
| 时区变更 | 切换时区后重开 | 历史数据归属不变（存的是 epoch millis） |

---

## 8. 统计查询模式

### 8.1 今日学习总时长

```sql
SELECT COALESCE(SUM(durationMs), 0) FROM focus_sessions
WHERE status = 'COMPLETED'
  AND startWallClockMs >= :startMs
  AND startWallClockMs <  :endMs
```

### 8.2 今日消费总额

```sql
SELECT COALESCE(SUM(amountCents), 0) FROM expenses
WHERE type = 'EXPENSE'
  AND occurredAt >= :startMs
  AND occurredAt <  :endMs
```

### 8.3 项目排行（时间）

```sql
SELECT p.id, p.name, p.colorHex, SUM(f.durationMs) AS total
FROM focus_sessions f JOIN projects p ON f.projectId = p.id
WHERE f.status = 'COMPLETED'
  AND f.startWallClockMs >= :startMs
  AND f.startWallClockMs <  :endMs
GROUP BY p.id
ORDER BY total DESC
```

### 8.4 分类占比（金钱）

```sql
SELECT c.id, c.name, c.colorHex, SUM(e.amountCents) AS total
FROM expenses e JOIN categories c ON e.categoryId = c.id
WHERE e.type = 'EXPENSE'
  AND e.occurredAt >= :startMs AND e.occurredAt < :endMs
GROUP BY c.id
ORDER BY total DESC
```

### 8.5 项目四级累计

同一个查询，换 4 个区间：今日 / 本周 / 本月 / **累计（无区间，全表）**。
`累计` 不加 `WHERE occurredAt` 条件——这是"考研数学 128h32m"的来源。

### 8.6 时间轴

```sql
SELECT * FROM focus_sessions
WHERE status = 'COMPLETED'
  AND startWallClockMs >= :startMs AND startWallClockMs < :endMs
ORDER BY startWallClockMs ASC
```

**单表查询**（消费不进时间轴，Phase 3 已定）。

---

## 9. 索引策略

| 表 | 索引 | 服务查询 |
| --- | --- | --- |
| `focus_sessions` | `(startWallClockMs)` | 所有周期统计、时间轴 |
| `focus_sessions` | `(projectId, startWallClockMs)` | 项目累计、项目详情 |
| `focus_sessions` | `(status)` | 查找活动会话、待确认 |
| `focus_sessions` | **部分唯一索引**（见下） | 活动会话唯一性 |
| `expenses` | `(occurredAt)` | 周期统计 |
| `expenses` | `(categoryId, occurredAt)` | 分类占比 |
| `expenses` | `(projectId)` | 项目关联消费（P1） |
| `projects` | `(sortOrder)` | 列表排序 |
| `categories` | `(sortOrder)` | 列表排序 |

### 9.1 活动会话唯一性（数据库级硬保障）

```sql
CREATE UNIQUE INDEX idx_single_active_session
ON focus_sessions(status)
WHERE status IN ('RUNNING', 'PAUSED');
```

SQLite 3.8.0+ 支持部分索引（Android 5.0+ 均已满足）。

**作用**：即使出现异常（并发点击、进程恢复竞态、多处入口同时启动计时），数据库层面也不可能同时存在两个活动会话。这比在应用层加锁可靠得多。

---

## 10. 数据完整性约束

| # | 约束 | 落实层 |
| --- | --- | --- |
| 1 | 同一时刻最多 1 个活动会话 | DB 部分唯一索引（§9.1） |
| 2 | `durationMs >= 0` | 计算时 `coerceAtLeast(0)` + 落库前校验 |
| 3 | `endElapsedMs >= startElapsedMs` | 落库前校验，异常则拒绝写入并记日志 |
| 4 | `amountCents > 0` | UI 输入校验 + DB CHECK 约束 |
| 5 | 删除项目 → 会话 `projectId = NULL` | 外键 `SET_NULL` |
| 6 | 删除分类被拒绝 | 外键 `RESTRICT` |
| 7 | 归档项目仍参与统计 | 统计查询**不加** `isArchived` 过滤；只有计时选择器过滤 |
| 8 | 所有写操作在事务内 | Repository 层统一 `@Transaction` |

**约束 7 是最容易写错的一条。** 一旦在统计 SQL 里顺手加了 `WHERE isArchived = 0`，用户归档考研项目的瞬间，三年的历史就"消失"了。

---

## 11. 迁移策略

| 场景 | 策略 |
| --- | --- |
| App 升级（表结构变更） | Room `Migration(from, to)`，逐个版本手写，禁用 `fallbackToDestructiveMigration()` |
| 导出文件版本低于当前 | 导入时按 `schemaVersion` 依次执行内存迁移链，再落库 |
| 导出文件版本高于当前 | **拒绝导入**，提示"请升级 App" |

**为什么禁用破坏性迁移**：这是一个长期生活记录工具（D3-1）。丢数据等于产品死亡。`fallbackToDestructiveMigration()` 在调试期方便，但一旦带进发布版就是定时炸弹。

---

## 12. 导出格式

```json
{
  "schemaVersion": 1,
  "exportedAt": 1767000000000,
  "appVersion": "1.0.0",
  "preferences": {
    "dayStartHour": 4,
    "weekStartDay": 1
  },
  "projects":    [ ... ],
  "categories":  [ ... ],
  "focusSessions": [ ... ],
  "expenses":    [ ... ]
}
```

**导出必须包含 `preferences`。** 否则换机后日切时刻回到默认 4:00，历史统计口径会整体漂移。

CSV 明细两份：`focus_sessions.csv`、`expenses.csv`（供 Excel 自行分析）。

---

## 13. 本阶段的两个设计修正

| # | 修正 | 原因 |
| --- | --- | --- |
| 1 | `PENDING_REVIEW` 从 status 枚举**改为独立布尔标志** `needsReview` | 避免复合状态爆炸；"是否运行"与"是否需要人工确认"语义正交 |
| 2 | `durationMs` 明确定义为**权威时长**（保留原始时间戳作证据） | 手工修正场景下时长无法从时间戳重算；同时保持"不建汇总表"的底线 |

---

## 14. 阶段总结

### 已确定
- 4 张表 + DataStore 的完整字段定义与索引
- 外键行为（项目 SET_NULL / 分类 RESTRICT）
- 会话状态机（4 态）+ `needsReview` 标志
- 双时钟策略与时长计算公式
- 首页"今日学习"的口径（含实时会话）
- `DayBoundary` 铁律与 9 个必测边界用例
- 7 条统计查询 SQL
- 部分唯一索引保障活动会话唯一性
- 8 条数据完整性约束
- 迁移策略与导出格式

### 未确定
- Room 的 DAO 接口具体签名（Phase 5）
- 是否引入 `@Transaction` 之外的并发控制（Phase 5）
- 图标库的图标名集合（Phase 6）

### 决策依据
- 竞品研究：原始数据时事实来源，统计靠查询
- D2-5：按开始时间归属 + 可配置日切
- D1-2：消费可选挂项目
- D1-4：纯本地 + 导出导入为 P0
- D3-1：长期工具 → 禁用破坏性迁移

### 下一阶段
**Phase 5 — 技术架构**：模块划分、依赖方向、Repository / UseCase / ViewModel 分层、前台服务与进程恢复方案、依赖注入、日志与异常处理。
