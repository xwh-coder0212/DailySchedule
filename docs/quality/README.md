# docs/quality —— 质量门禁的原始证据

本目录保存最近一次完整质量门禁的原始输出，随仓库一起提交。

**从 2026-10-04 起这个仓库有了 CI**（`.github/workflows/ci.yml`），推送即跑单测、
ktlint 门禁与 Lint，另外还有一个 job 真的走一次 R8 与资源压缩。在此之前，
「门禁成立与否」取决于这一次记不记得手动跑，本目录的日志就是唯一的凭据；
现在日志的作用变成了「本地怎么复现 CI 的结论」和「留一份可回溯的原始输出」。

| 文件 | 内容 |
| --- | --- |
| `quality-gate-final.txt` | 最后一次完整门禁的原始输出：ktlint 门禁 + Android Lint + 打包 |
| `quality-gate-lint.txt` | Android Lint 原始报告（`app/build/reports/lint-results-debug.txt` 的原样拷贝） |
| `quality-gate-tests.txt` | 单元测试原始输出（`--rerun-tasks` 强制重跑） |
| `ktlint-violations-before-cleanup.log` | **清理前**的 ktlint 全量违规清单（1469 条，124 个文件，20 条规则，含文件:行:列与规则名） |
| `ktlint-cleanup-and-gate.txt` | 批量格式化 + 基线收紧 + 门禁通过的过程记录 |
| `ktlint-reformat-neutrality.txt` | **语义中性验证**：去掉全部空白后逐字符比对 137 个被重排的文件，证明格式化只动了排版 |
| `device-acceptance-2026-10-02.md` | **真机验收报告（第一轮）**：设备环境、逐项证据、发现的问题、未覆盖项 |
| `device-acceptance-2026-10-03.md` | **真机验收报告（第二轮）**：JSON 备份/恢复全链路、补录角标，以及验收脚本自身修掉的 4 个失真问题 |
| `release-build-first-run-2026-10-04.md` | **release 包首次构建与静态验证**：签名、包体对比、R8 有没有裁掉 kotlinx.serialization（含两次「看起来是缺陷但实测不是」的排查） |
| `release-runtime-verification-2026-10-04.md` | **release 包运行期验证**：把 R8 后的包装进 Android 运行时，跑通 JSON 导出与恢复；含本次在验收脚本里查出的 3 个失真问题与 1 个环境事实 |
| `release-runtime-verify-log.txt` | 上文的原始输出（模拟器启动 → 安装 → 冷启动 → 复用验收脚本跑 JSON 全链路） |
| `release-runtime-verify-backup-log.txt` | 上文中 `verify_backup.sh` 单跑的原始输出 |
| `release-runtime-coldstart.png` | release 包冷启动后的首页截图（资源未被 `isShrinkResources` 误裁的直接凭证） |
| `ci-first-green-2026-10-04.md` | **CI 首次绿灯**：两次运行（先红后绿）的逐步骤记录、为变绿修掉的三处问题、以及 CI 现在守住/没守住什么 |
| `ui-foundation-2026-10-04.md` | **界面令牌层收口**：量化诊断（令牌引用数全为 0 而硬编码 39 处）、新增间距/排版/动效令牌与统一空状态、21 处圆角等价收回令牌、以及「未做视觉验证」的边界 |

## CI

**从 2026-10-04 起这个仓库有了 CI**（`.github/workflows/ci.yml`），推送即跑。
`docs/quality/ci-first-green-2026-10-04.md` 记了第一次真实运行的过程，
本目录其它日志的作用因此变成「本地怎么复现 CI 的结论」和「留一份可回溯的原始输出」。

| job | 内容 | 首次绿灯耗时 |
| --- | --- | --- |
| `quality` | 单测（`testDebugUnitTest`）+ ktlint 门禁（`ktlintGate`）+ Lint（`lintDebug`） | 292 秒 |
| `release` | `assembleRelease` 真跑一次 R8 与资源压缩，再核对产物（含 1 MB 体积下限守卫） | 234 秒 |

两个 job 都**不带 `--rerun-tasks`** —— CI 每次都是全新 workspace，没有历史产物，
`testDebugUnitTest` 不可能是 UP-TO-DATE。那个参数只在本地需要。

`release` job 目前跑的是 **unsigned 包**：四个 `ANDROID_*` secret 未配，
`还原发布密钥` 那一步会跳过。这一步要盯的是「R8 有没有把东西裁坏」而不是签名，
所以足够。**签名包目前只在本地产出**（见 `release-build-first-run-2026-10-04.md`）。

CI 不跑真机 / 模拟器 —— GitHub 托管的 runner 没有 Android 运行时。
运行期那一层仍靠 `release-runtime-verification-2026-10-04.md` 里那套脚本手工跑。


## 最近一次结果（2026-10-04）

四个关卡分四次运行、每次一张日志，原因见下面「`--rerun-tasks` 与 lint 的竞态」。
原始输出见 `quality-gate-final.txt`（四段合同一份）与 `quality-gate-tests.txt`、`quality-gate-lint.txt`。

| 门禁 | 命令 | 结果 |
| --- | --- | --- |
| 单元测试 | `:app:testDebugUnitTest --rerun-tasks` | **213 通过 / 0 失败 / 0 错误 / 0 跳过**（24 个测试类） |
| ktlint | `:app:ktlintGate` | 通过，存量 **0** 处 |
| Android Lint | `:app:lintDebug --rerun-tasks` | **0 error / 27 warning** |
| release 打包 | `:app:assembleRelease` | 通过（工程史上首次），7,379,541 字节，v2 已签名 |
| release 运行期 | `verify_release_rt.sh`（模拟器） | **`退出码=0`，崩溃特征 0 命中**；冷启动 415 ms；JSON 导出→恢复→撤销全通 |
| CI（GitHub Actions） | 推送后看 `/actions` | **两个 job 全绿**（run `37187264545`，sha `5e6fb89`，总 9 分 13 秒） |

单测用 `--rerun-tasks` 强制重跑过，不是 Gradle 的 UP-TO-DATE 缓存结果 ——
`testDebugUnitTest` 在输入未变时会被判为最新而不执行，只看 `BUILD SUCCESSFUL`
可能什么都没跑。`quality-gate-tests.txt` 末尾附了逐类的用例数，方便核对「213」这个数字
不是从某一次旧运行里抄来的。

## `--rerun-tasks` 与 lint 的竞态

**`--rerun-tasks` 是全局开关，不要加在整个任务图上。** 不加限定地重跑全部任务时，
`lintAnalyzeDebugUnitTest` 与 `kspReleaseKotlin` 会在同一次构建里同时跑，
而 lint 把 `app/build/kspCaches/release/backups/java` 当成 Java 源根，
KSP 正在换掉这个目录，于是 lint 读到：

```
Unexpected failure during lint analysis of ExportTableFactoryTest.kt
...kspCaches\release\backups\java\...\AppStartupUseCase_Factory.java (系统找不到指定的路径。)
```

lint 自己的报错文案就写着 `this is a bug in lint or one of the libraries it depends on`，
不是本工程的配置问题。做法是拆开：

```bash
./gradlew :app:testDebugUnitTest --rerun-tasks    # 强制重跑只给单测
./gradlew :app:ktlintGate                         # ktlint 任务没有声明 outputs，本来每次都会跑
./gradlew :app:lintDebug --rerun-tasks            # 单独一次，任务图里不含 release 任务
./gradlew :app:assembleRelease
```

## ktlint 存量债的处理路径

1469 处违规不是某一次改版引入的，而是历史代码与 ktlint 1.0 默认规则的差异。
处理分三步，顺序不能换：

1. **先建基线**（`app/config/ktlint-baseline.txt`）。
   目的是让门禁对**新代码**立刻生效 —— 否则"先把存量清完再说"会拖到永远，
   而在这期间每一次提交都还在往存量里加。
2. **再批量格式化**（`ktlintFormatAll`，即 `ktlint -F`）。
3. **最后收紧基线到 0**，门禁从此按最严标准执行。

基线记 `(文件, 规则, 出现次数)`，**不记行号** —— 行号会随任何一次编辑整体位移，
那种基线一改就满屏假警报，等于没有基线。

清理后剩下的三类违规不能靠 `-F` 解决，是手工处理的：

| 规则 | 数量 | 处理方式 |
| --- | --- | --- |
| `function-naming` | 61 | 全是 `@Composable` 的 PascalCase 函数名 —— Compose 编译器自己就要求大写开头，ktlint 不认识 `@Composable`。用 `.editorconfig` 点名豁免该注解，而不是关掉整条规则。 |
| `max-line-length` | 5 | **格式化过程中新产生**的（原始清单里一处都没有）：4 处是新写的单行构造器调用，1 处是老代码因缩进加深被顶过 140 字符。逐个换行。 |
| `discouraged-comment-location` / `no-consecutive-comments` | 11 / 2 | 参数列表里的尾随注释移到上一行；相邻的 KDoc 合并。 |

## 存量违规的规则分布

下面这份是清理前那 1469 处的构成（ktlint 自己在扫描输出末尾给出的汇总）：

| 规则 | 数量 |
| --- | --- |
| `function-signature` | 504 |
| `multiline-expression-wrapping` | 438 |
| `no-empty-first-line-in-class-body` | 100 |
| `annotation` | 74 |
| `function-naming` | 61 |
| `blank-line-before-declaration` | 61 |
| `trailing-comma-on-call-site` | 53 |
| `import-ordering` | 26 |
| `statement-wrapping` | 26 |
| `if-else-wrapping` | 21 |
| `multiline-if-else` | 18 |
| `no-blank-line-in-list` | 17 |
| `wrapping` / `argument-list-wrapping` | 15 / 15 |
| `no-unused-imports` | 13 |
| `discouraged-comment-location` | 11 |
| `no-multi-spaces` / `spacing-between-declarations-with-annotations` | 7 / 7 |
| `if-else-bracing` / `no-consecutive-comments` | 1 / 1 |

前四条占了 76%。它们全都是「换行位置」类规则 —— 也就是说这批存量债的大头是
排版风格漂移，不是代码缺陷。真正可能掩盖问题的两条（`no-unused-imports` 13 处、
`if-else-bracing` 1 处）也一并清掉了。

## release 运行期验证

静态证据（`mapping.txt` / `usage.txt` / `dexdump`）读的是文件，能把「类被删了」「字段被改了」
证伪，但证伪不了「跑起来会炸」—— `Resources.NotFoundException`、
`NoClassDefFoundError`、反序列化路径上的问题，都要等真实运行时才暴露。

`release-runtime-verification-2026-10-04.md` 补的就是这一层：把 R8 后的 release 包
装进 Android 14（模拟器，`system-images;android-34;google_apis;x86_64`）跑一遍
JSON 导出与恢复。结论是 `verify_backup.sh 退出码 = 0`、logcat 崩溃特征 0 命中、
冷启动 415 ms，导出文件里 `appVersion` 是 `1.0.0`（不带 `-debug` 后缀，可直接确认
产出属于 release 包）。

它**不是**真机验收：手上没有可连接的设备。所以 `device-acceptance-*` 那两轮
仍然是真机侧的唯一凭据，模拟器跑的只是「release 构建产物在 AOSP 路径上可用」。

驱动脚本 `D:/toolchain/emu/verify_release_rt.sh`。两个必须写在脚本里的原因：
模拟器进程必须活在同一棵进程树里（`emulator.exe` 只是 launcher，拉起后端就自己退了，
分次调用会让进程被回收），以及跑之前要清掉设备上上一轮的导出文件
（验收脚本用「最新那个」取文件，残留会让结论失真）。

## 真机验收

- `device-acceptance-2026-10-02.md`：在 Redmi（`rothko` / 2407FRK8EC，Android 16，
  HyperOS V816）上的逐项证据。其中一条发现直接变成了修复：设置页与消费分类页
  既没有标题栏也没有返回按钮，只能靠系统返回手势退出。
- `device-acceptance-2026-10-03.md`：第二轮，验 JSON 完整备份与恢复的**全链路**
  （导出写盘 → 内容校验 → 与设备库逐字段比对 → 导入确认 → 真正替换 → 撤销），
  外加上一轮唯一没覆盖的**补录角标**。14 项全过，未发现应用缺陷；
  倒是在验收脚本自身里查出 4 个会让结论失真的问题（坐标取错、断言读错文件、
  失败仍报通过、导入路径没真正走到选文件），已一并修复。

真机验收的断言强度分两档：**「JSON 自洽」是弱断言**（只证明文件内部没矛盾），
**「与设备库逐 id 逐字段比对」才是强断言**（证明文件与库一致）。
脚本里用的是后者 —— 先 `am force-stop` 让 WAL 落盘，再把数据库拉下来用 SQLite 打开对比。
