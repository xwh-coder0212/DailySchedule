# DailySchedule · Phase 5 技术架构

| 项 | 内容 |
| --- | --- |
| 版本 | v1.0 |
| 日期 | 2026-09-07 |
| 项目根目录 | `D:\DailySchedule` |
| 上游 | `phase2-prd.md`、`phase3-information-architecture.md`、`phase4-data-model.md` |
| 状态 | 待评审 |
| 下游 | Phase 6 UI/UX |

---

## 1. 架构总原则

| # | 原则 | 说明 |
| --- | --- | --- |
| 1 | **单向依赖，UI 不认识 Room** | `feature → domain ← data`。UI 层不得出现任何 `androidx.room` import |
| 2 | **DB 是唯一真相，内存只是缓存** | 活动会话的权威状态在 `focus_sessions` 表；进程内 StateFlow 只是投影 |
| 3 | **时间戳即状态** | 计时不靠"每秒 +1"，任何时刻都能从两个时间戳算出正确时长 |
| 4 | **前台服务不承载正确性** | 服务只负责通知与番茄到点。服务被杀 ≠ 计时错 |
| 5 | **纯本地，零网络** | Manifest 不声明 `INTERNET`，从系统层面杜绝数据外流 |
| 6 | **显式并发** | 所有 `Dispatcher` 由 Hilt 注入，不在业务代码里硬编码 `Dispatchers.IO` |
| 7 | **不为假想需求抽象** | 只给"有业务规则"的操作建 UseCase；纯查询直接走 Repository |

---

## 2. 模块与包结构

**结论：MVP 用单模块 + 严格分包**，不搞多 Gradle 模块。

| 理由 | 说明 |
| --- | --- |
| 规模 | 12 个页面、4 张表，多模块带来的编译收益接近 0 |
| 成本 | 多模块要维护 `:core:*` / `:feature:*` 的 API 边界、依赖图和构建配置，对单人是净负担 |
| 可逆 | 只要包结构干净，未来按包切模块是机械操作 |

```
com.dailyschedule.app
│
├── app/                        Application · MainActivity · AppNavHost
├── di/                         Hilt Modules（Database / Repository / Dispatcher / Clock）
│
├── core/
│   ├── time/       Clock · DayBoundary · DurationCalculator · DurationFormatter
│   ├── result/     AppError · AppResult · ErrorMapper
│   ├── log/        AppLogger · FileLogTree · CrashHandler
│   └── ui/         Theme · 基础组件 · 状态页（空/加载/错误）
│
├── data/
│   ├── db/         AppDatabase · *Entity · *Dao · Converters · Migrations
│   ├── datastore/  UserPreferencesDataSource
│   ├── mapper/     Entity ⇄ Domain 映射
│   ├── repository/ *RepositoryImpl
│   └── export/     JsonExporter · CsvExporter · DataImporter · SchemaMigrator
│
├── domain/
│   ├── model/      Project · FocusSession · Expense · Category · 统计聚合模型
│   ├── repository/ 接口（SessionRepository / ExpenseRepository / ...）
│   └── usecase/    仅放"有业务规则"的操作（见 §5）
│
├── timer/
│   ├── TimerForegroundService
│   ├── TimerController        进程内唯一的计时入口
│   ├── TimerStateStore        StateFlow<ActiveSessionState?>
│   ├── notif/     TimerNotificationBuilder · NotificationChannels
│   └── widget/    TimerGlanceWidget · WidgetUpdater
│
└── feature/
    ├── home/      stats/      record/      project/      settings/
    │   └ 每个包内：*Screen(Compose) · *ViewModel · *UiState
```

### 2.1 依赖方向铁律

```
feature  ──→  domain  ←──  data
   │             ▲            │
   └─────────────┴────────────┘
              core
```

- `feature` **不得** import `data` 或 `androidx.room.*`
- `domain` **不得** import `data`、`feature`、`android.*`（纯 Kotlin，可 100% JVM 单测）
- `core` **不得** import 任何其他层的业务类型

**已定：以上三条由 Konsist 架构测试自动校验**（Phase 8 实现）。靠人自觉守不住，靠 code review 太贵。

```kotlin
// ArchitectureTest.kt（示例）
@Test fun `UI 层不得依赖 data 层与 Room`() {
    Konsist.scopeFromProduction()
        .classes().withPackage("com.dailyschedule.feature..")
        .assertFalse { it.hasImport { i -> i.name.startsWith("androidx.room") } }
}
```

四条待校验规则：

1. `feature` 包不得 import `androidx.room.*`
2. `feature` 包不得 import `com.dailyschedule.data.*`
3. `domain` 包不得 import `android.*`（保证纯 Kotlin，可 JVM 单测）
4. `domain` 包不得 import `com.dailyschedule.data.*` / `feature.*`

### 2.2 主题包抽象（可替换视觉风格）

**2026-09-08 决策**：整体配色先定为"石墨青"，但**未来要支持多套视觉风格**（卡通风、简洁风、少女风等）。这是一条架构约束，不是 UI 细节。

如果现在把颜色写死在组件里，将来换风格要改几百处。**实现成本必须在今天付掉，不到 1 小时；留到将来是几天的返工。**

```kotlin
interface ThemePack {
    val id: String                       // "graphite_teal" / "cartoon" / ...
    val displayName: String
    fun colorScheme(dark: Boolean, monet: Boolean): ColorScheme
    val shapes: Shapes
    val typography: Typography
}

object GraphiteTealTheme : ThemePack { ... }   // MVP 唯一实现
object CartoonTheme : ThemePack { ... }        // 未来
```

| 规则 | 说明 |
| --- | --- |
| 颜色只走语义 token | 组件内一律 `DsTheme.colors.primary`、`DsTheme.colors.onSurface`，**禁止** `Color(0xFF0F6E56)` |
| 形状与字号同样走 token | 卡通风可能需要更大的圆角和更粗的字重，写死就换不了 |
| 项目色规范化**跨主题包通用** | §2.3（Phase 6）的算法属于色彩系统基础设施，不随主题包变 |
| 新增风格 = 新增一个实现类 | 不动任何组件，不改任何页面 |

**校验方式**（Phase 8 实现）：一个 JVM 单测扫描 `src/main/kotlin`，正则匹配硬编码颜色：

```kotlin
private val HEX = Regex("""Color\(0x[0-9A-Fa-f]{8}\)|"#[0-9A-Fa-f]{6}"""")

@Test fun `组件中不得硬编码颜色`() {
    val offenders = sourceFiles()
        .filterNot { it.path.contains("core/ui/theme/") }   // 主题包自身除外
        .filter { HEX.containsMatchIn(it.readText()) }
    assertThat(offenders).isEmpty()
}
```

20 行代码，比任何 code review 都可靠。

---

## 3. 技术选型与工具链

### 3.1 版本快照（2026-09 查询所得，**Phase 7 开工当天用 `libs.versions.toml` 锁死**）

| 类别 | 选型 | 说明 |
| --- | --- | --- |
| 语言 | Kotlin 2.4.x | 2.4.10 为当前修复版；配 Gradle 9.x |
| 构建 | AGP 9.x + Gradle 9.x + Version Catalog | Compose 1.12 起要求 AGP 9 + compileSdk 37 |
| UI | Jetpack Compose + Material3 | BOM 统一管理 |
| 编译期 | KSP | Room / Hilt 均走 KSP |
| 数据库 | Room 3.0.x | 2026-07 转稳定。若遇破坏性变更则退锁 2.7.x |
| DI | Hilt 1.4.x | Google 官方推荐，`hilt-navigation-compose` 提供 `hiltViewModel()` |
| 异步 | Coroutines + Flow | |
| 导航 | Navigation Compose（type-safe routes） | 底部 Tab 独立返回栈方案成熟。Navigation 3 作后续评估，MVP 不上 |
| 存储偏好 | DataStore Preferences | 替代 SharedPreferences |
| 后台 | WorkManager | 周期自检 + 导出/导入 |
| 小组件 | Glance | |
| 序列化 | kotlinx-serialization-json | 导出格式 + type-safe route |
| 测试 | JUnit4 · Truth · Turbine · MockK · Robolectric · Room Testing | |

**SDK 配置**：`minSdk 26`（`java.time` 原生可用，无需 desugaring）、`compileSdk 37`、`targetSdk 36+`。

> Play 自 2026-08-31 起要求新应用/更新 target 到 API 36。本项目是自用 APK 分发，不受此限，但按官方推荐对齐没有坏处。

### 3.2 两个容易踩的坑

| # | 坑 | 处理 |
| --- | --- | --- |
| 1 | **16 KB 内存页** | Android 15+ 出现了页大小为 16 KB 的设备。纯 Kotlin/Java 代码跑在 ART 上不受影响，**但 Room 通过 `androidx.sqlite` 捆绑了原生 SQLite 的 `.so`**。原生库若按 4 KB 对齐加载，在 16 KB 页设备上会加载失败 → 启动即崩。必须确认 Room / androidx.sqlite 版本已按 16 KB 重新对齐（Google 已在新版本中处理，开工时验证；验证方法：解压 APK 检查 `lib/*/libsqlite3x.so` 的段对齐）。**实际风险不高，风险在于"以为纯 Kotlin 就万事大吉"从而不验证** |
| 2 | **Compose 与 compileSdk 绑定** | 升 Compose BOM 会强制升 compileSdk → 强制升 AGP。三者必须一起评估，不能单独点升级 |

---

## 4. 分层职责

```
┌─────────────────────────────────────────────────────┐
│  UI (Compose)                                        │
│  · 只渲染 UiState，不做业务判断                       │
│  · 用户操作 → 调 ViewModel 方法（不传 Context）        │
└────────────────────┬────────────────────────────────┘
                     │  UiState (StateFlow) ↑ / Event ↓
┌────────────────────┴────────────────────────────────┐
│  ViewModel                                           │
│  · 持有 UiState，处理一次性事件                        │
│  · 调 UseCase（写）/ Repository（读）                 │
│  · try/catch → AppError → UiState.error              │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────┐
│  UseCase（仅"有规则"的写操作）                        │
│  · 状态机校验、异常会话判定、金额校验                  │
│  · 纯 Kotlin，可 JVM 单测                             │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────┐
│  Repository 接口（domain） ← RepositoryImpl（data）   │
│  · 事务边界                                          │
│  · Entity ⇄ Domain 映射                              │
│  · 对外只暴露 Flow<T> / suspend                      │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────┐
│  DAO (Room) / DataStore / FileStore                  │
└─────────────────────────────────────────────────────┘
```

### 4.1 为什么坚持做 Entity ⇄ Domain 映射

多写一个 20 行的机械映射函数，换来三件事：

1. UI 层永远不会 import `androidx.room`，换持久化方案不动 UI；
2. Room 表结构演进（拆表、改列名）不外溢；
3. domain model 可以用 `value class`、不可变集合、`Duration` 等更适合业务的类型，不受 Room 类型限制。

**成本是一次性的，收益是长期的。** 这是"专业工程"和"能跑的 Demo"的分界线之一。

---

## 5. UseCase 的取舍

**只为"有业务规则"的写操作建 UseCase；纯查询直接走 Repository。**

| 建 UseCase | 不建（直接 Repository） |
| --- | --- |
| `StartSessionUseCase` | `observeTodaySessions()` |
| `PauseSessionUseCase` | `observeTodayTotalDuration()` |
| `ResumeSessionUseCase` | `observeProjectStats(id)` |
| `StopSessionUseCase` | `observeExpenseTotal(range)` |
| `ResolveAbnormalSessionUseCase` | `observeProjects()` |
| `AddExpenseUseCase` | |
| `UpdateExpenseUseCase` / `DeleteExpenseUseCase` | |
| `AdjustSessionDurationUseCase` | |
| `ExportDataUseCase` / `ImportDataUseCase` | |

> 给每个查询都套一层 UseCase 是"仪式性抽象"，只会让改一个查询要跳三个文件。

---

## 6. 计时子系统设计（本阶段核心）

### 6.1 三个必须想清楚的前提

| 前提 | 结论 |
| --- | --- |
| 计时正确性依赖什么？ | **只依赖 DB 里的两个时间戳**，不依赖任何存活的进程 |
| 前台服务的职责是什么？ | **只有两件**：常驻通知（可见 + 快捷操作）、番茄到点提醒 |
| 服务被杀了会怎样？ | 通知消失，但**数据一条不丢**。用户下次打开 App 时从 DB 恢复视图 |

这三条决定了整个子系统的容错上限：**最坏情况只是"用户看不到通知"，永远不会是"时长算错"。**

### 6.2 组件构成

```
TimerController（进程内单例，唯一写口）
   │  写 DB（事务）
   ▼
focus_sessions 表  ────Flow────>  TimerStateStore ────> UI / 小组件
   │                                                    
   └────────────────────────────>  TimerForegroundService（通知）
```

**唯一写口原则**：所有状态转换只能经 `TimerController`。UI、通知按钮、小组件按钮都只调它的方法，不直接碰 DAO。否则状态机会散落各处，出 bug 无从排查。

### 6.3 时间源抽成接口

```kotlin
interface Clock {
    fun elapsedRealtime(): Long      // 单调，算时长
    fun wallClockMillis(): Long      // 墙上，算归属日期
}
```

**所有取时间的地方必须注入 `Clock`，禁止直接调 `SystemClock.*` / `System.currentTimeMillis()`。**

理由：单测里注入 `FakeClock`，可以在 5 毫秒内跑完"跨午夜 8 小时"的场景。硬编码系统时钟的计时代码，本质上不可测。

### 6.4 手机重启 —— 自动截断到开机时刻（决策 D4-1）

**策略（2026-09-08 定）：会话跨越手机重启时，以开机时刻作为结束时间自动停止。**

理由：手机都关机了，人不可能还在学习。关机时段不属于任何人的投入时间，截断在开机瞬间是**事实**，不是估算。

**难点：怎么知道"开机那一刻是几点"？**

关键恒等式：

```
开机时刻（墙上时钟） = 当前墙上时钟 − 当前 elapsedRealtime
bootWallClockMs     = nowWallClockMs − nowElapsedRealtime()
```

`elapsedRealtime()` 的定义就是"自开机起经过的毫秒（含深度睡眠）"，两者相减即得开机的绝对时刻。**不需要任何额外持久化，不需要 `BOOT_COMPLETED`。**

**判定与处置**：

```kotlin
val TOLERANCE_MS = 2_000          // 两次取时钟有微小间隔
val CLOCK_TOLERANCE_MS = 300_000  // 5 分钟

fun resolveActive(session: FocusSession, clock: Clock): ActiveResolution {
    val nowElapsed = clock.elapsedRealtime()
    val nowWall    = clock.wallClockMillis()
    // 1) 会话是否跨越了重启？需要两个条件同时成立
    val bootWall = nowWall - nowElapsed          // ← 本次开机的墙上时刻
    val looksLikeReboot = session.startWallClockMs < bootWall - TOLERANCE_MS
    val monotonicReset  = session.startElapsedMs > nowElapsed   // 单调时钟倒退 = 硬证据

    if (looksLikeReboot && monotonicReset) {
        // 会话开始于本次开机之前 → 截断到开机时刻
        val duration = bootWall - session.startWallClockMs - session.accumulatedPauseMs
        return AutoStoppedAtReboot(endWallClockMs = bootWall, durationMs = duration.coerceAtLeast(0))
    }

    // 2) 正常路径
    val elapsed  = nowElapsed - session.startElapsedMs - currentPause(session, nowElapsed)
    val wallSpan = nowWall - session.startWallClockMs
    if (elapsed < 0 || (wallSpan - elapsed).absoluteValue > CLOCK_TOLERANCE_MS) {
        return ClockTampered                        // 系统时间被改过
    }
    return Normal(elapsed)
}
```

**为什么重启判定需要两个条件**

只用 `startWall < bootWall` 一个条件，会把「用户把系统时间调快」误判成重启 —— 结果是一段错误时长被**静默写进数据库**，这是本项目最不能接受的事故类型。

| 场景 | `startWall < bootWall` | `startElapsed > nowElapsed` | 结果 |
| --- | --- | --- | --- |
| 真重启 | ✓ | ✓ | 截断到开机时刻 |
| 改时间（调快） | ✓ | ✗ | 落到 `ClockTampered` → 人工确认 |
| 正常计时 | ✗ | ✗ | 正常 |

代价：若上次开机时间极短（开机 5 分钟就开始计时），重启后 `startElapsed > nowElapsed` 可能不成立，漏判为重启 —— **但漏判后会被 `ClockTampered` 兜住**，仍然不会产生错误数据。宁可让用户点一下确认，也不能让系统悄悄记错数。

**截断点的误差**：`bootWall` 是**开机**时刻，而会话实际结束于**关机**时刻，中间隔了一次重启耗时（通常几十秒到两三分钟）。这段会被计入时长，且无法避免 —— 我们只能观测到开机，观测不到关机。

| 场景 | `nowElapsed < startElapsedMs` 的表现 |
| --- | --- |
| 手机开机 5 分钟时开始计时，随后重启，10 分钟后才打开 App | `startElapsedMs = 300_000`，`nowElapsed = 600_000` → **判定失效**，时长被算成 300 秒，严重错误 |

`bootWall` 判据与 `startElapsedMs` 的绝对大小无关，**任何时刻都成立**。这一版替换掉上一版。

**执行时机（双保险，幂等）**

| 时机 | 说明 |
| --- | --- |
| `BOOT_COMPLETED` 广播 | 只读 DB + 写结束时间，几毫秒。国产 ROM 常禁用此广播，不能只靠它 |
| App 冷启动 | 兜底。同一会话不重复处理（status 已是 `COMPLETED` 则跳过） |

**边界情况**

| 场景 | 处理 |
| --- | --- |
| 关机期间处于暂停态 | `accumulatedPauseMs` 按 elapsed 累计，关机时段不计入，逻辑自洽 |
| 重启耗时 3 分钟 | 这 3 分钟被排除在时长外 —— 正确，机器是关着的 |
| 会话期间改过系统时间 | `bootWall` 会失真 → 走 `ClockTampered` 分支，标 `needsReview` 交人工（不静默写错数据） |
| 精度 | 误差仅来自 NTP 校时，通常 < 几秒 |

**与 D2-1 的关系**：D2-1 说"忘记点结束 → 标记待确认，人工修正"。重启截断是**另一个分支**——它的结束时刻是机器可确证的（`bootWall` 是算出来的，不是猜的），所以不需要人工确认。只有 `ClockTampered` 才走 D2-1 的人工流程。

**已定：截断后发一条可清除通知**（见 §17.8），告知"已按 03:12 结束 X（1h 42m）"。不标 `needsReview`，因为这不是歧义场景。

**实现位置**：`ResolveRebootedSessionsUseCase` 统一负责"检测 → 写结束时间 → 发通知"，由 `BOOT_COMPLETED` 与 App 冷启动两条路径共用，幂等。

### 6.5 前台服务

| 项 | 选择 |
| --- | --- |
| 服务类型 | API 34+ 用 `specialUse`，并在 `<service>` 内用 `<property>` 声明用途（用户自行分发 APK，无 Play 审核风险） |
| 权限 | `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、`POST_NOTIFICATIONS` |
| 重启策略 | `START_STICKY`，`onStartCommand` 时从 DB 恢复会话；`intent == null` 也能恢复 |
| 停止时机 | 会话进入 `COMPLETED` / `DISCARDED` 立即 `stopForeground` + `stopSelf` |
| 通知更新 | **用 `setUsesChronometer(true)` + `setWhen(startWallClockMs + accumulatedPauseMs)`**，由系统渲染秒数，**App 零更新开销** |
| 暂停时 | 切换为静态文本（"已 2 小时 5 分 · 已暂停"），因为 chronometer 在暂停期间不该继续走 |

> 若不用 chronometer 而选择每秒 `notify()`，一分钟就是 60 次 IPC + 通知重建，纯属浪费电。

### 6.6 通知与小组件的刷新节奏

| 载体 | 刷新方式 | 频率 |
| --- | --- | --- |
| 常驻通知 | 系统 chronometer 自动走秒 | 0（仅状态变更时重建） |
| 桌面小组件 | `TimerForegroundService` 持有期间调 `updateAppWidgetState` | 每 30 秒（仅当有活动会话） |
| 小组件（无计时） | WorkManager 周期任务保底 | 15 分钟（下限） |
| 番茄到点 | `AlarmManager` 精确闹钟 / `setExactAndAllowWhileIdle` | 一次性 |

**小组件不需要走秒。** 用户看小组件的目的是"一眼确认在不在跑、大概多久了"，分钟粒度足够。为它做秒级刷新是拿电量换一个没人看的效果。

### 6.7 异常会话自检

| 触发点 | 动作 |
| --- | --- |
| App 冷启动 | 查活动会话，跑 §6.4 判定，异常置 `needsReview` |
| 前台服务运行中 | 每 5 分钟检查一次（在服务内的协程里，不额外起 WorkManager） |
| 无服务时 | WorkManager `PeriodicWorkRequest`（15 分钟）兜底 |

阈值：`ABNORMAL_THRESHOLD_MS = 8 小时`（Phase 4 已定）。

---

## 7. 后台与国产 ROM 的现实问题

郗哥大概率用国产 ROM（小米 / 华为 / OPPO / vivo）。这些系统对后台的限制**远严于原生 Android**：

| 问题 | 表现 | 处理 |
| --- | --- | --- |
| 电池优化 | 锁屏几分钟后服务被杀、通知消失 | 设置页读取 `PowerManager.isIgnoringBatteryOptimizations()`，未加白名单则引导跳转到 `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` |
| 自启动 / 后台运行权限 | 开机或杀进程后无法恢复 | 设置页提供文字引导（各 ROM 入口不同，不做自动跳转） |
| 应用被"强行停止" | `START_STICKY` 也不再生效 | 无法绕过。用户下次手动打开 App 时恢复；数据不受影响 |

**这一节不是锦上添花。** 不做这层引导，用户会以为"App 有 bug，计时断了"——而实际上数据一直是对的，只是通知没了。要在设置页把这个区别写清楚。

---

## 8. 数据层实现要点

| 要点 | 做法 |
| --- | --- |
| 事务 | 所有多表写操作在 `RepositoryImpl` 内用 `withTransaction { }` |
| 并发 | Room 自带连接层串行化；`TimerController` 用 `Mutex` 保证状态转换原子 |
| 查询返回 | 读操作返回 `Flow<T>`，Room 自动在表变更时重发 |
| 首页聚合 | 三个统计各一个 Flow，`combine` 成一个 `HomeSummary`。**不用一条巨型 SQL**——可读性差且难测 |
| 分页 | **不引入 Paging 3。** 个人一年数据量级几千条，`Flow<List>` + `LazyColumn` 足够，且省掉一整套 PagingSource 抽象 |
| 迁移 | 手写 `Migration(from, to)`，禁用 `fallbackToDestructiveMigration()`（Phase 4 §11） |

### 8.1 Dispatcher 注入

```kotlin
@Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
@Provides @DefaultDispatcher fun default(): CoroutineDispatcher = Dispatchers.Default
```

测试里全部替换为 `UnconfinedTestDispatcher` / `StandardTestDispatcher`。

---

## 9. 错误处理与日志

### 9.1 错误模型

```kotlin
sealed interface AppError {
    data class Validation(val field: String, val reason: String) : AppError
    data class NotFound(val entity: String, val id: Long)       : AppError
    data class Database(val cause: Throwable)                   : AppError
    data class Import(val reason: ImportRejectReason)           : AppError
    data class Unknown(val cause: Throwable)                    : AppError
}
```

- `domain` 层抛/返回 `AppError`，**不让 `SQLException` 之类的实现细节泄漏到 UI**
- `ViewModel` 捕获 → `UiState.error` → UI 显示 Snackbar 或内联错误
- **绝不静默吞异常。** 每个 `catch` 都必须做三件事之一：转成用户可见错误、记日志、或 `throw`（明确不可恢复）

### 9.2 日志

| 项 | 策略 |
| --- | --- |
| 实现 | 自研 `AppLogger` 接口（不引入 Timber，少一个依赖，且能定制落盘） |
| 落盘 | 滚动文件日志，存 `filesDir/logs/`，保留最近 7 天，单文件上限 1 MB |
| 级别 | debug 版 VERBOSE；release 版 WARN+ |
| 崩溃 | `Thread.setDefaultUncaughtExceptionHandler` → 写 `crash-<timestamp>.log` |
| 隐私 | **不接任何崩溃上报 SDK**（那等于给一个"零网络"App 开了网络出口） |
| 导出 | 数据导出时可选择"附带日志"，用于自己排查问题 |

---

## 10. 导出 / 导入的执行方式

**用 WorkManager，不用 ViewModel 里的裸协程。**

| 理由 | 说明 |
| --- | --- |
| 进程被杀 | 导出到一半切后台，协程随进程死亡，用户只看到"卡住了" |
| 用户感知 | `WorkInfo` 可观察进度，UI 显示进度条 |
| 前台保活 | `setExpedited()` + `ForegroundInfo`，长时间导出也不被系统干掉 |

- 导出：`OneTimeWorkRequest` + `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`
- 导入：先自动备份当前库 → 再执行覆盖/合并（Phase 3 已定）
- 文件写入：通过 SAF（`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`），用户自己选位置。**不申请存储权限**，符合隐私最小化

---

## 11. 权限清单（最小化）

| 权限 | 用途 | 必要性 |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | 常驻通知、番茄到点 | 必需 |
| `FOREGROUND_SERVICE` | 常驻通知 | 必需 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | API 34+ 必需的类型权限 | 必需 |
| `SCHEDULE_EXACT_ALARM` | 番茄到点 | 功能依赖 |
| `RECEIVE_BOOT_COMPLETED` | 开机后提醒未结束会话 | 可选但值得 |
| `VIBRATE` | 记一笔成功反馈 | 可选 |

**明确不声明**：`INTERNET`、`ACCESS_NETWORK_STATE`、`READ/WRITE_EXTERNAL_STORAGE`、`READ_PHONE_STATE`、任何定位权限。

> `INTERNET` 不写进 Manifest，是"零上传"最硬的证明——不是"我们承诺不上传"，是"它做不到"。

**备份策略**：`android:allowBackup="false"`。理由：系统自动备份会把数据库传到用户不控制的云端（国内设备通常是厂商云），与"数据只属于我"冲突。换机走手动导出/导入（P0 功能，Phase 3 已定）。

---

## 12. 时区（对 Phase 4 的一处补充风险）

Phase 4 §7.4 写了"时区变更 → 历史数据归属不变（存的是 epoch millis）"。**这句话不准确，这里修正。**

epoch millis 是绝对时刻，但**业务日期是算出来的**：`DayBoundary.businessDateOf(wallClockMs)` 会按**当前**时区换算。用户从北京飞到纽约再打开 App，历史记录的归属日期会整体漂移。

### 12.1 漂移到底长什么样（举例）

假设日切时刻 04:00，郗哥长期在**北京（UTC+8）**。以下是两条真实记录：

| # | 记录（北京时间） | epoch millis | 北京口径业务日期 | 换算成伦敦（UTC+1）本地时间 | 伦敦口径业务日期 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| A | 09-07 23:30 学习 | `T1` | **09-07** | 09-07 16:30 | 09-07 | 不变 |
| B | 10-01 05:00 学习 | `T2` | **10-01** | 09-30 22:00 | **09-30** | 跨了月份 |

**关键：数据库里存的 `T1` / `T2` 一个字节都没变**，变的是"把它们解释成哪一天"的口径。

漂移的具体规律：

- **日切时刻 04:00 是本地时间概念**。时区一变，"当地几点"整体平移，于是原本落在 04:00 之后的记录可能落到 04:00 之前（或反之），归属日就挪动一天。
- 只有**落在边界附近 ±时区差** 的记录会移动。北京→伦敦（差 7 小时），一天 24 小时里有 7 小时的记录可能跨日；北京→洛杉矶（差 15 小时），15/24 的记录可能跨日。
- 挪一天在日视图上只是"这天少了一条、那天多了一条"；但**落在月初/年初的那几条会跨月、跨年** —— 10 月的记录跑到 9 月，年度趋势就错了。

**回到原时区，一切自动恢复。** 所以这不是数据损坏，是"看法变了"。

### 12.2 三种处理方式

| 方案 | 做法 | 成本 | 评价 |
| --- | --- | --- | --- |
| A. 加冗余字段 | 每条记录写入时算好 `businessDate`（yyyy-MM-dd）存一列 | 每表加一列 + 写入时计算；跨时区时"历史归属"与"当前口径"会打架 | 最干净，但违反"原始数据为事实来源、不落冗余" |
| B. 记录时区 | 每条记录存 `timeZoneId` | 同上，且查询口径变复杂（一条 SQL 里混多时区） | 更重 |
| C. **检测并提示** | DataStore 存 `lastKnownZoneId`，启动时比对 | 一行比对 + 一次提示 | **推荐** |

**推荐 C**：个人 App 长期跨时区记录的概率极低，为一列冗余字段付出的复杂度不划算；但完全无视会在发生时让人一头雾水。检测到就说一句，是成本收益最优解。

```kotlin
// 启动时
val zone = ZoneId.systemDefault().id
if (prefs.lastKnownZoneId != null && prefs.lastKnownZoneId != zone) {
    // 弹一次说明性提示："检测到时区变化（Asia/Shanghai → Europe/London），
    // 历史统计将按新时区计算。短期出差建议在设置中暂时保持原时区。"
}
prefs.lastKnownZoneId = zone
```

---

## 13. 导航实现

```
MainActivity (single Activity)
└── AppNavHost
    ├── home      (Tab 1, 独立栈)
    ├── stats     (Tab 2, 独立栈)
    ├── record    (Tab 3, 独立栈) ── 内部子 Tab：timer / expense
    └── projects  (Tab 4, 独立栈)
        ├── project/{id}
        ├── project/edit?id=
        ├── session/edit/{id}
        ├── expense/edit/{id}
        ├── settings
        │   ├── settings/categories
        │   └── settings/data
```

- **type-safe routes**：用 `@Serializable` data class 定义路由，避免字符串拼参数
- **Tab 独立栈**：`navigate(route) { popUpTo(graph.startDestinationId){ saveState = true }; launchSingleTop = true; restoreState = true }`
- **ViewModel 作用域**：屏幕级用 `hiltViewModel()`；跨屏共享的（如活动会话状态）放在 Activity 作用域或 `TimerStateStore` 单例

---

## 14. 测试策略

| 层 | 类型 | 内容 |
| --- | --- | --- |
| `core/time` | **纯 JVM**（快） | `DayBoundary` 的 9 个边界用例（Phase 4 §7.4）、时长计算、重启/改表判定 |
| `domain/usecase` | **纯 JVM** | FakeRepository + FakeClock，覆盖状态机全部转换与非法转换 |
| `data/db` | Instrumentation / Robolectric | in-memory Room 测 DAO；`MigrationTestHelper` 测迁移链 |
| `data/export` | **纯 JVM** | 导出 → 导入 的往返一致性（含 schema 版本升级链） |
| `feature/*` | Compose UI Test | 开始→暂停→结束；记一笔→落库→列表出现 |
| 架构约束 | Konsist / 自定义单测 | 校验 §2.1 的三条依赖方向铁律 |
| 手工 | 真机 | 见 §15 |

**覆盖率目标不追求数字**，但以下三处必须 100%：`DayBoundary`、时长计算、会话状态机。这三处错了就是静默数据错误。

---

## 15. 真机测试清单（Phase 10 直接执行）

| # | 场景 | 期望 |
| --- | --- | --- |
| 1 | 开始计时 → 按 Home 键 → 5 分钟后回来 | 时长正确，通知仍在 |
| 2 | 开始计时 → 锁屏 → 30 分钟 → 解锁 | 时长正确（含锁屏期间） |
| 3 | 开始计时 → 多任务划掉 App | 通知消失，重新打开后会话仍在且时长正确 |
| 4 | 开始计时 → 手机重启 | 重开 App 后提示"待确认"，建议值 ≈ 墙上时钟跨度 |
| 5 | 开始计时 → 手动把系统时间调快 2 小时 → 回来 | 判定为 `ClockTampered`，标记待确认，**不产生错误时长** |
| 6 | 23:30 开始 → 01:00 结束 | 整段归属前一天，时间轴不拆分 |
| 7 | 凌晨 3:30 记一笔 | 归属前一天 |
| 8 | 连续快速点两次"开始计时" | 只产生一个活动会话（部分唯一索引兜底） |
| 9 | 计时中修改日切时刻 | 统计口径随之变化，不崩溃 |
| 10 | 归档项目后查看历史统计 | 历史数据仍在（不加 `isArchived` 过滤） |
| 11 | 导出 → 卸载重装 → 导入 | 数据完全一致 |
| 12 | 飞行模式下全功能使用 | 一切正常（零网络依赖验证） |

---

## 16. 本阶段的新发现与修正

| # | 内容 | 类型 |
| --- | --- | --- |
| 1 | **`elapsedRealtime` 在手机重启后归零**，会导致时长算成负数 | Phase 4 未覆盖。§6.4 给出 `bootWall = nowWall − nowElapsed` 判据，零新增字段。策略：跨越重启即截断到开机时刻（D4-1）。**我第一版用的 `nowElapsed < startElapsedMs` 判据有失效场景，已作废** |
| 2 | **Phase 4 §7.4"时区变更归属不变"不准确** | 修正：归属日期按当前时区计算，会漂移。§12 给出检测 + 提示方案 |
| 3 | **前台服务不承载计时正确性** | 明确了服务的职责边界，把"服务被杀"从灾难降级为体验问题 |
| 4 | **通知用系统 chronometer，不每秒 notify** | 省电，且免去一套定时更新逻辑 |
| 5 | **国产 ROM 电池优化引导** | 不做这层，用户会误判为"计时坏了" |
| 6 | **16 KB 页大小对 Room 生效**（捆绑原生 SQLite） | 不是"纯 Kotlin 就没事" |

---

## 17. 关键选型权衡（2026-09-08 已定）

| # | 项 | 决策 | 依据 |
| --- | --- | --- | --- |
| 17.1 | 模块结构 | **单模块 + Konsist 架构测试** | 省构建成本，用测试补上"编译期强制" |
| 17.2 | Entity ⇄ Domain | **做映射** | UI 永不认识 Room |
| 17.3 | UseCase 覆盖面 | **只覆盖有业务规则的写操作** | 拒绝仪式性抽象 |
| 17.4 | 分页 | **不引 Paging 3** | 数据量级远未到阈值，触发条件：单列表 > 1 万条 |
| 17.5 | 通知刷新 | **系统 chronometer** | 零刷新开销；倒计时需求出现时再改 |
| 17.6 | 备份 | **`allowBackup=false`** | 系统自动备份会传厂商云，与"零网络"定位冲突 |
| 17.7 | 日志 | **自研 `AppLogger`** | 与 Timber 工作量相当（落盘 Tree 都要自己写）；此项不坚持 |
| 17.8 | 重启截断告知 | **发一条可清除通知** | 不静默改数据，也不占用"待确认"通道 |

以下是各选项两个方向的优劣与**改主意的条件**，保留供将来复查。

### 17.1 模块结构：单模块 vs 多 Gradle 模块

| | 单模块（我的选择） | 多模块（主流"专业"做法） |
| --- | --- | --- |
| 编译速度 | 改动任意文件全量编译。12 页 + 4 表规模下，增量编译几秒，感知不到 | 改 `:feature:home` 只重编该模块，大项目能省几十秒 |
| 维护成本 | 零。一个 `build.gradle.kts` | 每模块一份构建配置、一份依赖声明，还要管模块间 API 边界 |
| 依赖纪律 | **靠自觉，容易腐化**。这是它最大的弱点 | 编译期强制。想从 UI 引用 DAO 直接编译不过 |
| 未来拆分 | 包结构干净的话是机械操作 | 已是最终形态 |

**改主意的条件**：如果这个项目最终超过 40 个页面，或者你发现自己需要"改一个页面却要等 30 秒编译"，再拆不迟。
**折中方案**：现在就加 Konsist 架构测试，用测试补上"编译期强制"的能力 —— **建议采纳这条**，成本一天不到。

### 17.2 Entity ⇄ Domain 映射：做 vs 不做

| | 做映射（我的选择） | 直接用 Room Entity（更常见） |
| --- | --- | --- |
| UI 与持久化解耦 | 是。换存储方案、拆表、改列名都不动 UI | 否。Room 注解会渗透到 Composable 里 |
| 类型自由度 | domain model 可用 `value class`、`kotlin.time.Duration`、不可变集合 | 受 Room 支持的类型限制 |
| 代码量 | 4 个 model + 4 组映射函数，约 150 行机械代码 | 0 |
| 心智负担 | 多一层，调试时要在两层间跳 | 一层，直观 |
| 出错风险 | 映射写错字段会静默出错（可用单测覆盖） | 无此风险 |

**改主意的条件**：如果这 150 行映射在半年后仍然一行没改过，说明它确实没起作用，可以合并掉。但在项目还处于"表结构会演进"的阶段，它是保险。

### 17.3 UseCase 覆盖面：只覆盖写操作 vs 全量

| | 只给"有业务规则"的写操作建（我的选择） | 所有查询也包一层 UseCase |
| --- | --- | --- |
| 文件跳转 | 改一个查询只需动 Repository | 动三个文件（DAO → Repo → UseCase → VM） |
| 一致性 | 写操作有统一入口，读操作直接 | 形式上整齐 |
| 实际收益 | 业务规则集中在可单测的纯 Kotlin 类里 | 多出来的一层没有逻辑，纯仪式 |

**改主意的条件**：几乎不会。如果将来某个查询长出了业务规则（比如"统计时需要排除某类项目"），就地把它提升为 UseCase 即可。

### 17.4 分页：不引 Paging 3 vs 引入

| | 不引（我的选择） | 引入 Paging 3 |
| --- | --- | --- |
| 代码量 | `Flow<List<T>>` + `LazyColumn`，几十行 | `PagingSource` + `Pager` + `LazyPagingItems` + 加载/错误状态，几百行 |
| 性能 | 一年几千条一次性载入内存，每条约 100 字节 → 几百 KB，无压力 | 按需加载，内存恒定 |
| 风险 | 数据量涨到十万级会卡 | 无此风险 |
| 复杂度 | 无 | 多一套抽象 + 一套测试 |

**改主意的条件**：当单个列表可能超过 **1 万条**（按每天 10 条算 = 3 年）时再引。届时加 Paging 3 是局部改造，不会推翻架构。

### 17.5 通知刷新：系统 chronometer vs 每秒 notify

| | `setUsesChronometer(true)`（我的选择） | 每秒 `NotificationManager.notify()` |
| --- | --- | --- |
| 刷新开销 | **零**。由 SystemUI 渲染秒数 | 每分钟 60 次 IPC + 通知重建 + 布局 |
| 耗电 | 可忽略 | 持续唤醒，一天下来可观 |
| 可控性 | 只能正向计时，样式由系统决定 | 完全自定义 |
| 暂停时 | 需要切换成静态文本（chronometer 不该继续走） | 天然支持 |

**改主意的条件**：如果将来要做"倒计时"样式（`setChronometerCountDown` 支持）或通知上要显示自定义富文本进度，再换成手动刷新。

### 17.6 备份：`allowBackup=false` vs `true`

| | `false`（我的选择） | `true`（系统自动备份） |
| --- | --- | --- |
| 换机体验 | 需手动导出 → 传文件 → 导入（5 分钟，且是 P0 功能） | 装 App 自动恢复，无感 |
| 数据去向 | 只在用户自己选的位置 | 上传到设备厂商云（国内多为厂商云，Google 设备为 Google Drive） |
| 与产品定位 | 一致："数据只属于我" | **冲突**：等于给一个零网络权限的 App 开了一条云端通道 |
| 可靠性 | 依赖用户记得导出 | 依赖厂商备份策略，且备份文件不完全可控 |

**改主意的条件**：如果发现"手动导出"在 30 天自测里一次都没做过（意味着换机时会丢数据），就该重新评估。届时可选折中：`allowBackup=true` 但用 `dataExtractionRules` 显式排除数据库文件。

> **已定：`allowBackup=false`。** 导出/导入是 P0 功能（Phase 3 已定），换机路径完整。

### 17.7 日志：自研 `AppLogger` vs Timber

| | 自研（我的选择） | Timber |
| --- | --- | --- |
| 依赖 | 0 | 1 个（很轻，2 万方法以内） |
| 落盘能力 | 完全可控（滚动文件、7 天保留、1 MB 上限） | 需自己写 `Tree`，一样的工作量 |
| API 熟悉度 | 自己定义 | 行业标准，第三方一看就懂 |

**这条其实无关紧要。** Timber 只是"打日志的语法糖"，真正的工作量在 `Tree` 落盘实现上，两边一样。用 Timber 也没问题，我不坚持。

---

### 17.8 重启截断怎么告知用户

| 选项 | 行为 | 优劣 |
| --- | --- | --- |
| A. 静默截断 | 直接写 `COMPLETED`，什么都不说 | 用户会疑惑"我明明没点结束"。会话本来可在编辑页改，但用户不知道要改 |
| B. **截断 + 一条可清除通知**（**已定**） | "检测到手机重启，已按 03:12 结束高等数学（1h 42m）" | 一次 `notify()`，零阻塞。用户知道发生了什么，想改就去改 |
| C. 截断 + 标 `needsReview` 卡片 | 首页顶部出现待确认卡片 | 与 D2-1 的"异常会话"语义混在一起，而重启截断**不是异常**，是机器可确证的事实 |

**已定 B**：重启时刻由 `bootWall` 算出，是确证值而非估算值，不该占用为真正歧义场景保留的"待确认"通道；但静默改数据违背"不静默篡改"原则，发条通知是最低成本的平衡。

**实现位置**：`BootRecoveryWorker`（`BOOT_COMPLETED` 触发）与 App 冷启动的自检逻辑共用同一个 `ResolveRebootedSessionsUseCase`，该 UseCase 负责写结束时间 + 发通知，幂等。

---

## 18. 阶段总结

### 已确定
- 单模块 + 严格分包，三层单向依赖（`feature → domain ← data`）；四条依赖规则由 **Konsist 测试**强制
- 完整技术选型与 SDK 配置（Kotlin 2.4 / AGP 9 / Compose / Room 3 / Hilt / Navigation type-safe）
- Entity ⇄ Domain 映射；UseCase 只覆盖"有规则"的写操作
- 计时子系统：`TimerController` 唯一写口、`Clock` 可注入、DB 为唯一真相
- **D4-1 重启即截断**：`bootWall = nowWall − nowElapsed` 判据，`BOOT_COMPLETED` + 冷启动双保险，截断后发可清除通知，不标 `needsReview`
- 时区：检测 `lastKnownZoneId` 变化并提示一次，不加冗余字段
- 8 项选型权衡已定（§17）
- 前台服务类型、通知 chronometer 方案、小组件刷新节奏
- 导出走 WorkManager + SAF；权限最小化；`allowBackup=false`
- 错误处理模型、本地日志与崩溃落盘
- 测试分层与 12 项真机清单

### 未确定
- 精确依赖版本 → **Phase 7 开工当天锁定**（2026 年工具链迭代快，现在写死反而会过期）
- 图标库来源 → Phase 6
- Room 3 是否引入破坏性变更 → 开工时验证，必要时退锁 2.7.x

### 决策依据
- PRD：Local-first、零网络、稳定性 > 一切
- Phase 3：4 Tab 独立栈、3 个通知通道、2 个小组件
- Phase 4：双时钟、DayBoundary 铁律、不建汇总表
- 2026 平台现状：前台服务类型强制、Play target API 36、16 KB 页大小

### 下一阶段
**Phase 6 — UI/UX**：视觉语言（配色 / 字体 / 圆角 / 间距）、组件库、明暗主题、12 个页面的视觉稿与交互细节。
