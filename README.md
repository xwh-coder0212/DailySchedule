# DailySchedule

个人生活记录 Android App。只回答两个问题：**时间花在哪**、**钱花在哪**。

纯本地、零网络权限、不接云同步 —— 数据只存在于设备上，换机靠手动导出。

---

## 目录

- [为什么做这个](#为什么做这个)
- [当前能力](#当前能力)
- [技术栈](#技术栈)
- [架构](#架构)
- [数据与隐私](#数据与隐私)
- [快速开始](#快速开始)
- [质量门禁](#质量门禁)
- [文档索引](#文档索引)
- [版本规划](#版本规划)
- [许可](#许可)

---

## 为什么做这个

市面上的时间管理 App 与记账 App 是割裂的两套东西：番茄钟只管时长，记账只管金额，
两者之间没有"同一个项目上我投入了多少小时、花了多少钱"这种视图。

DailySchedule 把两条流水合并到**项目**这一个维度上：

```
考研数学   128h32m   ¥680
英语       46h10m    ¥120
```

同类 App 的另一个问题是数据在别人的服务器上。这个 App 从 Manifest 层面就不申请
`INTERNET` 权限 —— 不是"承诺不上传"，而是**物理上没有上传的能力**。

## 当前能力

### 已实现

| 模块 | 说明 |
| --- | --- |
| 待办（项目） | 列表 / 新建 / 编辑 / 排序 / 归档；详情面板弹出，不占独立路由 |
| 计时 | 前台服务常驻通知、暂停 / 继续 / 结束、精确闹钟到点提醒、开机后处理未结束会话 |
| 补录 | 手动补一条历史会话，标「补录」来源，与计时会话同表共存 |
| 记账 | 按月列表 + 行内左滑编辑 / 删除，与上月对比 |
| 统计 | 专注 / 消费分段切换；环形（占比）、柱状（周分布）、折线（日 / 月趋势）三图全部手写 Canvas，零图表依赖 |
| 设置 | 主题三态（跟随系统 / 浅色 / 深色）、Monet 动态取色、日切时刻、周起始日、分类管理、数据导出与恢复 |
| 导出 | 手写 `XlsxWriter` 生成 .xlsx（无第三方表格库），走 SAF 由用户选择落点 |
| 完整备份与恢复 | 整库导出为一份 JSON，可从该文件恢复；导入前自动快照当前库并支持撤销，活跃会话不参与备份 |

### 未实现

| 项 | 说明 |
| --- | --- |
| 桌面小组件 | 依赖（Glance）已在版本目录声明，代码未写 |

## 技术栈

| 类别 | 选型 | 版本 |
| --- | --- | --- |
| 构建 | Gradle / AGP | 9.5.0 / 9.3.0 |
| 语言 | Kotlin（JVM 21） | 2.3.21 |
| UI | Jetpack Compose + Material 3 | BOM 2026.08.00 |
| 导航 | Navigation Compose | 2.9.6 |
| 持久化 | Room + KSP / DataStore Preferences | 2.8.4 / 1.2.0 |
| 依赖注入 | Hilt | 2.60.1 |
| 后台 | Foreground Service + Exact Alarm | — |
| 序列化 | kotlinx.serialization | 1.9.0 |
| 异步 | kotlinx.coroutines | 1.10.2 |
| 测试 | JUnit4 + Truth + Turbine + Mockk + Robolectric | — |
| SDK | minSdk 26 / targetSdk 36 / compileSdk 37 | — |

**这个表里删过一行东西**：曾声明 `androidx.work:work-runtime-ktx 2.11.0`，
但全工程零调用（唯一一次出现是 `BootCompletedReceiver` 的注释里提到「Worker / JobScheduler」，
属说明文字）。保时提醒、开机恢复都用 `AlarmManager` + `BroadcastReceiver` 实现，
没有一处需要 WorkManager。

**AGP 9 起内置 Kotlin 支持**，因此没有 `org.jetbrains.kotlin.android` 插件，
只需要 `kotlin.plugin.compose` / `kotlin.plugin.serialization`。

## 架构

依赖方向单向，`core/` 在最底层被各层共用，不允许反向依赖：

```
界面层 (Compose Screen)
    ↓
状态层 (ViewModel, StateFlow)
    ↓
领域层 (UseCase)
    ↓
数据层 (Repository → Room DAO / DataStore)
    ↑
core/  ← 纯 Kotlin：时间、金额、结果类型、导出、统计聚合
```

```
app/src/main/java/com/dailyschedule/app/
├── MainActivity.kt / DailyScheduleApplication.kt
├── core/                 纯逻辑，不依赖 Android UI
│   ├── time/             Clock、DayBoundary、DurationCalculator / Formatter
│   ├── money/            金额换算（分为单位的整数运算）
│   ├── result/           AppResult 密封类型 + onSuccess / onFailure 扩展
│   ├── stats/            统计聚合纯函数（空格补 0、百分比最大余数法）
│   ├── export/           ExportTable / ExportTableFactory / XlsxWriter
│   ├── log/              AppLogger、CrashHandler
│   └── ui/               theme（主题包、Monet）、component（手写 Canvas 图表）
├── data/
│   ├── db/               AppDatabase、4 张表、Migrations、DAO、seed
│   ├── mapper/           Entity ↔ Domain
│   ├── repository/       5 个 RepositoryImpl
│   └── export/           导出落盘
├── domain/
│   ├── model/            领域模型
│   ├── repository/       仓储接口
│   └── usecase/          bootstrap / expense / project / session / timer
├── feature/              每个包 = 一个页面 + 一个 ViewModel
│   ├── home/             首页（计时卡 + 三数字 + 今日时间轴）
│   ├── projects/         待办列表 / 编辑 / 详情面板 / 排序 / 会话历史
│   ├── expense/          记账列表 / 编辑
│   ├── stats/            统计
│   ├── settings/         设置 / 分类管理
│   └── transfer/         数据导出
├── navigation/           AppNavHost、Route、TopLevelDestination
└── timer/                TimerController、前台服务、通知、开机接收器
```

### 几个刻意的设计决定

**日期口径只有一个出口。** 所有按天聚合都走 `core/time/DayBoundary`，
业务代码里不出现 `LocalDate.now()`，SQL 里不出现 `date('now')`。
日切时刻（凌晨几点算第二天）是用户可配的，口径必须单一，否则统计页和列表页会互相矛盾。

**计时的权威性是 `elapsedRealtime` 的差，不是墙上时钟。** 用户改系统时间不会让计时变长；
重启后未结束的会话有一条兜底路径（只发通知，不擅自补记时长）。

**`AppResult` 是自定义密封类型，不是 Kotlin 的 `Result`。** 需要业务语义的失败原因
（`AppError`），`Result` 的 `Throwable` 装不下。扩展函数 `onSuccess` / `onFailure`
是唯一的消费方式。

**百分比用最大余数法算。** 三个项目各 1/3，逐个四舍五入会显示成 33 / 33 / 33（合计 99）。
`core/stats/StatsAggregator.percentShares` 保证合计恒为 100。

**图表是手写 Canvas，零依赖。** 颜色一律作为参数传入，组件本身不认识主题 ——
有单测（`ThemeHardcodeTest`）禁止 `core/ui/theme/` 之外出现 `Color(0x…)` 字面量。

## 数据与隐私

| 项 | 现状 |
| --- | --- |
| 网络权限 | **无**。Manifest 中不声明 `INTERNET` / `ACCESS_NETWORK_STATE` |
| 存储权限 | **无**。导出走 SAF（`ACTION_CREATE_DOCUMENT`），不申请外部存储权限 |
| 定位权限 | **无** |
| 云备份 | `android:allowBackup="false"`，且不实现任何厂商备份适配 |
| 崩溃日志 | 只落本地文件，不上报 |
| 多账户 / 社交 | 无 |
| 数据库 | Room + SQLite，schema 版本化（`app/schemas/*.json`，已 v1 → v2 迁移） |

声明的全部权限：

```
POST_NOTIFICATIONS            常驻通知与到点提醒
FOREGROUND_SERVICE            计时期间维持通知
FOREGROUND_SERVICE_SPECIAL_USE
SCHEDULE_EXACT_ALARM          番茄到点精确提醒
RECEIVE_BOOT_COMPLETED        开机后处理未结束的会话
VIBRATE                       记一笔成功反馈
```

## 快速开始

### 环境要求

- JDK 21
- Android SDK（`platforms` 含 compileSdk 37、`build-tools`、`platform-tools`）
- 无需额外安装 Gradle —— 仓库自带 Wrapper

### 构建

```bash
# 1. 指向本机 SDK
echo "sdk.dir=/你的/Android/sdk" > local.properties

# 2. 编译
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug

# 3. 产物
# app/build/outputs/apk/debug/app-debug.apk
```

### release 包与签名

`release` 已开启 `isMinifyEnabled` 与 `isShrinkResources`（实测包体 26.93 MB → 7.03 MB）。
要产出**可安装**的 release 包，需要在仓库根放一份 `key.properties`（已被 `.gitignore` 挡住）：

```properties
storeFile=D:/path/to/your.jks
storePassword=…
keyAlias=…
keyPassword=…
```

```bash
./gradlew assembleRelease        # 产物：app/build/outputs/apk/release/app-release.apk
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

**没有 `key.properties` 时构建不会失败，而是产出一个 unsigned 包并在构建输出里打一条警告。**
这是刻意的：keystore 不入库，硬失败会让没有密钥的机器/CI 连「R8 有没有把东西裁坏」都验不了。
代价是 unsigned 包在设备上会以 `INSTALL_PARSE_FAILED_NO_CERTIFICATES` 失败，
而这一点只看 `BUILD SUCCESSFUL` 是看不出来的，所以那条警告不能忽略。

PKCS12 格式下 `keyPassword` 必须等于 `storePassword`，否则 `keytool` 只会警告一句
然后用 `storePassword` 覆盖掉你写的 `keyPassword`。

首次 release 构建的完整静态验证（R8 mapping / usage / dexdump、序列化与枚举是否被裁）见
[docs/quality/release-build-first-run-2026-10-04.md](./docs/quality/release-build-first-run-2026-10-04.md)。

静态证据证伪不了「跑起来会炸」（资源被误裁、`NoClassDefFoundError`、反序列化路径问题
都只在运行时暴露），所以补了一轮运行期验证：把 release 包装进 Android 14 模拟器跑
JSON 导出 → 恢复 → 撤销，结果 `退出码=0`、logcat 崩溃特征 0 命中、冷启动 415 ms。
详见 [docs/quality/release-runtime-verification-2026-10-04.md](./docs/quality/release-runtime-verification-2026-10-04.md)。
**注意这一轮不是真机验收**（手上没有可连接的设备），真机侧的唯一凭据仍是
`docs/quality/device-acceptance-2026-10-0{2,3}.md` 那两轮。

### 测试

```bash
./gradlew testDebugUnitTest --rerun-tasks     # 213 个单测
```

**本地一定要带 `--rerun-tasks`。** 源码没变时 `testDebugUnitTest` 会被判为最新直接跳过，
日志里只剩 `BUILD SUCCESSFUL`，什么都没跑。CI 每次都是全新拉取，不需要这个参数。

单测用 Robolectric 跑，不需要设备。`testOptions.unitTests.isReturnDefaultValues = true`
是刻意打开的 —— `android.util.Log` 在纯 JVM 下是抛异常的 stub，打开后日志调用退化为 no-op。

其中 `BackupRoundTripTest` 走**真实的 Room 内存库**而不是 mock DAO：
备份恢复的风险全在"SQLite 里到底发生了什么"——外键顺序、主键自增序列、
触发器、事务边界。mock 掉 DAO 等于把要验的东西全 mock 没了，测试会全绿而线上照样丢数据。

更详细的 Windows 环境搭建与常见报错见 [BUILD.md](./BUILD.md)。

## 质量门禁

当前实测结果（2026-10-04）：

| 门禁 | 命令 | 结果 |
| --- | --- | --- |
| 单元测试 | `./gradlew :app:testDebugUnitTest --rerun-tasks` | **213 通过 / 0 失败 / 0 错误 / 0 跳过**（24 个测试类） |
| Android Lint | `./gradlew :app:lintDebug` | **0 error / 27 warning** |
| ktlint | `./gradlew :app:ktlintGate` | **通过，存量 0 处** |
| debug 打包 | `./gradlew assembleDebug` | 通过 |
| release 打包 | `./gradlew assembleRelease` | 通过，7,379,541 字节，v2 已签名 |
| release 运行期 | `verify_release_rt.sh`（Android 14 模拟器） | **`退出码=0` / 崩溃特征 0 命中**；冷启动 415 ms；JSON 导出→恢复→撤销全通 |
| 真机验收 | `docs/quality/device-acceptance-2026-10-03.md` | Redmi / Android 16 上 14 项全部通过（含 JSON 备份恢复全链路、补录角标） |
| 真机验收（release 包） | —— | **未做**。手上没有可连接的设备；release 包目前只在模拟器上跑过 |

**CI 从 2026-10-04 起存在**（`.github/workflows/ci.yml`）。在它之前，「门禁成立」的前提是
「记得手动跑」；现在推送即跑单测、ktlint 门禁与 Lint，另有一个 job 真的走一次 R8 与资源压缩。
原始输出归档在 `docs/quality/`。

几个需要说明的地方，因为它们都是「看起来绿、实际空过」的坑：

**`ktlintCheck` 是空过的。** ktlint-gradle 12.3.0 认不出 AGP 9 的源集模型，
只生成了 `.kts` 的检查任务，`src/main` 与 `src/test` 下几百个 `.kt` 一个都没扫。
因此仓库里自建了四个直连 ktlint CLI 的任务：

| 任务 | 用途 |
| --- | --- |
| `ktlintSources` | 扫描并输出报告到 `build/reports/ktlint/ktlint.out` |
| `ktlintBaseline` | 用当前扫描结果刷新基线 |
| `ktlintGate` | 与基线比对，出现新增违规就失败（**已挂进 `check`**） |
| `ktlintFormatAll` | `ktlint -F` 批量修复 |

存量 1469 处（124 个文件）已清零，基线随之收紧到 0，门禁按最严标准执行。
清理前的完整违规清单留在 `docs/quality/ktlint-violations-before-cleanup.log`。

**基线记「文件 + 规则 + 次数」，不记行号。** 行号会随任何一次编辑整体位移，
那种基线一改就满屏假警报，等于没有基线。

**Android Lint 曾经连跑都没跑起来。** 早期失败是拉 `lint-gradle` 时网络中断导致的
依赖解析失败，不是配置问题。重试后正常。

原始日志（含违规明细）在 [`docs/quality/`](./docs/quality/)。

## 文档索引

设计与决策文档按阶段编号，`docs/` 下：

| 文件 | 内容 |
| --- | --- |
| `phase0-competitive-research.md` | 竞品分析与市场格局 |
| `phase1-requirements-log.md` | 需求访谈记录与逐条决策依据 |
| `phase2-prd.md` | 正式 PRD |
| `phase3-information-architecture.md` | 页面清单与跳转矩阵 |
| `phase4-data-model.md` | 表结构、索引、状态机 |
| `phase5-technical-architecture.md` | 架构与选型权衡 |
| `phase6-uiux.md` | 视觉语言与页面规格 |
| `phase7-implementation-plan.md` | 实施计划与 WBS |
| `rev2-ui-revision.html` | 第二轮改版方案与落地状态（三 Tab、统计重做、补录标记） |
| `quality/device-acceptance-2026-10-02.md` | 真机验收报告（第一轮）：环境、逐项证据、发现的问题、未覆盖项 |
| `quality/device-acceptance-2026-10-03.md` | 真机验收报告（第二轮）：JSON 备份/恢复全链路、补录角标、验收脚本修正 |
| `quality/release-build-first-run-2026-10-04.md` | release 包首次构建与静态验证：签名、包体对比、R8 有没有裁掉 kotlinx.serialization |
| `quality/README.md` | 质量门禁原始日志的索引与 ktlint 违规分布 |
| `optimization-roadmap-2026-10-03.md` | 验收之后的优化方案与可行性评估（A/B/C/D 四档、依赖顺序、逐项验证方式、A 档执行状态） |
| `../BUILD.md` | 本机构建与排错 |

## 版本规划

- **V1（当前）** —— 计时 + 待办 + 记账 + 统计 + 设置 + xlsx 导出 + 完整备份与恢复。纯本地。
- **V2** —— 桌面小组件、编辑页补齐。
- **V3** —— 主题包扩展、更细的统计维度。

明确不做：Todo 清单、预算、云同步、多账户、银行同步、社交、锁机、游戏化、复式记账。
这些不是"以后再说"，是产品定位上的排除项 —— 见 `docs/phase1-requirements-log.md`。

## 许可

[MIT](./LICENSE)

---

项目规模：主源码 125 个文件 / 14,108 行，测试 27 个文件 / 4,831 行（213 个 `@Test`，24 个测试类）。
