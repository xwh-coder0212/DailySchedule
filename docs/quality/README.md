# docs/quality —— 质量门禁的原始证据

本目录保存最近一次完整质量门禁的原始输出。项目没有 CI，这些日志就是"门禁确实跑过"
的唯一凭据，因此随仓库一起提交。

| 文件 | 内容 |
| --- | --- |
| `quality-gate-final.txt` | 最后一次完整门禁的原始输出：ktlint 门禁 + Android Lint + 打包 |
| `quality-gate-lint.txt` | Android Lint 原始报告（`app/build/reports/lint-results-debug.txt` 的原样拷贝） |
| `quality-gate-tests.txt` | 单元测试原始输出（`--rerun-tasks` 强制重跑） |
| `ktlint-violations-before-cleanup.log` | **清理前**的 ktlint 全量违规清单（1469 条，124 个文件，20 条规则，含文件:行:列与规则名） |
| `ktlint-cleanup-and-gate.txt` | 批量格式化 + 基线收紧 + 门禁通过的过程记录 |
| `ktlint-reformat-neutrality.txt` | **语义中性验证**：去掉全部空白后逐字符比对 137 个被重排的文件，证明格式化只动了排版 |
| `device-acceptance-2026-10-02.md` | **真机验收报告**：设备环境、逐项证据、发现的问题、未覆盖项 |

## 最近一次结果（2026-10-02）

| 门禁 | 结果 |
| --- | --- |
| `compileDebugKotlin` | 通过，0 警告 |
| `testDebugUnitTest` | 206 通过 / 0 失败 / 0 跳过 |
| `:app:lintDebug` | 0 error / 27 warning |
| `:app:ktlintGate` | 通过，存量 0 处 |
| `assembleDebug` | 通过 |

单测用 `--rerun` 强制重跑过，不是 Gradle 的 UP-TO-DATE 缓存结果 ——
`testDebugUnitTest` 在输入未变时会被判为最新而不执行，只看 `BUILD SUCCESSFUL`
可能什么都没跑。

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

## 真机验收

`device-acceptance-2026-10-02.md` 记录了在 Redmi（`rothko` / 2407FRK8EC，
Android 16，HyperOS V816）上的逐项证据。其中一条发现直接变成了修复：
设置页与消费分类页既没有标题栏也没有返回按钮，只能靠系统返回手势退出。
