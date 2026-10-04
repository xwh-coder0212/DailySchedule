# 令牌层落到「用户真正看得见的页面」（2026-10-04）

## 这份文档回答什么

上一份 [`ui-foundation-2026-10-04.md`](ui-foundation-2026-10-04.md) 建好了令牌层（间距 / 排版 / 动效 / 空状态），
并把它接进了一个**示范页**。那份文档的假设是：示范页就是用户打开 App 看到的第一屏。

**这个假设是错的。** 真机验收时把它证伪了 —— 见下节。

本文记录：错在哪、修正了什么、以及修正后的设备证据。

## 一、先纠正上一份文档的一个错误判断

上一份文档写：

> `feature/home/HomeScreen.kt`：作为示范页接入全部四层……

真机截图显示，用户看到的首页是「待办 / 统计 / 记账」三个 Tab，**没有任何一屏是 `HomeScreen`**。
回到源码核对：

```
$ grep -rn "HomeScreen" --include=*.kt --include=*.kts --include=*.xml . | grep -v /build/
./app/src/main/java/com/dailyschedule/app/feature/home/HomeScreen.kt:50:fun HomeScreen(...
```

全仓**只有定义，零调用**。`HomeViewModel` 同样只有一处定义。

再核对导航图 `navigation/AppNavHost.kt`，`NavHost` 注册的目的地是：

| 目的地 | 页面 |
| --- | --- |
| `Projects`（startDestination，待办 Tab） | `ProjectsScreen` |
| `Stats`（统计 Tab） | `StatsScreen` |
| `Expense`（记账 Tab） | `ExpenseListScreen` |
| `ExpenseEdit` / `ProjectSessions` / `ProjectEdit` | 详情页 |
| `Settings` / `CategoryManager` / `DataTransfer` | 设置链路 |

**没有 `Home` 目的地。** `git log` 显示 `HomeScreen.kt` 自首次提交（`9076d24`）起就存在，
从未被接进导航 —— 也就是说它一直是死代码。

**后果**：上一轮为 `HomeScreen` 做的 `DsEmptyState`、`numeric()`、`animateContentSize()`、
`DsSpacing` 改造，在 App 里**一帧都不会出现**。上一份文档里「等价替换零视觉变化」的结论不受影响
（那 21 处圆角在 11 个消费端文件里，是可见页面的），但「示范页已接入」这句不成立。

`HomeScreen` / `HomeViewModel` 本轮**没有删除**：那是一个产品决策（未来要不要做首页），
不属于界面整治范围，留给评审。

## 二、把改动落到可见页面上

三个 Tab 页各自的问题与本轮改法：

| 页面 | 改前 | 改后 |
| --- | --- | --- |
| `ProjectsScreen`（待办） | 空态是居中一行灰字 `bodyMedium` | `DsEmptyState`（`Icons.Outlined.Checklist` + 标题 + 引导） |
| `StatsScreen`（统计） | 空态是居中一行灰字 `bodyMedium` | `DsEmptyState`（`Icons.Outlined.PieChart` + 标题） |
| `ExpenseListScreen`（记账） | 空态是居中一行灰字 `bodyMedium` | `DsEmptyState`（`Icons.AutoMirrored.Outlined.ReceiptLong` + 标题 + 引导） |

空态文案拆成「标题 + 引导」两段，读起来才有层次：

| 键 | 改前 | 改后 |
| --- | --- | --- |
| `project_empty` | `还没有项目。点右下角 + 创建第一个，比如「考研数学」。` | 拆成 `project_empty_title` = `还没有项目` + `project_empty` = `点右下角 + 创建第一个，比如「考研数学」。` |
| `stats_chart_empty` | `这个周期还没有数据。` | `这个周期还没有数据`（作标题，去掉句末句号） |
| `expense_empty` | `本月还没有记账。点右下角 + 记第一笔。` | 拆成 `expense_empty_title` = `本月还没有记账` + `expense_empty` = `点右下角 + 记第一笔。` |

手写 `fontFeatureSettings = "tnum"` 收敛到 `DsText` 令牌。三个可见页面共 5 处：

| 文件:行 | 改前 | 改后 |
| --- | --- | --- |
| `ProjectsScreen.kt:283` | `displayMedium.copy(fontWeight = Medium, fontFeatureSettings = "tnum")` | `numericEmphasis(displayMedium)` |
| `StatsScreen.kt:431` | `headlineSmall.copy(fontWeight = Medium, fontFeatureSettings = "tnum")` | `numericEmphasis(headlineSmall)` |
| `ExpenseListScreen.kt:204` | `titleMedium.copy(fontFeatureSettings = "tnum")` | `numeric(titleMedium)` |
| `ExpenseListScreen.kt:249` | `headlineMedium.copy(fontWeight = SemiBold, fontFeatureSettings = "tnum")` | `numeric(headlineMedium).copy(fontWeight = SemiBold)` |
| `ExpenseListScreen.kt:371` | `titleSmall.copy(fontWeight = Medium, fontFeatureSettings = "tnum")` | `numericEmphasis(titleSmall)` |

注意第 249 处**没有**用 `numericEmphasis`：原值是 `SemiBold`，而 `numericEmphasis` 会写成 `Medium`。
用 `numeric(...).copy(fontWeight = SemiBold)` 保持字重不变，只把 tnum 收进令牌。

另外给三张会变高的卡片加了 `animateContentSize()`：`ProjectsScreen` 的正在专注卡、
`StatsScreen` 的 `SectionCard`、`ExpenseListScreen` 的月度汇总卡。

顺带清掉 `ProjectsScreen` 里因空态替换而变成孤儿的 `TextAlign` import。

### 二之续、把剩下 11 处手写 tnum 也一并收敛（同日晚）

上一份文档断言「其余手写 tnum 各处写法不同（有的带 `fontSize`、有的带 `fontWeight`），
机械替换会改视觉，需逐处判断」。**这句话经不起核对。**

把剩余调用点全部列出来之后，它们形状**完全一致**：

```kotlin
MaterialTheme.typography.X.copy(fontWeight = W, fontFeatureSettings = "tnum")
```

而 `numeric()` 的实现就是 `base.copy(fontFeatureSettings = "tnum")`，不动任何其它属性。
所以每一点都能严格等价改写：

| 原写法 | 等价改写 |
| --- | --- |
| `X.copy(fontWeight = Medium, fontFeatureSettings = "tnum")` | `numericEmphasis(X)` |
| `X.copy(fontWeight = SemiBold, fontFeatureSettings = "tnum")` | `numeric(X).copy(fontWeight = SemiBold)` |
| `X.copy(fontFeatureSettings = "tnum")`（不设字重） | `numeric(X)` |

| 文件 | 处数 |
| --- | --- |
| `ProjectSessionsScreen.kt` | 5 |
| `DsCharts.kt` | 2 |
| `ProjectDetailSheet.kt` | 2 |
| `ExpenseEditScreen.kt` | 1 |
| `DataTransferScreen.kt` | 1 |
| **合计** | **11** |

另删掉 `DsCharts.kt` / `ExpenseEditScreen.kt` 里因此变成孤儿的 `FontWeight` import。

改完后：

```
$ grep -rn 'fontFeatureSettings' app/src/main/java --include=*.kt | grep -v core/ui/theme/DsText.kt
（0 行）
```

全工程数字排版只剩 `DsText.kt` 一个定义处。

**顺带纠正一个数字**：上一份文档与本节初稿都写「剩余 15 处」。
实际是 HomeScreen 4 处 + 三个可见页 5 处 + 其余 11 处 = 20（与「20 处消费端」吻合），
**剩余的正确值是 11**。「15」是没有回读源码就写下的数 —— 与上一轮圆角计数少算 1 处是同一类错误。

## 三、验证证据

### 3.1 门禁（与改造前的对照）

| 门禁 | 命令 | 结果 | 改造前 |
| --- | --- | --- | --- |
| 编译（release） | `gradle --no-daemon :app:assembleRelease` | `RELEASE_EXIT=0`，**编译警告 0 条** | 0 警告 |
| 单测 | `gradle --no-daemon :app:testDebugUnitTest --rerun-tasks` | **213 用例 / 24 类 / 0 失败 / 0 错误 / 0 跳过**（`TEST_EXIT=0`） | 213 / 0 / 0 / 0 |
| ktlint | `gradle --no-daemon :app:ktlintGate` | 通过，**基线 0 处存量，未引入新违规**（`KTLINT_EXIT=0`） | 同样通过 |
| Android Lint | `gradle --no-daemon :app:lintDebug` | `LINT_EXIT=0`，**0 error / 27 warning** | 0 error / 27 warning |

单测与 Lint 数字与改造前逐项相同。

**第二遍（收敛其余 11 处 tnum）之后四道门禁重跑，结果完全相同**：
`KTLINT_EXIT=0`（基线 0 处）、`LINT_EXIT=0`（0 error / 27 warning）、
`TEST_EXIT=0`（213 / 24 / 0 / 0 / 0）、`RELEASE_EXIT=0`（**0 条编译警告**）。
这也是「等价改写」这一判断的第二组证据。

编译过程中出现并已修掉一条：`Icons.Outlined.ReceiptLong` 被标记 deprecated，提示改用
AutoMirrored 版本。已改为 `Icons.AutoMirrored.Outlined.ReceiptLong`，改后警告数回到 0。

### 3.2 设备证据（Redmi 2407FRK8EC / Android 16 / HyperOS，release 包）

| 项 | 值 |
| --- | --- |
| APK | 7,380,325 字节，SHA-256 `7a8b2d4d1a012c72c4db0a6c85fcb25320c894257a7ecf80b6fd6d3c41dc2eef`（第二遍收敛后重构建；第一遍为 `036b5d78…`） |
| 体积变化 | 7,379,541 → 7,380,325（**+784 字节**，与新加空态图标、两条新字符串相符） |
| 安装 | `adb install -r --no-streaming` → `Success` |
| 冷启动 | `Status: ok`，`TotalTime 176 ms` |
| 崩溃 / ANR | logcat 全文 grep `FATAL EXCEPTION` / `ANR in com.dailyschedule.app` → **命中数 0** |

第二遍重装后的冒烟测试（改动过的详情页逐个走一遍）：

| 页 | 结果 |
| --- | --- |
| 安装 | `Success` |
| 冷启动 | `Status: ok`，`TotalTime 219 ms` |
| `ExpenseEditScreen`（记一笔） | 正常打开，内置数字键盘与「选择分类即完成记录」全在 |
| `ProjectDetailSheet`（项目详情） | 正常打开：更换背景 / 编辑 / 排序 / 删除 / 专注历史记录 / 数据统计 / 周热力图 / 累计专注 `0 次 0 小时 0 分钟` |
| 崩溃 / ANR | **0 命中** |

⚠️ 本次冒烟里**我自己的一条断言是无效的**：用 `tap_until` 切「统计」Tab 时，
判定文本写的是 `统计`，而底栏 Tab 标签本身就是「统计」——
于是第一次点击即使没生效，断言也会立刻通过。统计页本次**没有被真正重新核对**；
它的数字由单测 `StatsNumbersEndToEndTest` 覆盖，且 `DsCharts` 的改动是等价改写。

四张页面截图（归档在 `docs/quality/`）：

- [`ui-visible-todo.png`](ui-visible-todo.png) —— 待办页有 1 个项目时的卡片
- [`ui-visible-todo-empty.png`](ui-visible-todo-empty.png) —— **待办页新空态**（清理测试数据后补拍，见 3.4）
- [`ui-visible-stats-empty.png`](ui-visible-stats-empty.png) —— 统计页新空态：图标 + 「这个周期还没有数据」
- [`ui-visible-money-empty.png`](ui-visible-money-empty.png) —— 记账页切到 2026年9月 后的新空态：图标 + 「本月还没有记账」+「点右下角 + 记第一笔。」

⚠️ 口径说明：统计页与记账页的空态**上一轮就已拍到**；**待办页空态此前拍不到** ——
因为待办页只有 1 个项目（见表 3.3），有数据就不会渲染空态。
`ui-visible-todo-empty.png` 是补拍的，也是本轮 `DsEmptyState` 改造里
**唯一一个此前从未在设备上被看到过**的产物。

记账页当前月（2026年10月）渲染 `本月支出 ¥ 1,234.56`、明细行 `-¥ 1,234.56`，
走的就是收敛后的 `numeric()` 路径。

### 3.3 为让验收有意义而造的测试数据

设备数据库原本是空的（0 项目 / 0 记账），JSON 往返会退化成「只搬运 7 个默认分类」。
因此本轮先在设备上造了数据：

- 1 个项目 `StudyMath120`
- 1 笔记账 `餐饮 · ¥1,234.56 · 今天 19:19`

这两条是**测试数据**，不是产品内容。

### 3.4 测试数据的清理（2026-10-04 收尾）

测试数据由实施方（AI）在验收时注入，**也由实施方负责清除**，不留给用户手工删。

清理方式：`adb shell pm clear com.dailyschedule.app` —— 回到 `adb install` 之后的原始状态。
选择它而非「在 App 内逐条删除」的原因是：**当时设备上的 `input tap` 已失效**（见下）。

- 清理前先确认设备库里的内容确实只有测试数据：待办页截图仅 1 张项目卡 `StudyMath120`
  （`docs/quality/ui-visible-todo.png`），与 `verify_backup.sh` 输出的
  `counts={projects:1, categories:7, sessions:0, expenses:1}` 一致 —— 7 个分类是 App 首次建库的默认值。
- 清理后冷启动 `Status: ok / LaunchState: COLD / TotalTime: 246 ms`，
  `FATAL EXCEPTION` 与 `ANR in com.dailyschedule` 命中数 **0**。
- 清理后待办页渲染出新空态：图标 + `还没有项目` + `点右下角 + 创建第一个，比如「考研数学」。`
  截图见 [`ui-visible-todo-empty.png`](ui-visible-todo-empty.png)。

**同一台设备上 `input tap` 会失效（本轮实测）**

同一台 Redmi（`2407FRK8EC` / Android 16 / HyperOS）在本轮稍早时 `input tap` 是可用的，
收尾时却完全失效：`adb shell input tap` 返回 `TAP_EXIT=0` **且不报权限错误**，
但点击既没有触发目标控件，也没有触发遮罩关闭 —— 即事件根本没进入应用。

排查与结论：

| 检查项 | 结果 | 是否原因 |
| --- | --- | --- |
| `dumpsys input_method` 的 `mInputShown` | `false` | 否（不是键盘遮挡） |
| `dumpsys power` 的 `mWakefulness` | `Awake` | 否（不是息屏） |
| `input tap` 退出码 / 权限报错 | `0` / 无 | 否（不报错，静默失效） |
| 屏幕时间戳前后对比 | 8:42 → 8:43 | 否（设备是活的、界面在刷新） |

**关联线索**：本轮每次执行 adb 命令都会看到 `daemon not running; starting now` ——
adb 守护进程在命令之间没能存活。MIUI 的「USB 调试（安全设置）」所授予的
模拟输入能力与 adb 会话绑定，**守护进程重启后该授权可能不再生效**。
判据留在此处：若 `input tap` 静默失效而三项上表检查全否，先尝试在开发者选项里
**关掉再打开「USB 调试（安全设置）」**，而不是反复调坐标。

有了它，release 包的真机 JSON 往返才有内容可搬：

```
counts = {'projects': 1, 'categories': 7, 'sessions': 0, 'expenses': 1}
appVersion = 1.0.0 / dbVersion = 2
导出文件：DailySchedule_备份_20261004_1920.json（3114 字节）
verify_backup.sh --replace 退出码 = 0，0 项失败
```

`appVersion` 是 `1.0.0` 而**没有** `-debug` 后缀 —— 这是「本次结果属于 release 包」的直接凭证。

## 四、一个必须记下来的设备坑（会产出假结论）

造测试数据时，`adb shell input tap` 点「每日目标」输入框和「保存」按钮**连续十几次都不生效**，
而同一屏上方的项目名称框、以及底栏 Tab 都能点。

一度以为是坐标算错。实际原因是：

**MIUI/搜狗输入法在点击文本框后会弹出软键盘，键盘覆盖了表单下半屏。
点在 y≈2114、y≈2360 上的 tap 全部落在键盘上，根本没到 App。**

三点取证：

1. `adb shell dumpsys input_method | grep mInputShown` → `true`；
2. 截图里键盘占据屏幕下半部，目标框与保存按钮都在键盘之后；
3. 关掉键盘（`KEYCODE_BACK`，且**只在 `mInputShown=true` 时按**，否则会误退页面）后，
   保存按钮第一次点击就生效。

另外两个反直觉点：

- **把输入法 `ime disable` 掉并不能阻止键盘弹出**。实测三个输入法全禁用后，
  点文本框仍报 `mInputShown=true`（MIUI 有兜底输入路径）。
- **`adb shell input text` 不弹键盘**（它直接注入按键事件），所以「没看到键盘」
  不等于「键盘没开」；而**点一下文本框就会开**。这个不对称是本次踩坑的根源。

**正确做法**（已可用于后续所有真机脚本）：

```bash
keyboard() { adb shell dumpsys input_method | grep -m1 -o 'mInputShown=[a-z]*'; }
close_kb() {                       # 只在键盘真的开着时才按返回
  for i in 1 2 3 4; do
    [ "$(keyboard)" = "mInputShown=false" ] && return 0
    adb shell input keyevent KEYCODE_BACK; sleep 1.3
  done
  return 1
}
# 规则：点击 y > 约 1500 的控件之前，必须先 close_kb 并确认 mInputShown=false
```

顺带一提，记账页是**自带数字键盘**（1-9 / . / 0 / ⌫）的表单，不弹软键盘，
所以那一页的 tap 一直正常 —— 这也解释了为什么问题只在「新建项目」这一页出现。

## 五、仍未覆盖

| 项 | 状态 | 原因 |
| --- | --- | --- |
| `HomeScreen` / `HomeViewModel` 的去留 | **未决** | 死代码属实，但「做首页」还是「删掉」是产品决策，不单方面定 |
| 空态图标尺寸 | **待评审（已有真机截图可判）** | 当前用 `DsSpacing.xxxl`（32dp）。[`ui-visible-todo-empty.png`](ui-visible-todo-empty.png) 是第一个真实空态截图，图标在 1220×2712 的屏上明显偏小，且标题与引导之间留白偏大；是否把图标提到 40dp、收紧两行间距属视觉决策 |
| ~~其余手写 tnum~~ | **已清零** | 见「二之续」：11 处全部等价收敛，`grep` 结果为 0 处 |
| 非槽位圆角 13 处 | 未归位 | 见上一份文档 |
| 字号定稿 | 未做 | `DsTypography` 仍等于 Material3 默认，等排版评审 |
| 其余详情页接令牌 | 未做 | 等排版评审一次性做，避免做两遍 |
| 真机跑 release 包的**逐字段库比对** | 未做 | 依赖 `run-as`，release 包不可 debuggable；脚本已明确打「已跳过」而不是假装通过 |
