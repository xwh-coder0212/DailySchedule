# DailySchedule 优化方案与可行性评估

> 生成时间：2026-10-03 深夜（真机验收第二轮通过之后）
> 基线提交：`1fc1b35`（`origin/main` 已同步）
> 口径说明：文中**「实测」**= 本轮真的跑过命令或读过源码得到的事实，附命令或文件路径；
> **「估算」**= 我对工作量的判断，属主观，不是实测值。

---

## 零、A 档执行状态（2026-10-04 更新）

| 项 | 状态 | 证据 |
| --- | --- | --- |
| A1 清死依赖 | **已完成** | `work-runtime-ktx` 从 `app/build.gradle.kts` 与 `gradle/libs.versions.toml` 移除（连同 `work` 版本条目）；Glance 两行按本文件建议保留给 V2。**执行时发现本文件写错了一处**：那行显式声明并不是「纯冗余」—— 实测 `releaseRuntimeClasspath` 显示 `glance-appwidget:1.2.0 → glance:1.2.0` 传递带入的是 `work-runtime(-ktx):2.7.1`，删掉等于把 WorkManager 从 2.11.0 降到 2.7.1。今天无影响（两者都没被引用，R8 全裁），但 V2 做小组件时不能沿用 2.7.1 |
| A2 release 签名 + 首次构建 | **已完成** | `:app:assembleRelease` 首次成功，26.93 MB → **7.03 MB**；`apksigner verify` v2 通过，签名者 `CN=DailySchedule, OU=Personal, O=xwh-coder0212, C=CN`，证书 SHA-256 `1ac90d20…398fd645`。**踩坑记录**：`apksigner` 报 `JAVA_HOME is set to an invalid directory: D:\idea\IntelliJ IDEA 2025.2.4`，须先 `export JAVA_HOME=D:/toolchain/jdk21/jdk-21.0.12.1+1`。详见 `docs/quality/release-build-first-run-2026-10-04.md` |
| A2b release 包端到端 | **已完成（模拟器 + 真机双验）** | 模拟器侧（Android 14，`system-images;android-34;google_apis;x86_64`）：JSON 导出 → 恢复 → 撤销，`verify_backup.sh` 退出码 0、崩溃 0 命中、冷启动 415 ms。**真机侧（2026-10-04 补做）**：`verify_device.sh all` 安装 `Success`、冷启动 `Status: ok / TotalTime 195 ms`、崩溃与 ANR **各 0 命中**；`verify_backup.sh --replace` **退出码 0 / 0 项失败**（SAF 导出 → 二次确认 → 替换恢复 → 撤销全通）；设备库 `counts={projects:1, categories:7, sessions:0, expenses:1}`，导出文件 `appVersion = 1.0.0`（无 `-debug` 后缀，是「本次产出属于 release 包」的直接凭证）。真机验收用的是 `assembleRelease` 输出的通用 APK |
| A3 验收遗留两项转单测 | **已完成** | 新增 `StatsNumbersEndToEndTest`（4 个用例）+ `ManualBadgeMixTest`（3 个用例）。全量 **213 通过 / 0 失败 / 0 跳过**，24 个测试类（原 206 / 22） |
| A4 加 CI | **已完成（已在 GitHub 上跑绿）** | run `37187264545`（sha `5e6fb89`）两个 job 全绿，总 9 分 13 秒：`quality` 292 秒、`release` 234 秒（R8 与资源压缩真跑，含 1 MB 体积下限守卫）。首次运行是红的，修掉三处：`android-actions/setup-android@v3` 在 Node 24 上会碎（改为不依赖第三方 Action，直接用 runner 预装的 `ANDROID_HOME`）、`platforms;android-37` 包名不存在（应为 `platforms;android-37.0`）、`gradlew` 缺可执行位。详见 `docs/quality/ci-first-green-2026-10-04.md`。**此后每次 push 自动触发**；最新 run `37201131583`（sha `da5bc98`）同样两 job 全绿，12:09:19Z → 12:16:03Z 共 6 分 44 秒，job 名 `单测 + ktlint 门禁 + Lint` 与 `release 构建（R8 与资源压缩真的跑一次）`，其中 `还原发布密钥` 步骤为 **skipped**（未配 secret，符合设计） |
| A4b 配签名 secret | **未做** | 四个 `ANDROID_*` secret 未配，CI 上的 release job 跑的是 unsigned 包（设计如此，不阻塞） |

**执行过程中推翻的两个判断**（这两条比结论本身更有用）

1. **「R8 把 `$$serializer` 裁掉了」是错的。** release dex 里 grep 不到任何
   `Lcom/dailyschedule/app/**serializer;`，看起来像被删。真相是**只改了名**
   （`BackupDocument$$serializer -> tg`），`mapping.txt` 里白纸黑字，`INSTANCE` 字段也在。
   grep dex 找类名，在混淆过的包里本来就不成立。
2. **「枚举常量字段被改名会把 JSON 里的枚举值写坏」也是错的。** `mapping.txt` 显示
   `SessionStatus RUNNING -> f`，但 `dexdump` 反汇编显示 `<clinit>` 里传给
   `java.lang.Enum.<init>` 的名字字符串**仍是 `"RUNNING"`**，而 `Enum.name()` 返回的就是它。
   四个枚举逐一核对过。

  真正的兜底不是 `app/proguard-rules.pro` 里手写的那几条，而是
  `kotlinx-serialization-core-jvm:1.9.0` 自带的 consumer R8 规则
  （`META-INF/com.android.tools/r8/kotlinx-serialization-r8.pro`），AGP 自动应用。
  这一点在 `mapping/release/configuration.txt`（R8 的已解析配置）里能直接看到。

**执行过程中撞到的两个环境/工具坑**

1. **`--rerun-tasks` 加在整个任务图上会触发 lint/KSP 竞态。**
   `lintAnalyzeDebugUnitTest` 把 `app/build/kspCaches/release/backups/java` 当成 Java 源根，
   而 `kspReleaseKotlin` 正在换掉这个目录，lint 于是读到
   `FileNotFoundException ... AppStartupUseCase_Factory.java (系统找不到指定的路径。)`。
   lint 自己的报错文案就写着 "this is a bug in lint or one of the libraries it depends on"。
   做法：`--rerun-tasks` 只给 `testDebugUnitTest`，lint 单独一次且任务图里不含 release 任务。
   本文件的 A3 验证方式写的是「只给单测加」，这一点原本就写对了。
2. **本机沙箱里 Gradle 守护进程写构建缓存会被拒**（`build-cache-1\*.part (拒绝访问。)`）。
   同一目录同一套动作由 Bash 启动的 JVM 做完全成功，判据是进程身份，不是工程问题。
   绕过方式是 `--no-build-cache`，只损失构建速度。
3. **模拟器在沙箱内启动会立刻崩，在沙箱外正常**（同一类进程身份问题）。
   取证：崩溃库 `%TEMP%\AndroidEmulator\emu-crash-37.2.12.db\reports\*.dmp`，
   自写 minidump 解析器读出异常码 `0xE06D7363`（MSVC 未捕获的 C++ 异常），
   dump 的模块列表里有沙箱注入的 `tsbx.dll`。另外 `emulator.exe` 只是 launcher，
   拉起后端 `qemu-system-x86_64-headless.exe` 后自己就退（退出码 127）——
   所以「启动命令返回了」不等于模拟器还活着，必须让模拟器和后续 adb 操作活在同一棵进程树里。

---

## 一、结论摘要

| 档 | 项 | 工作量【估算】 | 风险 | 前置依赖 |
| --- | --- | --- | --- | --- |
| A1 | 清掉零引用的死依赖（删 `work-runtime-ktx`；Glance 留待 V2） | S（半天内） | 低 | 无 |
| A2 | **release 签名配置 + 首次 release 构建验证** | S–M | **中**（keystore 保管） | 无 |
| A3 | 验收遗留 2 项转成单测 | S | 低 | 无 |
| A4 | 加 CI（GitHub Actions） | M | 中（首次调环境） | A1、A2 先做完 |
| B1 | Baseline Profile | M | 低 | 先测冷启动基线 |
| B2 | 备份文件版本兼容矩阵 | S | 低 | 无 |
| B3 | 导出文件校验和 | S | 低（需设计成可选字段） | 无 |
| B4 | 数据体检项扩展 | S | 低 | 无 |
| B5 | 依赖升级 / 清 Lint warning | M–L | 中 | 不与其他改动混轮 |
| C1 | 桌面小组件 | M–L | 低 | 与 A1 互斥，需先拍板 |
| C2 | 通知快捷操作（再来一轮） | S | 低 | 无 |
| C3 | 统计维度扩展（项目对比 / 上月同期） | M | 低 | 无 |
| C4 | 多语言 | M | 低 | 仅在要上架时做 |
| C5 | Excel 增加汇总 sheet | S | 低 | 无 |

**最该先说的一条**：目前 `assembleRelease` **从未成功跑过**，且没有签名配置。
也就是说，App 现在能跑、能验收，但**产不出一个别人能装的包**。
这是从「能跑」到「能分发」的最后一公里，也是 A2 排在第一位的原因。

---

## 二、现状基线（实测）

> ⚠️ 这一节是 **2026-10-03 的快照**，其中「无 CI」「release 从未构建」等条目
> 已在 2026-10-04 改变。最新状态看上面的「零、A 档执行状态」。

| 项 | 实测值 | 来源 |
| --- | --- | --- |
| 主源码规模 | 125 个 `.kt`，14108 行（含测试） | `find app/src/main -name '*.kt'` / `wc -l` |
| 分层 | core 30 / domain 34 / data 29 / feature 22 / timer 4 / navigation 3 / di 1 | `find ... -maxdepth 4 -type d` |
| 单测 | 22 个测试文件、**206 个 `@Test`** | `grep -c '@Test' app/src/test` |
| 仪器测试 | **0**（`app/src/androidTest` 无源码） | `ls -R app/src/androidTest` |
| CI | **不存在**（无 `.github`） | `ls -R .github` → 无此目录 |
| 构建产物 | 只有 `app/build/outputs/apk/debug/app-debug.apk`（28,235,569 字节） | `find app/build/outputs -name '*.apk'` |
| 门禁覆盖的构建类型 | 只有 `assembleDebug`；`quality-gate-final.txt` 全文**无 "release" 字样** | `grep -in release docs/quality/quality-gate-final.txt` |
| 签名配置 | **无**（`signingConfigs` / `storeFile` / `key.properties` 在构建脚本里零命中） | `grep -rn 'signingConfig\|storeFile\|keystore' *.gradle.kts` |
| R8 / 资源压缩 | `isMinifyEnabled = true` / `isShrinkResources = true`（release），但从未执行 | `app/build.gradle.kts:34-41` |
| 模块 | 单模块，`include(":app")` | `settings.gradle.kts` |
| SDK | minSdk 26 / targetSdk 36 / compileSdk 37 | `gradle/libs.versions.toml` |
| 语言 | 仅中文一套 `values/strings.xml`，212 条 | `ls app/src/main/res/values*` |
| 网络权限 | **零**（刻意不声明 INTERNET） | `AndroidManifest.xml` 注释段 |
| 数据库 schema | v1、v2 两份已入库，含 `Migration1To2Test` | `app/schemas/.../1.json`、`2.json` |

### 由此暴露的三个缺口

1. **release 链路完全未验证**。R8 开着、资源压缩开着，但一次都没真正跑过。
   混淆规则里对 Room / kotlinx.serialization / Hilt 都写了 keep 规则
   （`app/proguard-rules.pro`），**写了不等于对** —— 序列化字段名一旦被改名，
   导出的 JSON 键就变了，老备份直接读不回来。这条只能靠 release 包实跑验证。
2. **门禁没有执行者**。`docs/quality/README.md` 自己就写着：
   「项目没有 CI，这些日志就是『门禁确实跑过』的唯一凭据」。
   换句话说，门禁成立的前提是「我记得跑」，而不是「系统会跑」。
3. **3 个零引用依赖**（详见 A1）。其中 Glance 的两个包体积不小。

---

## 三、A 档：立刻可做

### A1 清理零引用的死依赖

**实测证据**

```
$ grep -rn 'glance' app/src app/build.gradle.kts gradle/libs.versions.toml settings.gradle.kts
app/build.gradle.kts:107:    implementation(libs.glance.appwidget)
app/build.gradle.kts:108:    implementation(libs.glance.material3)
gradle/libs.versions.toml:21:glance = "1.2.0"
gradle/libs.versions.toml:63:glance-appwidget = { ... }
gradle/libs.versions.toml:64:glance-material3 = { ... }

$ grep -rn 'WorkManager\|CoroutineWorker\|OneTimeWorkRequest' app/src
(仅依赖声明，无调用)
```

即：Glance 只出现在构建脚本与版本目录里，`app/src` 下**一行代码都没引用**，
`AndroidManifest.xml` 里也没有 `AppWidgetProvider` / `GlanceAppWidgetReceiver`，
`strings.xml` 里没有「小组件」相关文案。`work-runtime-ktx` 同理，
唯一一次出现是 `BootCompletedReceiver.kt` 的注释里提到「Worker / JobScheduler」，
是说明文字，不是调用。

**做法**：本次只删 `app/build.gradle.kts:106` 那一行 `work-runtime-ktx`，
并同步删版本目录里 `work` 的 version 与 library 条目。Glance 的两行按下面的建议保留。

**风险：低**。要留意的是 `glance-appwidget` 会传递带入 `work-runtime` ——
删掉显式声明后如果仍然编译通过，就反证了工程里没有对它的直接调用。

**验证**：`./gradlew :app:testDebugUnitTest` 通过。体积收益要在 A2
（release 构建首次跑通）之后才能量到，因为 debug 包不混淆，量不准。

**我的建议（默认按这条走，你要改就说一声）**

既然 README 的版本规划里桌面小组件已经占着 V2 的位置，就按「保留 Glance、
只删 `work-runtime-ktx`」处理：

- `app/build.gradle.kts:106` 的 `work-runtime-ktx` —— **删**。没有任何代码调用它。
  **但本文件原来说的「纯属冗余」是错的**（2026-10-04 执行时实测纠正）：
  那行声明一直在充当**版本下限**。删之前 `releaseRuntimeClasspath` 上解析到的是
  显式声明的 2.11.0；删之后只剩 `glance-appwidget:1.2.0 → glance:1.2.0` 带来的
  `androidx.work:work-runtime:2.7.1` / `work-runtime-ktx:2.7.1`。也就是降了 4 个 minor。
  今天没有影响 —— 全工程不用 WorkManager，Glance 也一行没引用，R8 把两者一起裁掉，
  产物里什么都没有。V2 做小组件时**不要沿用 2.7.1**（2022 年的版本，
  早于 Android 14 的前台服务类型要求），要么写回显式依赖，要么用 constraint 顶上去。
  真要写 Worker 时再加回来，成本是三行。
- `app/build.gradle.kts:107-108` 的两行 Glance —— **留**。它占的是 V2 的位置，
  删了再装回来虽然几乎零成本，但对一个已经规划在 V2 的功能来回折腾没有收益。
  真正该做的动作是「别让它一直闲着」，也就是把 C1 排进 V2。

反过来的处理方式（两行都删）只在一种情况下更优：你决定**不做**桌面小组件。
那时删掉能少一份升级负担，需要同步改 `README.md` 的 V2 范围。

---

### A2 release 签名配置 + 首次 release 构建验证（优先级最高）

**实测证据**

- `app/build.gradle.kts` 里没有任何 `signingConfigs` 块；`grep -rn 'storeFile\|key.properties\|keystore'` 在
  `app/build.gradle.kts`、`build.gradle.kts`、`gradle.properties` 三处全部零命中。
- `app/build/outputs/apk/` 下只有 `debug/app-debug.apk`，没有 `release/`。
- `docs/quality/quality-gate-final.txt` 里出现的打包任务是 `:assembleDebug`，全文不含 "release"。

**含义**：现在跑 `./gradlew assembleRelease`，产出的是 `app-release-unsigned.apk`
（Android 的约定，未配置签名时不签名）——**这个包在真机上装不上**。
同时，R8 与资源压缩虽然一直开着，但因为 release 从未构建，
「混淆后应用还能不能正常工作」这件事目前是**未知状态**，不是「已验证通过」。

**做法（四步）**

1. 用 `keytool -genkeypair` 生成发布用 keystore，**放在仓库之外**。
   `.gitignore` 里已经有 `*.jks` / `*.keystore` / `key.properties` / `release/`
   四条忽略规则，不用再改。
2. 写 `key.properties`（同样在仓库外或被忽略的路径），存 keystore 路径与两组口令。
3. 在 `app/build.gradle.kts` 加 `signingConfigs.create("release")`，并且
   **只在 `key.properties` 存在时才应用** —— 否则没有密钥的环境（比如 CI、
   或者你换了台机器）连 `assembleDebug` 都会被拖挂。
4. 跑 `./gradlew :app:assembleRelease`，用 `apksigner verify --print-certs` 确认已签名。

**风险：中，且风险点不在技术而在资产保管**
keystore 一旦丢失，**所有已经分发出去的包都永远无法升级**（Android 用签名区分
「同一个应用的新版本」和「另一个应用」）。只能换 applicationId 重装，
用户数据要重导。所以：keystore 至少离线备份两处。

**验证（这一步不能省）**
签名验过之后，把 release 包装到真机上，**跑一遍 JSON 导出 → 导入**。
这是唯一能证明 R8 没把序列化模型改坏的方式。理由：
`proguard-rules.pro` 里对 `@Serializable` 与 `Companion.serializer()` 写了 keep 规则，
但序列化字段名是编译期常量，真正的风险是 R8 把生成的 `$serializer` 类裁掉或改名，
导致运行时退化成反射失败 —— 这种情况**只有实跑才暴露**。

---

### A3 把验收遗留的两项转成单测

第二轮验收报告末尾自认了两项未覆盖。这两项**都不该用 UI 自动化去补**，
用单测覆盖更快、更稳、且能长期回归。

| 遗留项 | 更好的补法 | 依据 |
| --- | --- | --- |
| 统计页三图与详情面板的**数值**正确性 | 用 Robolectric + 真实 Room 造已知分布 → 走 UseCase → 断言聚合值。**不进 UI 自动化** | `core/stats/StatsAggregator.kt` 已是纯函数层，`StatsAggregatorTest` 已有 16 个用例；`Migration1To2Test` 证明「Robolectric + 真 Room」这条路在本工程跑得通 |
| 同一待办下**多条补录混排**时角标是否错位 | 把「会话来源 → 是否显示角标」抽成一个纯函数，对混排序列做参数化断言 | 现在角标逻辑在列表 Composable 里，UI 层不好断言 |

**可行性：高**。测试依赖（robolectric 4.16 / room-testing / truth / turbine / mockk）
都已在 `app/build.gradle.kts` 的 `testImplementation` 里，不需要新装任何东西。
`testOptions.unitTests.isIncludeAndroidResources = true` 也已打开，Robolectric 可跑。

**风险：低。**

---

### A4 加 CI（GitHub Actions）

**实测证据（2026-10-03 时的事实）**：`.github` 目录不存在；`docs/quality/README.md` 明写「项目没有 CI」。
（2026-10-04 已写出 `.github/workflows/ci.yml`，见「零、A 档执行状态」。）

**做法**：`.github/workflows/ci.yml`，`ubuntu-latest` + **JDK 21**
（`app/build.gradle.kts` 里 source/target 都是 `VERSION_21`，`jvmTarget = JVM_21`）。
分两个 job：

- `quality`：`:app:testDebugUnitTest` → `:app:ktlintGate` → `:app:lintDebug`
- `release`：`:app:assembleRelease`，仅在仓库 secrets 里配了 keystore 时才跑，
  没配就跳过（不要让它变成红的假失败）

**两个容易踩的点**

1. `local.properties` 不入库（`.gitignore` 里有），CI 上靠 runner 自带的
   `ANDROID_HOME` + `sdkmanager` 装 `compileSdk 37` 对应的平台包。
2. `gradle.properties` 里有一行显式把配置缓存关掉了：

   ```
   org.gradle.configuration-cache=false   # 注释：首次构建，Hilt/KSP 在配置缓存下易报不兼容
   ```

   CI 上**沿用同一设置**，不要为了「跑得快」单独在 CI 打开 —— 那会造出
   「本地过、CI 挂」或反过来的假象，比不跑 CI 更糟。

**风险：中**（首次调环境通常要来回几次）。**收益**：把「门禁成立靠我记得跑」
变成「每次推送自动跑」，这是 B 档所有改动的前置保障。

**顺序**：放在 A1、A2 之后。让 CI 第一次就跑在干净的基线上，
否则第一次 CI 就会带一堆与你本次改动无关的红。

---

## 四、B 档：需要设计，收益明确

### B1 Baseline Profile —— 但先量再投

**现状**：`isMinifyEnabled = true` 但工程里没有 `baseline-prof.txt`；
验收记录里冷启动约 1s。

**做法**：加 `androidx.baselineprofile` 插件 + 一个单独的 `baselineprofile` 模块
（现在 `settings.gradle.kts` 是单模块，要多 `include(":baselineprofile")`），
脚本走「冷启动 → 待办页 → 统计页 → 记账页」，产出 `baseline-prof.txt`。

**为什么建议先量后投**：Baseline Profile 对 **Compose 首帧**的改善通常可观，
但这个判断来自行业经验，不是本工程的实测。而验收时的库只有 1 条会话，
在这种空数据下的启动耗时**不能代表**你用了三个月之后的启动耗时。
所以先做一件事：拿现在的库灌一批数据（比如 200 条会话 + 100 笔消费），
再用 `adb shell am start -W` 量三次冷启动取中位数。**量完再决定投入**。

**风险：低–中**（多一个模块；生成 profile 需要一台设备或模拟器）。

---

### B2 备份文件的版本兼容矩阵

**现状**：`core/transfer/BackupValidator.kt` 会校验「结构版本」，
但目前 schema 只有 v1 → v2 一档，**没有断言过「v3 的应用读 v1 的备份会怎样」**。

**要定的规则**（现在定，比以后定便宜）：
同主版本向上兼容直接读；跨主版本明确拒绝并给出人话提示，**不做猜测式字段补齐**。

**做法**：把规则写进 `BackupValidator` 的 KDoc，然后加一组参数化测试覆盖
（备份版本 × 当前版本）矩阵。纯函数 + 单测，成本很低。

**风险：低。**

---

### B3 导出文件加校验和

**现状**：`BackupValidator` 校验的是「声明条数 vs 实际条数」。
这能查出**截断**（文件写到一半），但查不出**位翻转或静默篡改** —— 条数依然对得上。

**做法**：JSON 顶层加一个 `checksum`（对 payload 规范化后算 SHA-256），导入时先验。

**关键设计约束**：这会改备份文件的结构。为避免已导出的老文件读不回来，
把 `checksum` 设计成**可选字段**：缺失时跳过校验、照常导入。
不要为了这个去升 schema 主版本 —— 那是拿一件小事去破坏兼容性。

**风险：低。**

---

### B4 数据体检项扩展

**现状**：设置页已有「数据体检」，会检查会话的时长不变量
（计时类是 `end − start − accumulatedPause == durationMs`，补录类是 `end − start == durationMs`）。

**可加三项**，都是纯函数层能做的：

- 「孤儿外键」：`expense.projectId` / `focusSession.projectId` 指向不存在的项目
- **`sqlite_sequence` 与各表 `max(id)` 是否一致** —— 这次验收是手工查的，
  它正是「导入后新增记录会不会撞 id」的关键，值得固化成体检项
- 跨天会话的归属检查（会话横跨日切时刻时算哪天）

**风险：低。** 第 2 项尤其值：它这次的验证方式是手写 SQL 比对，
固化成体检项之后每次导入都能自查。

---

### B5 依赖升级 / 清掉剩余 Lint warning

**现状**：`lintDebug` 是 0 error / **27 warning**，全是
`NewerVersionAvailable` / `GradleDependency` / `OldTargetApi` 一类，**没有代码缺陷**。

**做法与取舍**：这类 warning 的消法只有升级。但当前 AGP 9.3 / Kotlin 2.3.21 /
Compose BOM 2026.08 已经是相当新的组合，`targetSdk 36` 对应 Android 16，
升到 37 收益不明确。
**建议：不与其他改动同一轮做**，单独开一轮，改完重跑全部门禁 + 重做一次真机验收。
混在一起的话，一旦出问题分不清是升级引入的还是别的改动引入的。

**风险：中。**

---

## 五、C 档：产品增强（需要你拍板）

### C1 桌面小组件

Glance 的依赖**已经躺在构建脚本里**（零引用，见 A1），
而且 `README.md:266` 已经把「桌面小组件」写进 **V2** 范围 —— 也就是说当初留了位置但没实现。

做的话成本主要在数据管道：小组件跑在独立进程，拿不到主进程的内存状态，
要走 `GlanceAppWidget` + 一个轻量数据源（DataStore 已引入，
可以先读一份「今日专注时长 / 今日支出」的缓存，由主进程在数据变化时写入）。

**这是一个岔路，得先拍板**：
- 近期要做 → 保留 Glance 依赖，走 C1 补实现
- 近期不做 → 走 A1 删掉依赖，以后要做再加回来（重新加依赖的成本几乎为零）

---

### C2 通知里的快捷操作

番茄到点的通知已经有了。可加一个「再来一轮」按钮，直接重启一轮计时，
省掉「解锁 → 找到 App → 点开始」三步。**工作量 S，风险低。**

### C3 统计维度扩展

现在统计页有环形（占比）/ 柱状（周分布）/ 折线（日 / 月趋势）三图。
可加「项目对比」与「上月同期」。注意 `StatsAggregator` 是纯函数层，
加维度＝加纯函数 + 单测 + 一个 Canvas 组件，**分层是干净的，不会牵动 UI 之外的东西**。

### C4 多语言

现在只有中文一套 strings（212 条）。**只有在你要上架、或给非中文用户用时才需要做。**
只给自己用就不做。

### C5 Excel 报表增加汇总 sheet

现在 Excel 是两张工作表（验收记录提到「两张工作表、表头顺序正确、
合计公式 `SUM` / `SUMIF` 正确」）。加一张「汇总」sheet 的成本很低，
`XlsxWriter` 是手写的、可控。

---

## 六、D 档：明确不做（附理由）

| 项 | 不做的理由 |
| --- | --- |
| 云同步 / 多账户 / 社交 / 锁机 / 银行同步 | 立项时已明确排除（`CHANGELOG.md` 末段有记录） |
| **任何引入网络的功能** | `AndroidManifest.xml` **刻意不声明 `INTERNET`** —— 这是从系统层面兑现「数据不外流」，不是「我们承诺不上传」。一旦引入网络，这个卖点立刻失效，且不可逆（权限一旦加上，用户就会怀疑） |
| 备份文件加密 | App 已设 `allowBackup="false"`，导出文件是用户**主动**放到自己的存储/网盘上的。加密会把「忘记密码 = 数据全丢」的风险引进来，而它防的威胁（别人拿到你的文件）在本机场景下并不成立。**结论：不做**，但要在导出页文案里讲清「导出的文件是明文的，放到哪里由你负责」 |

---

## 七、建议的推进顺序（含依赖）

```
① A1 清死依赖：删 work-runtime-ktx，Glance 留待 V2（按 README 的 V2 范围）  ← 不依赖任何项   ✅ 2026-10-04
② A2 release 签名 + 首次 release 包实跑验证        ← 门槛项，排最前                        ◐ 构建与签名已通，真机端到端待设备
③ A3 验收遗留两项转单测                            ← 不依赖任何项，可穿插                     ✅ 2026-10-04
④ A4 加 CI                                        ← 依赖 ①②③ 完成（让 CI 首跑在干净基线）  ◐ 文件已写，待推送实跑
⑤ B2 / B3 / B4                                    ← 三者都在纯函数层，可并行，不碰 UI        ← 下一个起点
⑥ B1 先量冷启动基线 → 再决定投不投 Baseline Profile
⑦ B5 单独一轮做依赖升级
⑧ C2 / C5（低成本增强）→ C3（统计维度）→ C4（仅在需要上架时）
```

**下一步的岔路（需要你定）**

A 档只剩下两件收尾的事，都不需要写新代码：

- **推送到 `xwh-coder0212`**，让 A4 的 CI 真的跑一次。这一步同时会验证
  `quality` 与 `release` 两个 job，也是「CI 首跑在干净基线」这个前提的兑现。
  联网/凭据的前置问题在真机验收那一轮已经查过（本机 `GIT_TERMINAL_PROMPT=0`
  且走 `127.0.0.1:31180` 代理，`curl` 的结论不能直接套到 `git` 上）。
- **插上手机做 A2b**：装 release 包，跑一次 JSON 导出 → 导入。
  这是唯一还没被证明的一环，而它恰好是「换手机不丢数据」这个核心承诺的最后一米。

两件互不依赖，可以先做任一件。⑤ 及之后的 B 档随时可以开，它们都在纯函数层，
不碰 UI，也不会与上面两件事冲突。

**为什么 A2 排最前**：它是唯一一个「不做就没法把 App 给别人用」的项，
而成本只有「加一段构建脚本 + 生成一个 keystore」。

**为什么 A4（CI）必须等前两项**：CI 的价值是「以后不用记得跑门禁」。
如果基线本身还带着未验证的 release 构建，第一次 CI 就会红一片，
你会开始习惯性忽略红色 —— 那 CI 就废了。

---

## 八、每项的验证方式（可核验，不接受「应该没问题」）

| 项 | 验证命令 / 动作 | 通过标准 | 2026-10-04 实况 |
| --- | --- | --- | --- |
| A1 | `./gradlew :app:assembleRelease :app:testDebugUnitTest` | 编译通过 + 单测全过 | **通过**。删掉 `work-runtime-ktx` 后依赖树照常解析，213 个单测全过 |
| A2 | `./gradlew :app:assembleRelease` → `apksigner verify --print-certs` → `adb install -r` → **真机跑一次 JSON 导出后导入** | 签名信息显示正确 CN；导出 JSON 能被 release 包读回 | **四步全通过**。签名 CN 与 SHA-256 已核；真机 `verify_backup.sh --replace` **退出码 0 / 0 项失败**，导出文件 `appVersion=1.0.0` 被 release 包读回、`counts={projects:1, categories:7, sessions:0, expenses:1}` |
| A3 | `./gradlew :app:testDebugUnitTest --rerun-tasks` | 新增用例全过；**必须带 `--rerun-tasks`** | **通过**。24 个测试类 / 213 用例 / 0 失败，逐类明细归档在 `docs/quality/quality-gate-tests.txt` |
| A4 | 推送后看 GitHub Actions 页面 | 两个 job 都是绿；release job 在无 secret 时显示为 skipped 而非 failed | **通过**。最新 run `37201131583`（sha `da5bc98`）两 job 全 绿；`还原发布密钥` 步骤显示 **skipped**（非 failed），与通过标准一致 |
| B1 | 灌数据后 `adb shell am start -W` 三次取中位数 → 加 profile 后再测三次 | 改善幅度可复现，不是单次抖动 | — |
| B2 | `./gradlew :app:testDebugUnitTest` | 版本矩阵每个组合都有断言 | — |
| B3 | 同上 | 含「校验和缺失仍能导入」与「校验和不匹配被拒绝」两个方向的用例 | — |
| B4 | 设置页手动触发体检 + 单测 | 人为造一条 `sqlite_sequence` 落后的库，体检能报出来 | — |
| B5 | `./gradlew clean check :app:assembleRelease` | 0 error；warning 数量下降 | — |

**一条实测补充**：A3 那条「只给单测加 `--rerun-tasks`」的写法原本就写对了 ——
把 `--rerun-tasks` 摊到整个任务图上会撞上 lint 与 KSP 的竞态（见 `docs/quality/README.md`
的「`--rerun-tasks` 与 lint 的竞态」一节）。

**一个通用注意**：`testDebugUnitTest` 在输入未变时会被 Gradle 判为 UP-TO-DATE
**而不执行**。只看 `BUILD SUCCESSFUL` 可能什么都没跑。
`docs/quality/README.md` 里已经记过这一点，后续每次报测试结果都要带 `--rerun-tasks`。

---

## 九、与备考节奏的配合

你同时在准备考研。上面这些按「能不能拆成一次坐下就能收尾的单元」来分：

- **可碎片化（每次 1 项，跑完门禁就停）**：A1、A2、A3、B2、B3、B4、C2、C5
  —— 每一项的验证都只需要 `./gradlew` 加一条命令，不需要连续大块时间。
- **需要整块时间**：A4（调 CI 环境，通常要来回几次）、B1（要生成 profile 并反复测启动）、
  B5（升级 + 重跑全套 + 重做真机验收）。

**建议**：A2 单独找个时间段做完 —— 它牵涉 keystore 的生成与备份，
中途打断容易把口令或路径搞乱。
