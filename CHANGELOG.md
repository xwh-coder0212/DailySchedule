# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 的格式。
尚未发布任何正式版本，`versionName` 目前为 `1.0.0`（`versionCode = 1`）。

## [未发布]

### 推送回路打通，CI 在 da5bc98 上通过（2026-10-04 收尾）

上一节的改动提交为 `da5bc98`（8 files changed / 103 insertions / 55 deletions）。
推送时经 IP 池代理**连续三次**返回 `error: 403` —— 与此前所有失败模式
（`Recv failure`、`schannel: server closed abruptly`、`could not read Username`）都不同。
改用直连后**一次成功**：`0dbfdf2..da5bc98  main -> main`，`PUSH_EXIT=0`。

**Verified**

- 远端引用核对：本地 `HEAD` 与 `origin/main` 同为
  `da5bc9868ae72b0fafa5b2d307093764fed1fbe2`，`ahead/behind = 0 0`。
- CI run `37201131583`（`head_sha = da5bc98…`）**conclusion = success**：
  job `单测 + ktlint 门禁 + Lint` 与 `release 构建（R8 与资源压缩真的跑一次）` 双绿，
  12:09:19Z → 12:16:03Z 共 **6 分 44 秒**；`还原发布密钥` 步骤为 **skipped**
  （未配 secret，符合设计，不是 failed）。

**Changed**

- 路线图文档里三处**已被本轮证伪**的状态标记按实测更正：
  A2「已完成（真机端到端除外）」→ **已完成**；A2b「真机仍未做」→ **已完成（模拟器 + 真机双验）**；
  A4「未做，需要先推到 xwh-coder0212」→ **通过**。第八节的 A2 / A4 实况列同步。
- 回填两个本轮踩到的坑：`apksigner` 需 `JAVA_HOME` 指向 JDK 21（默认指向 IntelliJ 目录会报
  `invalid directory`）；真机验收用的是 `assembleRelease` 输出的通用 APK。

**一条关于 CI 触发范围的实测**

- `on.push.branches: [main]` **没有 `paths` 过滤**，所以 docs-only 提交照样会跑完整两个 job
  （实测 run `37201754977` / sha `fd6c4bc`，2 分 36 秒，两 job 均 success；有 Gradle 缓存命中所以比首次快）。
- 推论：**不要在文档里用「最新一次 run」指代**，那会被下一次提交自己的 CI 顶掉。
  要指代就写死 `sha`，并用 `python D:/toolchain/wait_ci.py <owner/repo> <sha>` 以 `head_sha` 过滤。

**仍未覆盖**

- **403 的根因未定位**：代理在线、IP 池 32 个可用，但 HTTPS 层返回 403；同一时刻直连可通。
  属代理层状态，不是仓库或凭据配置问题，故未改任何配置。
- A4b 仍未做：四个 `ANDROID_*` secret 未配，CI 出的是 unsigned 包。

### 令牌层落到可见页面、release 包真机验收通过（2026-10-04）

上一节的判断里有一处是错的，真机验收时被证伪：上一节把 `HomeScreen.kt` 当「示范页」接入令牌，
但**全仓对 `HomeScreen` 零引用、导航图 `AppNavHost.kt` 里也没有 `Home` 目的地** ——
它自首次提交（`9076d24`）起就是死代码。上一节为它做的空态、tnum、`animateContentSize` 改造，
在 App 里**一帧都不会出现**。真正可见的是「待办 / 统计 / 记账」三个 Tab 页。

**Fixed**

- `Icons.Outlined.ReceiptLong` 被标记 deprecated（应改用 AutoMirrored 版本），
  改为 `Icons.AutoMirrored.Outlined.ReceiptLong`，release 编译警告数回到 0。

**Changed**

- 三个 Tab 页（`ProjectsScreen` / `StatsScreen` / `ExpenseListScreen`）的「一行灰字」空态
  换成 `DsEmptyState`（图标 + 标题 + 引导）；空态文案拆成标题与引导两条字符串，
  原来是「还没有项目。点右下角 + 创建第一个……」一句话兜底。
- **全工程手写 tnum 清零**：`fontFeatureSettings = "tnum"` 在 `DsText.kt` 之外 **0 处**。
  - 三个可见页 5 处：`ProjectsScreen` / `StatsScreen` / `ExpenseListScreen`。
    其中月度汇总卡保持原 `SemiBold` 字重不变 —— 用 `numeric(...).copy(fontWeight = SemiBold)`
    而不是会把字重改成 `Medium` 的 `numericEmphasis`。
  - 其余 11 处：`ProjectSessionsScreen` 5 / `DsCharts` 2 / `ProjectDetailSheet` 2 /
    `ExpenseEditScreen` 1 / `DataTransferScreen` 1。这 11 处形状一致
    （`X.copy(fontWeight = W, fontFeatureSettings = "tnum")`），且 `numeric()` 只设
    `fontFeatureSettings`，故均为严格等价改写；另删掉 `DsCharts` 与 `ExpenseEditScreen`
    里因此变成孤儿的 `FontWeight` import。
- 三张会变高的卡片加 `animateContentSize()`：正在专注卡、统计 `SectionCard`、月度汇总卡。
- 清掉 `ProjectsScreen` 因空态替换而变成孤儿的 `TextAlign` import。

**Verified**

- release 编译 `RELEASE_EXIT=0`，**编译警告 0 条**；APK 7,379,541 → 7,380,325（+784 字节，
  与新加的空态图标和两条新字符串相符）。
- 单测 **213 用例 / 24 类 / 0 失败 / 0 错误 / 0 跳过**；ktlint 门禁通过（基线 0 处存量）；
  Lint `0 error / 27 warning` —— 三项与改造前**逐项相同**。
- **release 包真机验收通过**（Redmi `2407FRK8EC` / Android 16 / HyperOS）：安装 `Success`、
  冷启动 `TotalTime 176 ms`、`FATAL EXCEPTION` 与 `ANR` 命中数 **0**；
  `verify_backup.sh --replace` 退出码 **0**、0 项失败（导出 → 选文件 → 二次确认 →
  替换并恢复 → 撤销上次导入全链路），导出文件 `appVersion = 1.0.0`
  （**不带 `-debug` 后缀**，直接确认本次结果属于 release 包）。

**仍未覆盖**

- `HomeScreen` / `HomeViewModel` 的去留未定：死代码属实，但「做首页」还是「删掉」是产品决策。
- 非槽位圆角 13 处、字号定稿、其余详情页接间距令牌 —— 等排版评审一并做。
  （手写 tnum 已清零，见上。）
- 真机 release 包的**逐字段库比对**仍做不到：依赖 `run-as`，而 release 包不可 debuggable。
  脚本已明确打「已跳过」而不是假装通过。

完整证据见 `docs/quality/ui-visible-pages-2026-10-04.md`。

### 界面令牌层收口（2026-10-04）

背景是「界面看着潦草」。先量化再动手，量出来的根因不是缺组件，而是**设计令牌层建了但没人用**：

| 探针 | 改造前 |
| --- | --- |
| `TabularNumbers` / `DisplayNumberStyle` / `TimerNumberStyle` 引用数 | 各 **0** |
| `MaterialTheme.shapes` 引用数 | **0**（而硬编码 `RoundedCornerShape(N.dp)` 有 34 处在消费端） |
| 页面手写 `fontFeatureSettings = "tnum"` | **20** 处 |
| dp 字面量 | **233** 处，含 `9/11/14/22/26/30.dp` 这类非 4dp 网格值 |
| `AnimatedVisibility` / `animateContentSize` / `Crossfade` | 各 **0** 个文件 |

即同一件事存在 20 份副本，改一处不会全局生效 —— 同类元素的间距、圆角、数字排版各自为政。

**Added**

- `core/ui/theme/DsSpacing.kt`：间距与尺寸令牌。9 个刻度（4dp 网格）+ 页面边距、
  列表行最小高度、最小触摸目标。
- `core/ui/theme/DsText.kt`：排版令牌收口。`numeric()` / `numericEmphasis()` 把
  手写 tnum 收敛成一处；`DsTypography` 作为唯一排版入口。
- `core/ui/theme/DsMotion.kt`：动效规范。三档时长（160 / 240 / 360ms）+ 进出曲线。
- `core/ui/component/DsEmptyState.kt`：统一空状态（图标 + 标题 + 说明 + 可选按钮）。
  此前全工程没有空状态组件，各页面自己拼「一行灰字」，用户分不清是没数据还是渲染失败。

**Changed**

- `Theme.kt`：`typography` 默认值由裸 `Typography()` 改为 `DsTypography`，排版有了唯一定稿入口。
- `HomeScreen.kt`：接入四层令牌 —— dp 字面量改 `DsSpacing`、圆角改
  `MaterialTheme.shapes.extraLarge`、4 处手写 tnum 改 `numeric()`、两处空态改 `DsEmptyState`，
  并给三张卡片加 `animateContentSize()`。
  **更正（同日真机验收时发现）**：`HomeScreen` 自首次提交起就是**死代码**，
  全仓零调用、导航图里没有 `Home` 目的地 —— 这些改动在 App 里一帧都不会出现。
  可见页面的处置见上一节。
- **21 处圆角等价收回令牌**：消费端 `RoundedCornerShape(12.dp)` → `MaterialTheme.shapes.medium`、
  `(16.dp)` → `Large`（11 个文件，共 19 处），另加 `HomeScreen` 的 2 处 `20.dp` → `extraLarge`。
  数值完全相等、视觉零变化。定义处 `ThemePackSpec` 明确排除，5 个文件的孤儿 import 一并删除。

**Verified**

- 编译 `COMPILE_EXIT=0`（0 警告）。
- 单测 **213 用例 / 24 类 / 0 失败 / 0 错误 / 0 跳过** —— 与改造前逐项相同。
- ktlint 门禁通过，基线 0 处存量，未引入新违规。
- Android Lint `0 error / 27 warning` —— 与改造前逐项相同。

单测与 Lint 数字一字未变，佐证「等价替换」的判断：没有改到任何被测试覆盖的行为。

**仍未覆盖**

- 本次改动当时没有经过视觉验证，只过了编译与门禁。卡点是 MIUI 的
  `INSTALL_FAILED_USER_RESTRICTED`（该开关不在标准 settings 命名空间内，adb 侧改不了；
  `am start` 拉起 MIUI 安装器会被其主动拒绝）。**该卡点已解除**：用户在设备上打开
  「USB 调试（安全设置）」后 `adb install -r` 直接成功，相关改动已在真机 release 包上目视核对。
- 字号一个都没改：三个大数字令牌（34 / 40sp）与页面在用的 Material3 基准
  （`displayMedium` 45sp、`titleLarge` 22sp）不一致，统一到哪套属排版决策。
- 其余 11 个页面未接入令牌；页面里 20 处手写 tnum 与非槽位圆角 13 处
  （`14 / 11 / 10 / 9 / 6 / 2.dp` 各若干，另有 1 处是 `topStart = 6.dp, topEnd = 6.dp`
  的命名参数写法，简单正则数不到）未动 —— 后者的归位必然改变视觉，需与排版评审一起定。
- 完整诊断与取舍见 `docs/quality/ui-foundation-2026-10-04.md`。

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

- **release 包运行期验证通过**（补做，同日）。静态证据证伪不了「跑起来会炸」——
  `Resources.NotFoundException`、`NoClassDefFoundError`、反序列化路径问题都只在运行时暴露。
  把 release 包装进 Android 14 模拟器（`system-images;android-34;google_apis;x86_64`）跑通
  JSON 导出 → 恢复 → 撤销：`verify_backup.sh 退出码 = 0`，logcat 崩溃特征 **0 命中**，
  冷启动 415 ms，导出文件里 `appVersion = 1.0.0`（不带 `-debug` 后缀，可直接确认产出属于 release 包）。
  证据见 `docs/quality/release-runtime-verification-2026-10-04.md`。
  这一轮顺带在验收脚本里查出 3 处「把某台设备/某种语言的偶然现象当成规则」的缺陷
  （拿上一轮残留文件当本次结果、只认中文「保存」不认 `SAVE`、抽屉导航点中面包屑导致
  后续滑动全落在抽屉上），均已修复。

- **CI 推上去第一次运行是红的，修掉三处后全绿**（run `37187264545`，sha `5e6fb89`，
  两个 job 全绿，总 9 分 13 秒：`quality` 292 秒、`release` 234 秒）。
  1. `android-actions/setup-android@v3` 是为 Node.js 20 构建的，而 runner 已把这类
     Action 强制迁到 Node.js 24 —— 它那一步直接 failure，之后的步骤全被 skip，
     所以问题 2 当时根本没机会执行。**改为不依赖这个第三方 Action**：runner 镜像
     本来就预装 `ANDROID_HOME=/usr/local/lib/android/sdk`，平台清单里已有
     `android-37.0` 与 `build-tools 37.0.0`，换成一段幂等 shell 定位 `sdkmanager`
     并补装。四个 Action 同时升到当前大版本（`using: node24`）。
  2. **`platforms;android-37` 这个包名不存在**。SDK 从 API 36 之后改成
     「主版本.小版本」命名，仓库里只有 `37.0` / `37.1` / `37.2`。
     已改为 `platforms;android-37.0`。
  3. **`gradlew` 在 git 里是 `100644`，没有可执行位**（Windows 上
     `core.fileMode` 不跟踪，入库时丢的），Linux runner 上 `./gradlew` 会
     `Permission denied`。已改成 `100755`。
  详见 `docs/quality/ci-first-green-2026-10-04.md`。
- `release` job 上的 release 包是 **unsigned**（四个 `ANDROID_*` secret 未配，
  「还原发布密钥」跳过）—— 设计如此：这一步盯的是 R8 有没有把东西裁坏，不是签名。
  签名包目前只在本地产出。

**仍未覆盖**

- release 包的**真机**端到端。上面那轮跑在模拟器上，覆盖的是 Android 14 / AOSP 路径；
  真机（Redmi / HyperOS / Android 16）侧目前只有 debug 包的凭据，release 包还没在真机上装过。
- 导出文件与数据库**逐 id 逐字段**比对没能在 release 包上做 —— 这一步依赖 `run-as`，
  release 包不可 debuggable。脚本现在会明确打「已跳过」而不是假装通过。
- CI 上的签名包（`A4b`）：要配四个 `ANDROID_*` secret 才会产出，目前未配。

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
