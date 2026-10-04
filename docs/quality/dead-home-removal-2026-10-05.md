# 删除死代码 `feature/home/`（2026-10-05）

## 结论

`HomeScreen.kt`（284 行）+ `HomeViewModel.kt`（134 行）**不是「待接线的新页」，是 Rev2 改版漏删的残留**，
本轮已删除。原先把它记成「做首页还是删掉，属产品决策」——**这个判断是错的**，
项目自己的文档早就定过了，不需要再决策一次。

## 一、为什么说判断是错的

三条证据，都在仓库里：

| 证据 | 位置 | 原文 |
| --- | --- | --- |
| Rev2 改版记录 | `CHANGELOG.md` § Rev2 改版（2026-10-01） | 「底部导航改为三项：**待办 / 统计 / 记账**。计时并入待办页顶部，**不再有独立的「今日」「记录」页**；删除旧的 `feature/record/` 整包。」 |
| 设计决策表 | `docs/rev2-ui-revision.html` 决策表 | 「今日页的去向 → **并进统计**」；依据：「底栏只有 3 格。今日的三个数字（学习/花费/专注次数）本来就是统计的『今日』区间，单独占一个 Tab 会和统计页重复」 |
| 设计正文 | `docs/rev2-ui-revision.html` | 「原来『今日』页里的三数字……和今日时间轴，我放进了统计页。」 |

也就是说：**「今日页该不该存在」这个问题已经答过了**（答：不存在，并进统计）。
真正没做完的是**清理动作**——那次改版删了 `feature/record/` 整包，却漏删了 `feature/home/`。
两个包是同一批被废掉的，只清了一半。

判据并非只有文档：`AppNavHost.kt` 的 `TopLevelDestination` 只有 `PROJECTS / STATS / EXPENSE`
三项，`NavHost` 的 `startDestination = Projects`，`feature/home/` 全仓零引用（含测试）。
即**代码侧的现状与设计一致，只有这两个文件是多的**。

## 二、删了什么

| 文件 | 行数 | 说明 |
| --- | --- | --- |
| `app/src/main/java/com/dailyschedule/app/feature/home/HomeScreen.kt` | 284 | 页面 + `TimelineCard` / `TimelineRow` 等私有 Composable |
| `app/src/main/java/com/dailyschedule/app/feature/home/HomeViewModel.kt` | 134 | `@HiltViewModel`，含 `HomeUiState` / `TimelineItem` |

删除前逐项核查过「谁在用」，避免删完留一堆孤儿：

| 被删文件引用的东西 | 除它之外还有谁用 | 结论 |
| --- | --- | --- |
| `SessionRepository.observeActive()` | `ProjectsViewModel`、`TimerForegroundService`、测试 | 保住 |
| `SessionRepository.observeTotalDuration()` / `observeCompletedCount()` | `StatsViewModel` | 保住 |
| `ExpenseRepository.observeTotalCents()` | `ExpenseListViewModel`、`StatsViewModel` | 保住 |
| `ProjectRepository.observeAll()` | 多处 + `ReorderProjectsUseCase` | 保住 |
| `TimelineItem` / `HomeUiState` | 仅 `feature/home/` 内部 | 随文件一起消失 |
| `SessionRepository.observeTimeline()` | **只有它** | 见下节，**保留** |

### 字符串资源

删掉 6 条变为零引用的字符串（均先 grep 确认全仓只剩 `feature/home/` 在引用）：

| 名称 | 值 |
| --- | --- |
| `tab_home` | `今日` |
| `home_label_study` | `学习` |
| `home_label_expense` | `消费` |
| `home_label_focus` | `专注` |
| `home_no_session` | `未开始计时` |
| `home_timeline_empty` | `今天还没有记录。` |

**保留** `record_no_project` / `record_pause` / `record_resume` / `record_stop` 等 ——
名字虽带 `record`，但 Rev2 之后计时控件搬到了 `ProjectsScreen`，
`ProjectsScreen.kt:267/283/295` 正在用它们。**按名字猜用途会误删。**

## 三、`SessionRepository.observeTimeline()` 为什么保留

删掉 `HomeViewModel` 后，这个仓库方法的**生产代码调用点归零**（只剩 `InMemoryRepositories` 测试替身的 override
和 DAO 层被 `DatabaseConstraintsTest` 覆盖）。按「零引用即删」的通则它该删，但它**没有删**，理由是：

它对应的**功能还没实现**。设计文档说「今日时间轴」要进统计页，而统计页当前只有
专注时长 / 专注分布 / 消费 / 消费分布 / 消费趋势五个区块，**没有时间轴**（见下节）。
也就是说 `observeTimeline` 不是「被废弃的旧 API」，而是「功能待补、API 先留着」。

保留它是有代价的（domain 层留了一个无生产调用者的方法）。代价可接受，
因为删掉它意味着将来补时间轴时要重新写一遍 repository + DAO + 测试替身三处。

## 四、顺带发现：`docs/rev2-ui-revision.html` 自相矛盾

同一份设计文档里，关于「今日时间轴」有两处互相冲突的说法：

- **正文（§ 一个我替你定了）**：「原来『今日』页里的三数字……**和今日时间轴**，我放进了统计页。」
- **条目清单（统计页重做那条）**：「统计页按参考图重做**分段切换 + 概览 + 环形 + 柱状 + 折线**重做页面。」——**没提时间轴**。

实现与**条目清单**一致（五个区块，无时间轴），与**正文**不一致。

两种可能：

1. 时间轴在重做时被实际舍弃了，正文那句话是乐观表述（写下时以为会做，后来没做）。
2. 时间轴是**漏做**的功能，统计页少了一块。

**本条不做单方面判断**：两处都是同一份文档写的，我无权替哪一处作准。
已在此记录，`observeTimeline` 因此保留，等判明后再决定「补进统计页」还是「连 API 一起删」。

（注意：这不影响本轮删除的正确性——`HomeScreen` 该不该删与时间轴该不该补是两个独立问题。
即使最终决定补时间轴，也不该复活 `HomeScreen`，而是在统计页内实现。）

## 五、验证

四道门禁 + 运行期验证，全部实跑：

| 项 | 结果 |
| --- | --- |
| ktlint | `KTLINT_EXIT=0`（基线 0 处存量） |
| Android Lint | `LINT_EXIT=0`，**27 条 warning / 0 error**，与删除前**条数相同**（全是 `NewerVersionAvailable` / `UseKtx` / `ObsoleteSdkInt` 一类版本提示，无代码缺陷） |
| 单测 | `TESTS_EXIT=0`，**24 类 / 213 用例 / 0 失败 / 0 错误 / 0 跳过**，与删除前**逐项相同** |
| release 构建 | `RELEASE_EXIT=0`，**0 条编译警告** |
| APK 体积 | 7,380,325 → **7,378,237 字节（−2,088）**，与删掉 418 行 + 6 条字符串相符 |
| APK 签名 | `apksigner verify` V2 通过，证书 SHA-256 `1ac90d20…398fd645`（与既有记录一致） |
| 模拟器运行期 | 安装 `Success`；`versionName=1.0.0`（**不带 `-debug` 后缀**，确认是 release 包）；冷启动 `Status: ok / TotalTime 690 ms`；崩溃特征命中 **0** |
| JSON 端到端 | `verify_backup.sh --replace` **退出码 0 / 0 项失败**（导出 → 替换恢复 → 撤销全通）；导出文件 `appVersion=1.0.0`、`dbVersion=2`、`counts={projects:0, categories:7, sessions:0, expenses:0}` |

单测与 Lint 计数**删前删后逐项相同**，这是「删除的是死代码、没有连带损伤」的直接证据 ——
如果误删了活代码，至少会看到测试类数或编译错误变化。

**底栏三格已视觉确认**：见 [`dead-home-removal-3tabs.png`](dead-home-removal-3tabs.png)，
底部只有「待办 / 统计 / 记账」，没有「今日」。

## 六、没做到的一件

**真机冒烟没做成本轮。** 计划是在 Redmi `2407FRK8EC` 上装一遍，但执行时 `adb devices` 已为空
（设备被拔线）。改用上面那份**模拟器**端到端验证替代，证据强度略低一档：

- 模拟器 `system-images;android-34;google_apis;x86_64`，真机是 Android 16 / HyperOS —— 系统版本不同。
- 模拟器覆盖不到 HyperOS 的厂商行为差异。

本轮改动是**纯删除**，且 compile / R8 / 单测 / Lint / 运行期五项均已通过，感染面很小；
但这一点如实记下，不拿模拟器结论冒充真机结论。

## 七、回滚方式

文件已从工作树移除，但没被销毁：

- 副本：`D:/toolchain/tmp/removed-home/`（`HomeScreen.kt` / `HomeViewModel.kt`）
- git 历史：删除前的提交里两个文件都在

```bash
git checkout <删除前的提交> -- app/src/main/java/com/dailyschedule/app/feature/home/
```

若最终决定恢复「今日页」，不要直接找回这两个文件接进导航 ——
它们引用的 6 条字符串已删、`TimelineItem` 与统计页的聚合口径也可能已经不同。
