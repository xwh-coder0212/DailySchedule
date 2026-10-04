# 界面基础设施：令牌层收口（2026-10-04）

## 一、问题的定性：不是缺组件，是缺约束

用户反馈「前端有点潦草」。先量化，不靠感觉。

扫描 `app/src/main/java` 全量源码，结果如下：

| 探针 | 改造前 | 含义 |
| --- | --- | --- |
| `TabularNumbers` 引用数 | **0** | 主题里定义了，页面没用 |
| `DisplayNumberStyle` 引用数 | **0** | 同上 |
| `TimerNumberStyle` 引用数 | **0** | 同上 |
| `MaterialTheme.shapes` 引用数 | **0** | 圆角令牌从未被使用 |
| 硬编码 `RoundedCornerShape(N.dp)` | **39** 处 | 其中 5 处在 `ThemePackSpec` 内属定义，**34 处在消费端** |
| 手写 `fontFeatureSettings = "tnum"` | **21** 处 | 其中 1 处是定义，**20 处在消费端** |
| dp 字面量总数 | **233** 处 | 另有 `9/11/14/22/26/30.dp` 这类不在 4dp 网格上的值 |
| `AnimatedVisibility` / `animateContentSize` / `Crossfade` / `animateColorAsState` / `animateFloatAsState` | **各 0 个文件** | 界面里一切变化都是硬切 |
| `EmptyState` 组件 | **不存在** | 各页面自己拼"一行灰字" |
| `LocalMinimumInteractiveComponentSize` / `sizeIn` / `minHeight` | 各 **0** | 触摸目标没有统一保障 |

**结论**：设计令牌层已经建好（`shapes` 有 5 个槽位、排版有 3 个令牌），但页面一处都没用，
全部另起一套手写值。同一件事存在 20 份副本 —— 改一处不会全局生效，令牌层等于不存在。

这解释了"说不出哪里怪"：不是某一处难看，而是**同一类元素的间距、圆角、数字排版各自为政**。

另外，动效全为 0 是比配色影响更大的问题：列表增删、卡片高度变化、按钮状态切换全是瞬间替换，
这种"硬切"是界面显得廉价的直接来源。

## 二、本轮动作

### 新增 4 个文件

| 文件 | 作用 |
| --- | --- |
| `core/ui/theme/DsSpacing.kt` | 间距 / 尺寸令牌。9 个刻度 + 页面边距 + 列表行最小高度 + 最小触摸目标 |
| `core/ui/theme/DsText.kt` | 排版令牌收口。提供 `numeric()` / `numericEmphasis()` 两个函数，把手写 tnum 收敛成一处 |
| `core/ui/theme/DsMotion.kt` | 动效规范。三档时长（160 / 240 / 360ms）+ 进出曲线 + `dsTween()` |
| `core/ui/component/DsEmptyState.kt` | 统一空状态。图标 + 标题 + 说明 + 可选按钮，`contentColor` 可覆盖以适配彩色卡片 |

### 改造既有文件

- `core/ui/theme/Theme.kt`：三个排版令牌移入 `DsText.kt`；`typography` 默认值由裸 `Typography()` 改为
  `DsTypography`，让排版有唯一定稿入口；补全各层归属说明。
- `core/ui/component/DsEmptyState.kt`：加 `contentColor` 参数。默认 `onSurfaceVariant` 放在
  `primaryContainer` 这类彩色卡片上对比度不足，必须能覆盖。
- `feature/home/HomeScreen.kt`：作为示范页接入全部四层——
  - dp 字面量 → `DsSpacing.*`
  - `RoundedCornerShape(20.dp)` → `MaterialTheme.shapes.extraLarge`
  - 4 处手写 tnum → `numeric()` / `numericEmphasis()`
  - 两处"一行灰字"空态 → `DsEmptyState`（`Icons.Outlined.Timer` / `Icons.Outlined.History`）
  - 三张卡片加 `animateContentSize()`，让有/无会话切换、列表增删时高度平滑过渡

### 存量治理：21 处圆角等价收回令牌

消费端（排除定义处 `ThemePackSpec`）原始共 **34 处** `RoundedCornerShape`，
与 `shapes` 槽位对照如下：

| 取值 | 处数 | 槽位 | 处理 |
| --- | --- | --- | --- |
| `12.dp` | 10 | `medium`（12dp） | **本轮替换**（零视觉变化） |
| `16.dp` | 9 | `large`（16dp） | **本轮替换**（零视觉变化） |
| `20.dp` | 2 | `extraLarge`（20dp） | **本轮替换**（随 `HomeScreen` 改造一并） |
| `14.dp` | 3 | 介于 medium 与 large 之间 | 未动 |
| `10.dp` | 3 | 介于 small 与 medium 之间 | 未动 |
| `9.dp` | 2 | 同上 | 未动 |
| `11.dp` | 2 | 同上 | 未动 |
| `6.dp` | 1 | 介于 extraSmall 与 small 之间 | 未动 |
| `2.dp` | 1 | 非槽位 | 未动 |
| `topStart = 6.dp, topEnd = 6.dp` | 1 | 命名参数形式 | 未动 |
| **合计** | **34** | 替换 21 / 未动 13 | — |

替换在 11 个消费端文件上批量进行（`12.dp` → `MaterialTheme.shapes.medium`、
`16.dp` → `MaterialTheme.shapes.large`，数值完全相等），另加 `HomeScreen` 的 2 处
`20.dp` → `shapes.extraLarge`。`ThemePackSpec.kt`（定义处）明确排除。

**注**：上表第 10 行的命名参数写法（`topStart = …`）不会被 `RoundedCornerShape(N.dp)`
这种简单模式匹配到。第一版统计漏了它，得出「剩余 12 处」，实际是 13 处 ——
按简单正则数数会少算，这类计数一律回读源码行核对。

其中 5 个文件替换后 `RoundedCornerShape` 成为孤儿 import，已一并删除
（`DsNumberPad` / `ExpenseEditScreen` / `CategoryManageScreen` / `SettingsScreen` / `DataTransferScreen`）；
其余 6 个文件仍有非槽位取值在用，import 保留。

**为什么只做 21 处**：剩下 13 处不是 4dp 网格值，归位必然改变视觉（14→12 或 14→16）。
那属于视觉微调，需与排版评审一起定，不单方面改。

## 三、验证证据

| 门禁 | 命令 | 结果 |
| --- | --- | --- |
| 编译 | `bash /d/toolchain/compile_app.sh` | `COMPILE_EXIT=0`，**0 警告** |
| 单测 | `gradle --no-daemon testDebugUnitTest` | **213 用例 / 24 类 / 0 失败 / 0 错误 / 0 跳过** |
| ktlint | `gradle --no-daemon :app:ktlintGate` | 通过，**基线 0 处存量，本次未引入新违规** |
| Android Lint | `gradle --no-daemon :app:lintDebug` | `LINT_EXIT=0`，**0 error / 27 warning**（与改造前逐项一致） |

单测与 Lint 数字与改造前完全相同，佐证「等价替换」这一判断 —— 没有改到任何被测试覆盖的行为。

## 四、明确没做的，以及为什么

1. **字号一个都没改。** `DsTypography` 目前仍等于 Material3 默认。三个大数字令牌
   （34sp / 40sp）与页面在用的 Material3 基准（`displayMedium` 45sp、`titleLarge` 22sp）
   不一致，统一到哪一套属于排版决策，等评审意见，不抢跑。
2. **页面里 20 处手写 tnum 未收敛。** 除 `HomeScreen` 外的写法各不相同
   （有的带 `fontWeight`、有的带 `fontSize`），机械替换会改变视觉，需逐处判断。
   组件层 `DsCharts.kt` 的 2 处同理。
3. **其余 11 个页面未接入令牌。** 批量替换上百处 dp 会与即将到来的排版评审冲突，
   一次到位比分两次更省。
4. **非槽位圆角 13 处未归位。** 见上文。
5. **未做视觉验证。** 本次改动没有在设备上看过一眼。原因见下节。

## 五、未验证的边界（重要）

改动**没有经过任何真机或模拟器验证**，只过了编译与门禁。`DsEmptyState` 的观感、
`animateContentSize()` 的实际手感、`Icons.Outlined.Timer` 的视觉大小，都还没有眼睛看过。

卡点不在代码，在设备：真机（Redmi `2407FRK8EC` / Android 16）连接正常，
但 `adb install` 被 MIUI 拦下：

```
Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]
```

排查结论（都试过了，均不通）：

- 设备上 `global.adb_enabled = 1`，`secure install_non_market_apps = 1`，USB 调试本身正常；
- MIUI 的「USB 调试（安全设置）」开关**不在标准 settings 命名空间里**，adb 侧读不到也改不了；
- `am start` 拉起 MIUI 安装器（`com.miui.packageinstaller/com.miui.packageInstaller.InstallStart`，
  exported=true）时，`file://` 与 `content://` 两种 URI 都试了：日志显示 Activity 确实被创建
  （有 `ActivityRecord` 与 `layerId`），随后**立即 `onHandleDestroyed`**，MIUI 主动拒绝了这次调用，
  前台回到桌面。

所以这一步需要人手在设备上开一次开关，然后我可以自动化跑完全部验收。
