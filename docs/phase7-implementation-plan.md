# DailySchedule · Phase 7 MVP 实现计划

| 项 | 内容 |
| --- | --- |
| 版本 | v1.0 |
| 日期 | 2026-09-08 |
| 项目根目录 | `D:\DailySchedule` |
| 上游 | Phase 0–6 全部文档 |
| 状态 | 待确认环境路线 |

---

## 1. 环境现状（2026-09-08 实测）

| 组件 | 状态 |
| --- | --- |
| JDK | **未安装**（无 `java`、无 `JAVA_HOME`） |
| Android SDK | **未安装**（无 `ANDROID_HOME`，常见路径均无） |
| Gradle | **未安装** |
| Android Studio | **未安装**（Program Files 与用户程序目录均无） |

**结论：当前无法在本机编译 Android 项目。** 这不阻碍产出代码，但阻碍"写完立刻验证能编译"。

### 1.1 两条路线

| | A. 你装 Android Studio | B. 我装命令行工具链 |
| --- | --- | --- |
| 做法 | 装 Android Studio（自带 JDK 21 + SDK Manager），我用它打开工程 | 我下载 JDK + cmdline-tools + platform/build-tools + Gradle |
| 我能构建吗 | 不能直接（我这边仍无工具链） | **可以**，我能自己编译并出 APK |
| 你需要做的 | 安装 + 首次配置（约 30 分钟，含 SDK 下载） | 无需操作，等我装完 |
| 额外收益 | 真机调试、Profiler、Layout Inspector、Logcat —— **你最终一定需要** | 无 |
| 风险 | 无 | 国内网络下 SDK / Maven 下载可能很慢或失败；失败则白等 |

**我的建议：A + B 都要，但先 A。**

理由：B 只是让我能自证编译，而 A 给你的是调试与打包的完整能力，且你迟早要装。顺序上先装 A（你在界面上点几下的事），SDK 就位后我这边即便不装 B，也可以把工程交给你在 AS 里一键构建。

若 A 装好后你希望我也具备编译能力，再走 B 不迟——那时 SDK 已经在磁盘上，我只需装 JDK 和 Gradle 分发，下载量小一个数量级。

---

## 2. 工具链版本锁定

**开工当天锁定，写进 `gradle/libs.versions.toml`**，之后非必要不动。

| 类别 | 目标版本 | 锁定前必须验证 |
| --- | --- | --- |
| JDK | 21（AGP 9 要求） | `java -version` |
| Gradle | 9.x（与 AGP 9 匹配） | wrapper 能下载分发 |
| AGP | 9.x | 与 Gradle 版本对应表 |
| Kotlin | 2.4.x | 与 Gradle 9 兼容；Compose 编译器插件版本 = Kotlin 版本 |
| Compose BOM | 当期稳定版 | 与 compileSdk 绑定关系（BOM 2026.08.00 → compileSdk 37 → AGP 9.1.1+） |
| Room | 3.0.x | **①16 KB 页大小已适配 ②API 无破坏性变更**（否则退锁 2.7.x） |
| Hilt | 1.4.x + KSP | |
| Navigation | 2.9.x（type-safe routes） | |
| DataStore / WorkManager / Glance | 当期稳定版 | Glance 与 Compose BOM 版本关系 |

**SDK**：`minSdk 26` / `compileSdk` 跟随 Compose BOM 要求 / `targetSdk 36+`。

### 2.1 锁定后立刻做的三项验证

| # | 验证 | 方法 | 不通过的后果 |
| --- | --- | --- | --- |
| 1 | **16 KB 页大小** | 构建 APK 后解压，检查 `lib/*/libsqlite3x.so` 段对齐 | 16 KB 设备上启动即崩 |
| 2 | **`material-icons-extended` 体积** | 对比引入前后 release APK 大小 | 若增幅过大改用图标子集 |
| 3 | **Room 3 API** | 先写一个 Entity + Dao 跑通编译 | 有破坏性变更则退锁 2.7.x |

### 2.2 版本校准清单（Gradle 同步失败时先看这里）

`libs.versions.toml` 里的版本号是按 2026-09 的公开信息填的。**以下几项我没有办法在本机验证**，若同步报错，按 Android Studio 的提示改即可，不要硬扛：

| 项 | 当前填写 | 若不匹配的症状 | 处理 |
| --- | --- | --- | --- |
| `ksp` | `2.4.10-1.0.3` | "KSP version is not compatible with Kotlin" | 改成 AS/报错里给出的 KSP 版本（必须前段与 Kotlin 版本一致） |
| `gradleWrapper` | `9.5.0` | "Minimum supported Gradle version is X" | 按提示升/降；AGP 与 Gradle 有对应表 |
| `agp` | `9.3.0` | 与 Gradle 版本不匹配 | 用 AS 的 AGP Upgrade Assistant |
| `composeBom` | `2026.08.00` | 要求更高 compileSdk | 同步升 compileSdk 或降到 `2026.06.00` |
| `room` | `3.0.1` | API 破坏性变更 | 退锁 `2.7.x` |
| `navigationCompose` / `datastore` / `work` / `glance` / `hiltNavigationCompose` | 见 toml | 找不到版本 | 在 AS 的 Dependency 提示里选最新稳定版 |
| `material-icons-extended` | 跟随 BOM | — | 构建后确认 APK 体积增幅 |

**判断标准只有一个**：能编译、能装到手机、4 个 Tab 能切换。版本号本身不重要，能跑起来的才重要。

---

## 3. 任务分解（WBS）

编号即实现顺序。每批**独立可编译**，不允许留半成品。

| # | 任务 | 依赖 | 主要产出 | 验收 |
| --- | --- | --- | --- | --- |
| T1 | 工程骨架 | — | Gradle 配置、`AndroidManifest`、`Application`、Nav 骨架（4 个空 Tab） | 能装到设备，4 Tab 可切换 |
| T2 | `core:time` | T1 | `Clock`、`DayBoundary`、`DurationCalculator`、`DurationFormatter` | **9 个日期边界用例 + 时长用例全绿**（纯 JVM） |
| T3 | `core:result` + `core:log` | T1 | `AppError`、`AppResult`、`AppLogger`、`CrashHandler` | 崩溃能落盘 |
| T4 | `data:db` | T1 | 4 个 Entity、Converters、4 个 Dao、`AppDatabase`、部分唯一索引、预置分类种子 | DAO 测试通过；活动会话唯一性验证 |
| T5 | `data` 其余 | T4 | `UserPreferencesDataSource`、Entity⇄Domain mapper、4 个 `RepositoryImpl` | 偏好读写往返测试 |
| T6 | `domain` | T5 | 4 个 model、repository 接口、10 个 UseCase | **状态机与 UseCase 单测全绿** |
| T7 | `di` | T6 | Hilt Modules（Database / Repository / Dispatcher / Clock） | 注入图无环，编译通过 |
| T8 | 计时子系统 | T6, T7 | `TimerController`、`TimerStateStore`、`TimerForegroundService`、通知、`BootReceiver`、`ResolveRebootedSessionsUseCase` | **真机清单 1–5 项通过**（后台/锁屏/杀进程/重启/改时间） |
| T9 | `core:ui` | T1 | `ThemePack`、`GraphiteTealTheme`、`DsTheme`、21 个基础组件 | 浅/深主题切换正常；硬编码颜色扫描测试通过 |
| T10 | `feature:home` | T8, T9 | 今日页（计时卡、三数字、目标、时间轴） | 首页数字含实时会话 |
| T11 | `feature:record` | T10 | 计时页 + 记账页（含 `DsNumberPad`） | 记一笔 3 次点击；开始计时 2 次点击 |
| T12 | `feature:projects` | T10 | 项目列表 / 详情 / 编辑 | 归档后历史统计仍在 |
| T13 | `feature:stats` | T10 | 统计页（日/周/月/年 + 横向条） | 跨月跨年数据正确 |
| T14 | `feature:settings` | T12 | 设置、分类管理、导出/导入、主题切换 | 导出→卸载重装→导入数据一致 |
| T15 | 小组件 | T10 | Glance 2×1 与 4×2 | 计时中小组件能显示并操作 |
| T16 | 测试与真机 | 全部 | 补齐单测、跑 12 项真机清单 | 全部通过 |

**进度（2026-09-08 21:20）**：T1–T14 已完成。剩 T15 小组件、T16 真机与单测补齐，
以及两块被推迟的功能：导出/导入（原属 T14）、会话与消费的编辑页（路由已定义，无页面）。

**首次编译尚未验证** —— 沙箱装不了 Android SDK packages（见 README 的「跑起来」），
所有代码是按编译期约束写的，但没有真正跑过 `assembleDebug`。

### 3.2 功能测试从哪一步开始

常见疑问是"什么阶段可以功能测试"，答案是**分三层，最早的一层现在就能跑**：

| 层 | 起点 | 跑什么 | 需要什么环境 |
| --- | --- | --- | --- |
| **单元测试**（已可跑） | T2 起 | `DayBoundaryTest` / `DurationCalculatorTest` / `AppResultTest` / `DatabaseConstraintsTest` | Android Studio，不需要手机 |
| **界面冒烟** | T9 主题 + 任一 feature 页 | 装到手机看深浅色、字号、组件是否错位 | 手机或模拟器 |
| **端到端功能测试** | **T10 完成**（首页 + 计时打通） | 记一笔、开始/暂停/结束计时、时间轴、三个数字 | 真机 |

**关键节点是 T10，不是 T16。** 原因：

1. T8（计时子系统）单独测不了 —— 它没有 UI，你能验证的只有"通知在不在"，而看不到时长对不对。
2. T10 把计时接到首页之后，"点开始 → 锁屏十分钟 → 回来 → 数字对不对"这件事才第一次可观测。
3. 之后每完成一个 feature（T11 记账 / T12 项目 / T13 统计 / T14 设置）就多一块可测区域，**不必等全部做完**。

**但有一类测试必须留到最后**：12 项真机清单里的后台行为（杀进程、重启、改系统时间、跨日切），只有整个 App 都在时才测得出交互影响。这些在 T16 集中跑。

**所以建议节奏**：T10 一过就装真机，边用边往后做 T11–T15。你自己就是第一个真实用户，30 天留存这条成功标准本来就要从那天开始计。

### 3.3 T3 / T4 的两处偏离（需知悉）

| # | 计划写法 | 实际做法 | 原因 |
| --- | --- | --- | --- |
| 1 | `amountCents > 0` 用 **DB CHECK 约束** | 改为 UseCase 层校验（T6） | Room 的 `@Entity` 无法表达 CHECK，只能靠 `CREATE TRIGGER ... RAISE(ABORT)` 模拟。为一条已有 UI 校验的规则引入触发器不划算。**风险可控**：写入路径只有 UseCase 一处 |
| 2 | 部分唯一索引写进 `@Entity` | 改为 `DatabaseCallback.onCreate` 执行原生 SQL | Room 不支持部分索引（带 WHERE） |

**第 2 条有一个必须在真机验证的点**：这个索引不在 Room 的 schema 元数据里。首次创建走 `onCreate` 没问题；第二次打开已有数据库时 Room 走 `onValidateSchema`（宽松校验，只比对表与列），预期不报错 —— **但必须验证**：安装后重启 App 两次，确认没有抛 invalid schema。若报错，改为把唯一性放进 `Migration(1,2)` 并把 version 升到 2。

### 3.1 关键路径

```
T1 → T2/T4 并行 → T5 → T6 → T7 → T8 ┐
T1 → T9 ─────────────────────────────┼→ T10 → T11~T15 → T16
```

T2（纯 Kotlin，可 JVM 单测）和 T4（Room）可以并行，但都由我顺序产出。**T8 是风险最高的一批**，Android 后台机制问题只会在这里暴露，一定要在真机上验，不能只看编译通过。

---

## 4. 编码规范

| 项 | 规则 |
| --- | --- |
| 格式 | Kotlin 官方代码风格，**用 ktlint 强制**（`ktlintCheck` 进构建） |
| 命名 | 见下表 |
| 字符串 | 全部放 `res/values/strings.xml`，代码里不出现中文硬编码 |
| 颜色 | **禁止硬编码**（Phase 5 §2.2），只走 `DsTheme.colors.*` |
| 数字 | 所有金额与时长必经 `DsNumberText`（tnum） |
| 时间 | **禁止直接调 `SystemClock` / `currentTimeMillis()`**，一律注入 `Clock` |
| 日期 | **禁止 `LocalDate.now()` 直接用于统计**，一律走 `DayBoundary` |
| 异常 | 每个 `catch` 必须做三件事之一：转 `AppError` 给用户、记日志、或 `throw` |
| 提交 | 每个批次一次提交，信息写清"做了什么 + 为什么" |
| TODO | **禁止跨批次留 TODO**。本批做不完就拆小 |

### 4.1 命名

| 对象 | 规则 | 例 |
| --- | --- | --- |
| Entity | `*Entity` | `FocusSessionEntity` |
| Domain model | 无后缀 | `FocusSession` |
| DAO | `*Dao` | `SessionDao` |
| Repository | 接口 `XxxRepository` / 实现 `XxxRepositoryImpl` | |
| UseCase | `*UseCase`，单个 `operator fun invoke` | `StartSessionUseCase` |
| ViewModel | `*ViewModel` | `HomeViewModel` |
| UiState | `*UiState`（密封或 data class） | `HomeUiState` |
| Composable | 页面 `*Screen`，组件 `Ds*` | `HomeScreen`、`DsCard` |

---

## 5. 验收标准

### 5.1 每批次

1. `./gradlew assembleDebug` 通过
2. `./gradlew ktlintCheck` 通过
3. 本批次相关单测通过
4. 无新增编译警告

### 5.2 MVP 总验收（对齐 PRD 成功标准）

| # | 标准 |
| --- | --- |
| 1 | 30 天自测留存（主观，Phase 13 评） |
| 2 | 记一笔 ≤ 5 秒 |
| 3 | 零数据丢失（真机清单 1–5、11 项） |
| 4 | 1 小时计时误差 < 1 秒 |
| 5 | 零崩溃 |
| 6 | 晚上愿意打开它回看今天（主观） |

### 5.3 单测覆盖率

**不追求整体数字**，但以下三处必须 100%：

- `DayBoundary`（所有日期换算）
- 时长计算（含重启/改时间判定）
- 会话状态机

这三处错了就是**静默数据错误**——用户不会报错，只会发现数字不对，而那时已经攒了几周脏数据。

---

## 6. 阶段总结

### 已确定
- 环境现状与两条路线（建议先装 Android Studio）
- 工具链版本锁定流程与三项锁定后验证
- 16 项任务分解、依赖与验收标准
- 编码规范（7 条禁令 + 命名约定）
- 覆盖率要求（三处 100%）

### 未确定
- **环境路线**（等你决定）
- 精确依赖版本（开工当天锁）

### 决策依据
- Phase 5：单模块包结构、Konsist 约束、ThemePack 抽象
- Phase 6：主题三态、色彩与组件规范
- Phase 4：DayBoundary 铁律、数据完整性约束

### 下一阶段
按 WBS 从 T1 开始实现。
