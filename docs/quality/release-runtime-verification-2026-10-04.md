# release 包运行期验证 —— 2026-10-04

## 这份文档回答什么

上一份 [`release-build-first-run-2026-10-04.md`](release-build-first-run-2026-10-04.md) 只回答了
「release 包装配得出来、签名对、R8 没裁掉该留的东西」。那是**静态**证据：`mapping.txt`、
`usage.txt`、`dexdump` 都读的是文件，不是运行中的进程。

静态证据能证伪「类被删了」「字段被改了」，但**证伪不了「跑起来会炸」**。例如：

- `Resources.NotFoundException` —— `isShrinkResources = true` 把某个只在反射里用到的
  字符串裁掉，编译期完全看不出来；
- `NoClassDefFoundError` / `NoSuchMethodError` —— 某个 `Provider` / `Factory` 被裁掉，
  要等首次调用才暴露；
- 反序列化路径上的问题 —— 序列化器类还在，但 `serializer()` 的某个分支指向被裁的符号。

本文补的就是那一层：**把 release 包装进 Android 运行时，真跑一遍导出与恢复。**

## 运行环境

| 项 | 值 |
| --- | --- |
| Runner | Android 模拟器，无窗口（`-no-window`），非真机 |
| AVD | `ds_release`（Pixel 7 规格，`-skin 1080x2400`，density 420） |
| 系统镜像 | `system-images;android-34;google_apis;x86_64`（Android 14 / SDK 34） |
| 加速 | WHPX（`emulator -accel-check` → `WHPX(10.0.26100) is installed and usable.`） |
| 被测包 | `com.dailyschedule.app`（release，无 `.debug` 后缀） |
| APK | 7,379,541 字节，SHA-256 `7812a54492d3ac2f559af5e823b9d6d38184071b57e78af2ce3dec0fe84017a6` |
| 系统语言 | 英文（en-US）—— 这一点在后面第三节变成了一个真实缺陷 |
| 驱动脚本 | `D:/toolchain/emu/verify_release_rt.sh`，内部复用 `D:/toolchain/verify_backup.sh --replace` |

## 结果

| 项 | 结果 | 依据 |
| --- | --- | --- |
| release APK 安装 | 通过 | `adb install -r` → `Success`；`versionCode=1 minSdk=26 targetSdk=36` |
| 冷启动 | 通过 | `am start -W` → `Status: ok`，`TotalTime` 415 / 447 / 756 / 760 ms（四轮均在 1 s 内） |
| 启动后前台窗口 | 通过 | `mCurrentFocus=com.dailyschedule.app/.MainActivity` |
| 崩溃与异常特征 | **0 命中** | logcat 全文 grep `FATAL EXCEPTION` / `ANR in` / `NoClassDefFoundError` / `NoSuchMethodError` / `ClassNotFoundException` / `SerializationException` / `Resources.NotFoundException` |
| 界面资源未被裁 | 通过 | 冷启动截图里「待办」「统计」「记账」「还没有项目。点右下角 + 创建第一个，比如「考研数学」。」全部正常渲染 |
| 导航到设置 / 数据导出与恢复页 | 通过 | 标题栏、返回按钮、四个按钮文案均可被 uiautomator 取到 |
| JSON 导出（SAF 写盘） | 通过 | 系统保存对话框弹出 → 保存 → 界面回报「已导出」→ 文件落在 `/sdcard/Download/` |
| 导出文件内容 | 通过 | `DailySchedule_备份_20261004_0740.json`，2331 字节；`format` / `schemaVersion` / `counts` 自洽、外键自洽 |
| **导出方是 release 包** | 通过 | 文件里 `appVersion = 1.0.0`（**没有** `-debug` 后缀）—— 这是"本次结果属于 release 包"的直接凭证 |
| 反序列化 + 替换恢复 | 通过 | 选文件 → 二次确认框（含「将写入：项目…」与清空警告）→ 替换并恢复 → 界面回报「已恢复」 |
| 撤销上次导入 | 通过 | 「撤销上次导入」入口出现且可用；撤销成功，撤销前自动又留了一份新快照 |
| JSON 自身结构 | 通过 | 脚本内 13 项校验全过 → `结论：…在设备上走通（0 项失败）` |

汇总：`verify_backup.sh 退出码 = 0`、`崩溃特征命中数 = 0`。

完整原始输出归档在 [`release-runtime-verify-log.txt`](release-runtime-verify-log.txt)，
其中第 4 节之后是 `verify_backup.sh` 的完整输出；冷启动截图见
[`release-runtime-coldstart.png`](release-runtime-coldstart.png)。

## 结论

R8 压缩 + 资源压缩之后的 release 包，在真实 Android 运行时里能装、能启、
能完成 JSON 的**写出**与**读回**两条路径，且 logcat 干净。
上一份文档里用静态证据推出的判断（序列化器被保留、枚举名常量没被改、
资源没被误裁）在这一层得到印证。

## 这次跑出来、顺手修掉的三个脚本缺陷

验收脚本是在真机（Redmi / HyperOS）上写成的，换到 AOSP 镜像上暴露出三处
「把某台设备/某种语言的偶然现象写成了规则」的地方。三处都会产出**错误结论**，
所以都在脚本里改了，不是绕过。

| # | 现象 | 根因 | 改法 |
| --- | --- | --- | --- |
| 1 | 第 6 步「校验导出的文件内容」**通过**，但该文件是上一轮残留 | 脚本用固定路径 `tmp/device-backup.json`，导出失败时旧文件还在，于是拿它去校验 —— 一条**假通过**（那份文件的 `appVersion` 是 `1.0.0-debug`） | 导出前先删本地副本；第 6 步在没有本次产出时明确打「已跳过」，不拿旧文件充数 |
| 2 | 保存对话框明明弹出来了，却报「保存对话框未出现」 | 系统对话框按钮文案跟**系统语言**走：中文机「保存」，英文机 `SAVE`。脚本只认中文 | 两种文案都认。注意两处匹配引擎不同、语法不能混用：`has()` 走 grep（BRE，交替要在竖线前加反斜杠），`node_xy()` 走 Python 正则（交替直接用竖线） |
| 3 | 抽屉导航点中了面包屑，抽屉没关，后面所有滑动都落在抽屉上，文件行永远点不到 | 那一屏里有**三个**写 `Downloads` 的节点：面包屑（`breadcrumb_text`，y≈252）、工具栏标题、抽屉项（`android:id/title`，y≈577）。只按 `text` 匹配会撞上第一个 | 匹配条件加上 `resource-id="android:id/title"`；点完检查抽屉是否仍开着，是则按一次返回收起；可点纵向下限由 600 下调为 300（AOSP 面包屑在 y≈250，不像 MIUI 吸顶到 600） |

第 3 条还连带修掉一处：单选选择器（`ACTION_OPEN_DOCUMENT` 不带 `allowMultiple`）
点一下文件就自动返回，**不存在「确定」这一步**，脚本却去找「确定」并记了一条失败 ——
凭空多出一项 fail。

## 过程中撞到的一个环境事实（不是应用缺陷）

模拟器在**沙箱内启动会立即崩溃**，在沙箱外正常。取证过程：

- 崩溃库 `%TEMP%\AndroidEmulator\emu-crash-37.2.12.db\reports\*.dmp`（3.7 MB）；
- 自写 minidump 解析器（`D:/toolchain/emu/mdump.py`）读出异常码 `0xE06D7363`
  = MSVC 未捕获的 C++ 异常；
- dump 的模块列表里有 `D:\AIAgent\WorkBuddy\resources\app.asar.unpacked\cli\vendor\sandbox\5.6.10\tsbx.dll`。

即沙箱把 `tsbx.dll` 注入进了 `qemu-system-x86_64-headless.exe`。这与前面
`gradle` 守护进程写 build-cache 报「拒绝访问」是同一类约束：**进程身份**，与路径、ACL 无关。

另外记一条与排查有关的坑：`emulator.exe` 只是 launcher，它把后端
`qemu-system-x86_64-headless.exe` 拉起后自己就退出（退出码 127）。所以
「启动模拟器那条命令返回了」**不等于**模拟器还活着 —— 一旦那条命令所在的任务结束，
进程树被回收，`adb devices` 立刻为空。正确的做法是让模拟器和后续所有 adb 操作
跑在同一个脚本、同一棵进程树里（`verify_release_rt.sh` 就是这么写的）。

## 仍未覆盖

| 项 | 状态 | 原因 |
| --- | --- | --- |
| **真机**（Redmi / HyperOS，Android 16）跑 release 包 | **未做** | 手上没有可连接的设备，`adb devices` 为空。本文的结论只覆盖「Android 14 / AOSP 路径」 |
| 导出文件与数据库**逐 id 逐字段**比对 | **未做** | 这一步依赖 `run-as`，release 包不可 debuggable，取不到 `databases/`。脚本现在会明确打「已跳过」而不是假装通过；要补这一层得用 debug 包再跑一次 |
| 通知权限下的实际通知行为 | 未做 | 本次是 `pm grant` 直接授予，没有走运行时弹窗 |
| Excel 导出 | 未做 | 本文只验 JSON 路径；Excel 路径见第一轮真机验收 |
| R8 后的性能/体积回归对比 | 未做 | 只有体积对比（28,235,569 → 7,379,541，26.1%） |
