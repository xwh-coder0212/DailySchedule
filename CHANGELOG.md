# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 的格式。
尚未发布任何正式版本，`versionName` 目前为 `1.0.0`（`versionCode = 1`）。

## [未发布]

### 真机验收（2026-10-02）

在 Redmi（`rothko` / 2407FRK8EC，Android 16，HyperOS V816）上完成首轮真机验收，
覆盖 12 项。完整报告见 `docs/quality/device-acceptance-2026-10-02.md`。

**Verified**

- 安装（MIUI 场景，`--no-streaming`）、冷启动约 1s、无崩溃无 ANR
- 连续两次冷启动无 schema / 迁移报错，数据库正常落盘
- 导出 xlsx 后用**仅 Python 标准库**的独立读取器解析成功：两张工作表、表头顺序正确、
  合计公式（`SUM` / `SUMIF`）正确、`numFmt` 一位小数生效、冻结首行
- 统计页三图（环形 / 柱状 / 折线）在真机渲染正常；占比合计恰为 100%
- 记账行左滑露出「编辑 / 删除」
- 设置页（主题三态 / 动态取色 / 日切时刻）、待办详情面板、周热力图、专注历史

**Known issues**

- `SettingsScreen` / `CategoryManageScreen` 没有标题栏与返回按钮，只能靠系统返回手势退出。
  同为设置入口的 `DataTransferScreen` 有标题栏，因此这是遗漏而非统一设计。
- 补录角标未用真实数据验证（设备库内 4 条会话全部来自计时，无补录记录）。

### 质量门禁补课（2026-10-02）

把两条"以为在跑、其实没跑"的检查变成了真在跑。

**Fixed**

- `local.properties` 中 `sdk.dir` 的 Windows 盘符冒号未转义，`lintDebug` 直接报
  `PropertyEscape` 错误。改为 `D\:/…` 形式，AGP 解析结果不变。
- 统计页图例用 `LocalContext.current.getString` 取百分比文案。该调用不感知
  Configuration 变化，语言 / 字号变更后可能返回变更前的旧资源，被 lint 规则
  `LocalContextGetResourceValueCall` 判为 error。改为在 `mapIndexed`（inline 函数，
  lambda 会内联进 Composable）内直接使用 `stringResource`。

**Added**

- `ktlintSources` 任务：直接调用 ktlint CLI 扫描 `src/main` 与 `src/test`。
  此前 ktlint-gradle 12.3.0 与 AGP 9 不兼容，只生成 `.kts` 的检查任务，
  几百个 `.kt` 文件一个都没扫（空过）。
- `gradle/wrapper`：补上 `gradle-wrapper.jar` 与 `gradlew` / `gradlew.bat`，
  仓库现在可以脱离本机构建环境独立构建。

**Known issues**

- ktlint 存量风格违规 1469 处，分布在 124 个文件，未清理。
  清单见 `docs/quality/quality-gate.log`。
- Android Lint 54 条 warning（26 条为未使用的字符串资源）。

### Rev2 改版（2026-10-01）

依据《改进方案》第二轮重做信息架构与统计页。

**Changed**

- 底部导航改为三项：**待办 / 统计 / 记账**。计时并入待办页顶部，
  不再有独立的「今日」「记录」页；删除旧的 `feature/record/` 整包。
- 待办详情从独立路由改为 `ModalBottomSheet` 弹出。
- 统计页重做：专注 / 消费分段切换，环形（占比）、柱状（周分布）、
  折线（日 / 月趋势）三图全部手写 Canvas，零图表依赖。

**Added**

- `core/stats/StatsAggregator.kt`：统计聚合纯函数层，"空格子补 0"与
  "百分比合计恒为 100"（最大余数法）在这里保证，并有 16 个单测。
- `core/ui/component/DsCharts.kt`：环形 / 柱状 / 折线 / 图例四个 Canvas 组件。
- 补录标记：手动补录的会话标 `SessionSource`，列表页显示「补录」角标，
  导出表增加「来源」列。数据库 schema v1 → v2。

### 功能实现（2026-09-09 ~ 09-10）

按实施计划 WBS 推进 T1–T14。

**Added**

- T1 工程骨架：Gradle 版本目录、Manifest、主题。
- T2 时间核心：`Clock` / `DayBoundary` / `DurationCalculator` / `DurationFormatter`。
- T3 错误与日志：`AppError` / `AppResult` / `AppLogger` / `CrashHandler`。
- T4 数据层：Room 4 张表（分类、消费、项目、专注会话）+ 索引 + 约束测试。
- T5 仓储：5 个 `RepositoryImpl` + Mapper + Hilt `DataModule`。
- T6 领域层：14 个 UseCase（bootstrap / timer / expense / project / session）。
- T8 计时：`TimerController` + 前台服务 + 通知 + 开机接收器。
- T9 主题：`ThemePackSpec` + `GraphiteTealSpec` + `ProjectColors` + 硬编码颜色扫描单测。
- T10–T14：首页、记账、项目、统计、设置。
- 导出：手写 `XlsxWriter` 生成 .xlsx，经 SAF 写入用户选定位置。

### 立项与设计（2026-09-07 ~ 09-08）

**Added**

- Phase 0–7 文档：竞品研究 → 需求访谈 → PRD → 信息架构 → 数据模型 →
  技术架构 → UI/UX 规格 → 实施计划。
- 确定排除项：不做 Todo 清单、预算、云同步、多账户、银行同步、社交、锁机、
  游戏化、复式记账。
