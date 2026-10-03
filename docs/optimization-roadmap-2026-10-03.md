# DailySchedule 优化方案与可行性评估

> 生成时间：2026-10-03 深夜（真机验收第二轮通过之后）
> 基线提交：`1fc1b35`（`origin/main` 已同步）
> 口径说明：文中**「实测」**= 本轮真的跑过命令或读过源码得到的事实，附命令或文件路径；
> **「估算」**= 我对工作量的判断，属主观，不是实测值。

---

## 一、结论摘要

| 档 | 项 | 工作量【估算】 | 风险 | 前置依赖 |
| --- | --- | --- | --- | --- |
| A1 | 删掉 3 个零引用的死依赖 | S（半天内） | 低 | 先决定 C1 做不做小组件 |
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

### A1 删掉零引用的死依赖

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

**做法**：删 `app/build.gradle.kts:106-108` 三行，同步删版本目录里 `glance` / `work` 的
versions 与 libraries 条目。

**风险：低**。要留意的是 `glance-appwidget` 会传递带入 `work-runtime` ——
删掉后如果仍然编译通过，就反证了工程里没有对它们的隐式依赖。

**验证**：`./gradlew :app:assembleRelease` 与 `:app:testDebugUnitTest` 都通过。

**⚠ 这一项与 C1 互斥，而且 README 里已经有倾向**

`README.md:266` 的版本规划写着：

```
- **V2** —— 桌面小组件、编辑页补齐。
```

也就是说**桌面小组件已经被列为 V2 范围**，Glance 那两行不是随手加的，是给 V2 占位。
所以这里要分开处理：

- **`work-runtime-ktx`（`app/build.gradle.kts:106`）这一行无论哪条路都可以删。**
  没有任何代码调用它；即使保留 Glance，`glance-appwidget` 也会传递带入
  `work-runtime`，显式声明纯属冗余。等真要写 Worker 时再加回来。
- **Glance 两行怎么处理，取决于 V2 什么时候动手**：
  - V2 近期动 → 保留，走 C1 补实现（删了再装回来成本几乎为零，但没必要来回折腾）
  - V2 很久以后 → 删掉。留着它的代价在 release 包里其实不大（R8 开着，
    没有 manifest 入口的东西基本会被裁掉），主要代价是「依赖清单里挂着一个
    看不懂用途的包」以及每次升级依赖时要多考虑一个组件。

**两条路只能选一条，先拍板再动手** —— 别一边删一边又去写小组件。

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

**实测证据**：`.github` 目录不存在；`docs/quality/README.md` 明写「项目没有 CI」。

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
① 先拍板：桌面小组件做不做  ──┬─→ 做：走 C1（保留 Glance）
                              └─→ 不做：走 A1（删 Glance + WorkManager）
② A2 release 签名 + 首次 release 包实跑验证        ← 门槛项，不依赖 ①
③ A3 验收遗留两项转单测                            ← 不依赖任何项，可穿插
④ A4 加 CI                                        ← 依赖 ①② 完成（让 CI 首跑在干净基线）
⑤ B2 / B3 / B4                                    ← 三者都在纯函数层，可并行，不碰 UI
⑥ B1 先量冷启动基线 → 再决定投不投 Baseline Profile
⑦ B5 单独一轮做依赖升级
⑧ C2 / C5（低成本增强）→ C3（统计维度）→ C4（仅在需要上架时）
```

**为什么 A2 排在①之后但仍然靠前**：它是唯一一个「不做就没法把 App 给别人用」的项，
而成本只有「加一段构建脚本 + 生成一个 keystore」。

**为什么 A4（CI）必须等前两项**：CI 的价值是「以后不用记得跑门禁」。
如果基线本身还带着未验证的 release 构建，第一次 CI 就会红一片，
你会开始习惯性忽略红色 —— 那 CI 就废了。

---

## 八、每项的验证方式（可核验，不接受「应该没问题」）

| 项 | 验证命令 / 动作 | 通过标准 |
| --- | --- | --- |
| A1 | `./gradlew :app:assembleRelease :app:testDebugUnitTest` | 编译通过 + 206 个单测全过 |
| A2 | `./gradlew :app:assembleRelease` → `apksigner verify --print-certs` → `adb install -r` → **真机跑一次 JSON 导出让后导入** | 签名信息显示正确 CN；导出 JSON 能被 release 包读回 |
| A3 | `./gradlew :app:testDebugUnitTest --rerun-tasks` | 新增用例全过；**必须带 `--rerun-tasks`**，否则输入没变会被判 UP-TO-DATE 而什么都没跑 |
| A4 | 推送后看 GitHub Actions 页面 | 两个 job 都是绿；release job 在无 secret 时显示为 skipped 而非 failed |
| B1 | 灌数据后 `adb shell am start -W` 三次取中位数 → 加 profile 后再测三次 | 改善幅度可复现，不是单次抖动 |
| B2 | `./gradlew :app:testDebugUnitTest` | 版本矩阵每个组合都有断言 |
| B3 | 同上 | 含「校验和缺失仍能导入」与「校验和不匹配被拒绝」两个方向的用例 |
| B4 | 设置页手动触发体检 + 单测 | 人为造一条 `sqlite_sequence` 落后的库，体检能报出来 |
| B5 | `./gradlew clean check :app:assembleRelease` | 0 error；warning 数量下降 |

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
