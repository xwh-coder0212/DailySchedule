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
| 设置 | 主题三态（跟随系统 / 浅色 / 深色）、Monet 动态取色、日切时刻、周起始日、分类管理 |
| 导出 | 手写 `XlsxWriter` 生成 .xlsx（无第三方表格库），走 SAF 由用户选择落点 |

### 未实现

| 项 | 说明 |
| --- | --- |
| 桌面小组件 | 依赖（Glance）已在版本目录声明，代码未写 |
| JSON 导入 / 导出 | 备份恢复链路的缺口；当前只有单向上限的 xlsx 导出 |
| 真机验收 | 见 [质量门禁](#质量门禁) |

## 技术栈

| 类别 | 选型 | 版本 |
| --- | --- | --- |
| 构建 | Gradle / AGP | 9.5.0 / 9.3.0 |
| 语言 | Kotlin（JVM 21） | 2.3.21 |
| UI | Jetpack Compose + Material 3 | BOM 2026.08.00 |
| 导航 | Navigation Compose | 2.9.6 |
| 持久化 | Room + KSP / DataStore Preferences | 2.8.4 / 1.2.0 |
| 依赖注入 | Hilt | 2.60.1 |
| 后台 | WorkManager / Foreground Service + Exact Alarm | 2.11.0 |
| 序列化 | kotlinx.serialization | 1.9.0 |
| 异步 | kotlinx.coroutines | 1.10.2 |
| 测试 | JUnit4 + Truth + Turbine + Mockk + Robolectric | — |
| SDK | minSdk 26 / targetSdk 36 / compileSdk 37 | — |

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

发布构建需要自备签名；`release` 已开启 `minifyEnabled` 与 `shrinkResources`。

### 测试

```bash
./gradlew testDebugUnitTest      # 170 个单测
```

单测用 Robolectric 跑，不需要设备。`testOptions.unitTests.isReturnDefaultValues = true`
是刻意打开的 —— `android.util.Log` 在纯 JVM 下是抛异常的 stub，打开后日志调用退化为 no-op。

更详细的 Windows 环境搭建与常见报错见 [BUILD.md](./BUILD.md)。

## 质量门禁

当前实测结果（2026-10-02）：

| 门禁 | 命令 | 结果 |
| --- | --- | --- |
| 编译 | `./gradlew compileDebugKotlin` | 通过，0 警告 |
| 单元测试 | `./gradlew testDebugUnitTest` | **170 通过 / 0 失败** |
| Android Lint | `./gradlew :app:lintDebug` | **0 error / 54 warning** |
| ktlint | `./gradlew :app:ktlintSources` | 能跑，存量 1469 处风格违规**未清** |
| 打包 | `./gradlew assembleDebug` | 通过 |

两点需要说明，因为它们都是「看起来绿、实际空过」的坑：

**`ktlintCheck` 是空过的。** ktlint-gradle 12.3.0 认不出 AGP 9 的源集模型，
只生成了 `.kts` 的检查任务，`src/main` 与 `src/test` 下几百个 `.kt` 一个都没扫。
因此仓库里用 `ktlintSources`（直接调 ktlint CLI，绕开插件的 Android 集成）替代。
存量违规清单见 `docs/quality/quality-gate.log`。

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
| `../BUILD.md` | 本机构建与排错 |

## 版本规划

- **V1（当前）** —— 计时 + 待办 + 记账 + 统计 + 设置 + xlsx 导出。纯本地。
- **V2** —— 桌面小组件、JSON 备份与恢复、编辑页补齐。
- **V3** —— 主题包扩展、更细的统计维度。

明确不做：Todo 清单、预算、云同步、多账户、银行同步、社交、锁机、游戏化、复式记账。
这些不是"以后再说"，是产品定位上的排除项 —— 见 `docs/phase1-requirements-log.md`。

## 许可

[MIT](./LICENSE)

---

项目规模：主源码 116 个文件 / 11,668 行，测试 20 个文件 / 2,931 行。
