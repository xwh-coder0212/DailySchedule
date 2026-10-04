# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 的格式。
尚未发布任何正式版本，`versionName` 目前为 `1.0.0`（`versionCode = 1`）。

## [未发布]

### release 构建打通、补 CI、清死依赖（2026-10-04）

在此之前这个工程**一个 release 包都没产出过**：`app/build/outputs/apk/` 下只有 debug，
`docs/quality/quality-gate-final.txt` 全文不含 `release`，签名配置在三个构建文件里零命中。
`isMinifyEnabled` / `isShrinkResources` 一直开着，但 R8 从没执行过一次。

**Added**

- **release 签名配置**（`app/build.gradle.kts`）。只在仓库根的 `key.properties` 存在
  且四个键齐全时生效，否则退回 unsigned **并在构建期打一条警告** ——
  「装不上的包 + BUILD SUCCESSFUL」是最容易被忽略的组合。keystore 按设计不入库。
- **CI**（`.github/workflows/ci.yml`）。两个 job：`quality`（单测 + ktlint 门禁 + Lint，
  每次推送都跑）与 `release`（真的走一次 R8 与资源压缩，核对 dex 与体积）。
  此前 `docs/quality/README.md` 里那句「项目没有 CI，这些日志就是门禁确实跑过的唯一凭据」
  说的就是这件事 —— 门禁成立与否，取决于「这次记不记得手动跑」。
- 7 个单测，总计 **213 个 / 24 个测试类**。
  - `StatsNumbersEndToEndTest`：统计页的**数值**断言。第一轮验收只做了「三张图渲染正常、
    占比合计 100%」这种目视核对，能发现「图画不出来」，发现不了「柱子画对了但数字是错的」。
    走真实 Room + 真实 `DayBoundary` + 真实 `StatsViewModel`，期望值全是按业务规则手算的常量。
  - `ManualBadgeMixTest`：「补录」角标在**混排**列表里的归属。两个用例的时间顺序与来源模式
    刻意相反 —— 只测一种的话，「角标跟着记录走」和「角标按行号 0/2/4 走」会得出同样结果，
    测试无法区分这两种实现。

**Changed**

- 删掉 `androidx.work:work-runtime-ktx`（`app/build.gradle.kts` 与 `gradle/libs.versions.toml`）。
  全工程零调用，是排查死依赖时发现的。保时提醒与开机恢复走 `AlarmManager` + `BroadcastReceiver`。
  **一处需要留意**：这行声明原本还在充当版本下限。删之前 runtime classpath 上是它声明的
  2.11.0，删之后只剩 `glance-appwidget:1.2.0 → glance:1.2.0` 传递带入的
  `work-runtime(-ktx):2.7.1`，即降了 4 个 minor。当前无影响（两者都没被引用，R8 全裁掉），
  但 V2 实现桌面小组件时不能沿用 2.7.1 —— 那是 2022 年的版本，早于 Android 14 的
  前台服务类型要求。
- `README.md` 补上 release 包与签名的构建方式、`--rerun-tasks` 的坑、依赖表的更正。

**Verified**

- `:app:assembleRelease` 首次成功。包体 **26.93 MB → 7.03 MB**（dex 从 21 个降到 1 个）。
- `apksigner verify --print-certs`：v2 方案通过，签名者
  `CN=DailySchedule, OU=Personal, O=xwh-coder0212, C=CN`，
  证书 SHA-256 `1ac90d20…398fd645`。v1 为 false 属预期（`minSdk = 26`）。
  release 包 7,379,541 字节，SHA-256 `7812a544…0fe84017a6`。
- **R8 没有伤到 kotlinx.serialization**。排查过程中有两次「看起来是缺陷」：
  release dex 里 grep 不到 `$$serializer`（实际是被改名，`mapping.txt` 可证），
  以及枚举常量字段被改名（`RUNNING -> f`，但 `dexdump` 显示传给 `Enum.<init>` 的名字
  字符串仍是 `"RUNNING"`）。真正兜底的是 `kotlinx-serialization-core-jvm:1.9.0` 自带的
  consumer R8 规则，AGP 自动应用。逐条证据见
  `docs/quality/release-build-first-run-2026-10-04.md`。

**仍未覆盖**

- release 包的真机端到端（JSON 导出 → 导入 → 与库逐字段比对）。静态验证再强也替代不了
  这一步：它证明的是「类与字符串还在」，不是「这条代码路径真的能跑通」。

### 真机验收与验收脚本修正（2026-10-03）

在 Redmi（`rothko` / 2407FRK8EC，Android 16，HyperOS OS3.0）上对 JSON 完整备份与恢复
做了全链路验收，并补上上一轮唯一没覆盖的「补录角标」。完整报告见
`docs/quality/device-acceptance-2026-10-03.md`。

**Verified**

- JSON 导出走 SAF 写盘成功，文件确实落在 `/sdcard/Download/`，内容校验通过。
- 导出文件与设备数据库**逐 id 逐字段一致** —— 这是强断言。
  「JSON 自洽」只证明文件内部没矛盾，证明不了文件与库一致。
- 导入二次确认框的标题、`将写入：…` 条数、清空警告三条文案都在位。
- 「替换并恢复」真执行通过：恢复后库与备份文件再次逐字段一致，
  四张表的 `sqlite_sequence` 分别抬升到各表 `max(id)`，后续新增不会撞上恢复进来的 id。
- 「撤销上次导入」可用，且撤销前会自动再留一份快照（可以撤销「撤销」）。
- 补录角标：在设备上造一条 45 分钟补录后角标出现，且只挂在补录记录上；
  该记录满足补录的不变量 `end − start == durationMs`。

**Fixed**

- 验收脚本自身四处会让结论失真的问题：坐标按整行取到根节点导致点击全部落空、
  断言读错 dump 文件（假阴性）、有步骤失败仍打印「走通」（假阳性）、
  导入路径没有真正走到选文件（系统选择器的「最近」只索引媒体文件，JSON 不在其中）。
- `docs/quality` 中 `function-naming` 的处数写错（65 → 61）。

### 完整备份与恢复（2026-10-02）

在此之前只有 Excel 导出，而 Excel 是导不回 App 的 —— 换手机等于丢数据。
补上可恢复的备份格式。

**Added**

- **JSON 完整备份 / 恢复**。导出全库为一个 JSON 文件，可原样导回。
  与 Excel 报表是两件事：报表给人看，备份给机器读。
- **导入前校验**（`BackupValidator`）。全部在动数据库之前完成：
  格式标识、结构版本、声明条数 vs 实际条数、重复 id、悬空外键、非法数值。
  任一项不过就拒绝，数据一个字节不动。
- **导入前自动本地备份**（`PreImportBackupKeeper`）。滚动保留 3 份，
  并据此提供「撤销上次导入」。
- 新增 36 个单测（编解码 8 / 校验 14 / 真实 Room 往返 9 / 自动备份 5），
  合计 206 个。往返测试走真 SQLite，断言**逐字段一致**而不是只比条数 ——
  条数对了但某条记录的 `projectId` 错位，正是"看起来恢复了、统计却全变了"的典型。

**Changed**

- 数据导出页改为「数据导出与恢复」，分两段：完整备份（JSON）与 Excel 报表。
- 恢复是**整体替换**而非合并。合并需要一套 id 冲突消解规则
  （同 id 谁赢？同名不同 id 算不算同一个项目？），猜错会把两段历史搅在一起，
  且事后无法分辨。替换语义确定，配合自动备份 + 二次确认，用户始终有退路。
- 活动会话（RUNNING / PAUSED）**不进备份也不恢复**：它的单调时钟只对当时那次
  开机有效，换设备或重启后不对应任何真实时刻。事务内还会再查一次活动会话，
  正在计时时拒绝恢复 —— 界面上的检查只为提示，这里的检查才是正确性保障。
- 字符串资源：删除 26 处未被引用的条目，新增备份/恢复相关条目。

**Fixed**

- 设置页与消费分类页既没有标题栏也没有返回按钮，只能靠系统返回手势退出。
  `CategoryManageScreen` 的 `onBack` 参数甚至从未被使用。
  （`AppNavHost` 只给三个顶层 Tab 画顶部栏，这两页需要自己带。）

### ktlint 存量债清理（2026-10-02）

**Changed**

- **存量风格违规 1469 处（124 个文件）→ 0**。分三步走：
  1. 先建基线（`app/config/ktlint-baseline.txt`），让门禁对新代码立刻生效；
  2. 再 `ktlint -F` 批量格式化；
  3. 最后把基线收紧到 0，门禁按最严标准执行。
- `ktlint` 新增三个任务并挂进 `check`：
  - `ktlintSources` —— 扫描并输出报告
  - `ktlintBaseline` —— 刷新基线
  - `ktlintGate` —— 与基线比对，出现新增违规就失败
  - `ktlintFormatAll` —— `ktlint -F` 批量修复（名字不能叫 `ktlintFormat`，
    插件自己注册了同名任务）
- 基线按 `(文件, 规则, 出现次数)` 记录，**不记行号**。行号会随任何一次编辑整体
  位移，那种基线一改就满屏假警报，等于没有基线。
- `.editorconfig` 增加 `ktlint_function_naming_ignore_when_annotated_with = Composable`。
  Compose 要求 Composable 函数名 PascalCase（小写开头编译器直接报错），
  而 ktlint 的 `function-naming` 不认识 `@Composable`，把项目里 61 个
  Composable 全报成误报。只点名这一个注解，普通函数的命名仍然照查。
- 格式化过程中自己造出的 5 处超长行（`max-line-length`，140 上限）一并修掉：
  4 处是新写的单行构造器调用，1 处是老代码因缩进加深被顶过线。

**Fixed**

- 11 处 `discouraged-comment-location`（参数列表里的尾随注释）与
  2 处 `no-consecutive-comments`（相邻注释）。

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

- ~~补录角标未用真实数据验证（设备库内 4 条会话全部来自计时，无补录记录）~~ ——
  已于 2026-10-03 补验，见上方「真机验收与验收脚本修正」。
- ~~`SettingsScreen` / `CategoryManageScreen` 没有标题栏与返回按钮~~ —— 已修，见上文。

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
  清单见 `docs/quality/ktlint-violations-before-cleanup.log`。
  （已于同日清理归零，见上文。）
- Android Lint 54 条 warning（26 条为未使用的字符串资源）。
  （26 条未使用资源已删除，现为 27 条；剩余全部是
  `NewerVersionAvailable` / `GradleDependency` / `OldTargetApi` 一类的
  「依赖或目标 SDK 版本偏旧」提示，没有代码缺陷。）

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
