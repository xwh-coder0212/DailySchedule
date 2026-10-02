# docs/quality —— 质量门禁的原始证据

本目录保存最近一次完整质量门禁的原始输出。项目没有 CI，这些日志就是"门禁确实跑过"
的唯一凭据，因此随仓库一起提交。

| 文件 | 内容 |
| --- | --- |
| `quality-gate-final.txt` | 最后一次完整跑的合并输出：Android Lint + 单元测试 + 打包 + ktlint 扫描 |
| `quality-gate-lint.txt` | Android Lint 单独跑的完整输出（含 Gradle 失败时的基线提示） |
| `quality-gate-tests.txt` | 单元测试单独跑的完整输出 |
| `quality-gate.log` | ktlint 全量扫描的完整违规清单（1469 条，含文件:行:列与规则名） |

## 最近一次结果（2026-10-02）

| 门禁 | 结果 |
| --- | --- |
| `compileDebugKotlin` | 通过，0 警告 |
| `testDebugUnitTest` | 170 通过 / 0 失败 / 0 跳过 |
| `:app:lintDebug` | 0 error / 54 warning |
| `:app:ktlintSources` | 完成，1469 处存量违规未清 |
| `assembleDebug` | 通过，APK 28,614,215 字节 |

单测用 `--rerun` 强制重跑过，不是 Gradle 的 UP-TO-DATE 缓存结果 ——
`testDebugUnitTest` 在输入未变时会被判为最新而不执行，只看 `BUILD SUCCESSFUL`
可能什么都没跑。

## ktlint 违规分布（前几类）

| 规则 | 数量 |
| --- | --- |
| `standard:function-signature` | 504 |
| `standard:multiline-expression-wrapping` | 438 |
| `standard:no-empty-first-line-in-class-body` | 100 |
| `standard:annotation` | 74 |
| `standard:blank-line-before-declaration` | 61 |
| `standard:function-naming` | 61 |
| `standard:trailing-comma-on-call-site` | 53 |

集中在 `InMemoryRepositories.kt`(154)、`DatabaseConstraintsTest.kt`(55)、
`ProjectDetailSheet.kt`(44)、`XlsxWriter.kt`(44)。

这些是历史代码与 ktlint 1.0 默认规则的差异，不是某一轮改版引入的。
清理前需要先建立版本控制，否则跨 124 个文件的批量格式化成不可回滚操作。
