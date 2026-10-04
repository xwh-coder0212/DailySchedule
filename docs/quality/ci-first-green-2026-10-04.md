# CI 首次绿灯 —— 2026-10-04

## 这份文档回答什么

`A4 加 CI` 这一项此前只做到「文件写好了」——工作流在本地没法真跑，
只有推上去才知道它成不成立。本文记录**第一次真实运行**的过程与结果，
以及为了让它变绿而修掉的三处问题。

前一轮（不推上去就发现不了）的另一半在这里：
[`release-build-first-run-2026-10-04.md`](release-build-first-run-2026-10-04.md)
讲的是 release 包本身，本文讲的是「持续验证这件事有没有真的跑起来」。

## 运行记录

| 项 | 值 |
| --- | --- |
| 仓库 | `xwh-coder0212/DailySchedule`（公开） |
| 工作流 | `.github/workflows/ci.yml`，`name: CI` |
| **首次运行（红）** | run `37187051959`，sha `24a2b9a`，40 秒失败 |
| **修复后运行（绿）** | run `37187264545`，sha `5e6fb89`，`conclusion: success`，`run_attempt: 1` |
| 总耗时 | 9 分 13 秒（`2026-10-04T07:55:31Z → 08:04:21Z`） |
| quality job | `success`，**292 秒**（单测 + ktlint 门禁 + Lint） |
| release job | `success`，**234 秒**（R8 与资源压缩真的跑一次） |
| 产物 | `release-apk`，4,190,356 字节（GitHub 的压缩包体积，非 APK 原始字节） |

逐步骤结果（`/actions/runs/37187264545`）：

| quality | 结果 | release | 结果 |
| --- | --- | --- | --- |
| Set up job | ✅ | Set up job | ✅ |
| checkout@v7 | ✅ | checkout@v7 | ✅ |
| 装 JDK 21 | ✅ | 装 JDK 21 | ✅ |
| 确认 SDK 组件在位（缺则补装） | ✅ | 确认 SDK 组件在位（缺则补装） | ✅ |
| setup-gradle@v6 | ✅ | setup-gradle@v6 | ✅ |
| **单元测试** | ✅ | 还原发布密钥 | ⏭ 跳过（属预期） |
| **ktlint 门禁** | ✅ | **assembleRelease** | ✅ |
| **Android Lint** | ✅ | **核对产物** | ✅ |
| 失败时上传测试报告 | ⏭ 跳过（因为没有失败） | 上传 release 包 | ✅ |

`还原发布密钥` 被跳过是**设计如此**：仓库里没有 `key.properties`，四个
`ANDROID_*` secret 也没配，此时 `assembleRelease` 产出 unsigned 包。
这一步要盯的是「R8 有没有把东西裁坏」，不是签名，所以 unsigned 足够；
「核对产物」里的体积下限（1 MB）就是防止 R8 把东西裁空。

## 为让它变绿修掉的三处问题

前两处是**推送前**在本地逐项核对 CI 依赖时查出的，第三处是**推送后**从
GitHub 的注解里查出的。

### 1. `platforms;android-37` 这个包名不存在（推送前）

工作流里写的是 `sdkmanager "platforms;android-37"`。
实测 `sdkmanager --list` 里搜 `platforms;android-37` 前缀，只有
`37.0` / `37.1` / `37.2` 三个 —— SDK 从 API 36 之后改成了「主版本.小版本」命名，
没有不带小版本的那个。写错时 `sdkmanager` 会报 `Failed to find package` 并让该步失败。
已改为 `platforms;android-37.0`（`build-tools;37.0.0` 本来就对，未改）。

### 2. `gradlew` 在 git 里没有可执行位（推送前）

`git ls-files -s gradlew` 显示 mode 是 `100644`。本机是 Windows，
`core.fileMode` 不跟踪这个位，所以当初入库时就丢了。
Linux runner 上 `./gradlew ...` 会直接 `Permission denied`。
已用 `git update-index --chmod=+x gradlew` 改成 `100755`。

顺带核过、**没有问题**的项：`gradle-wrapper.jar` / `gradle-wrapper.properties` /
`app/config/ktlint-baseline.txt` / `gradle/libs.versions.toml` / `app/proguard-rules.pro`
均已入库；`.gitattributes` 里 `gradlew text eol=lf`、`*.bat text eol=crlf`、
`*.jar binary` 都在位，`gradlew` 实际换行符也是 LF；工作流用真实 YAML 解析器
（PyYAML 6.0.3）解析通过。

### 3. `android-actions/setup-android@v3` 在 Node 24 上会碎（推送后发现）

首次运行 40 秒就红，**失败点是这一第三方 Action 本身**，
它之后的步骤（含 `sdkmanager`、Gradle、测试）全部被 skip —— 所以上一轮修的包名
当时根本没机会被执行到。

GitHub 在这个 check run 上给了三条注解，指向同一个方向：

```
Node.js 20 is deprecated. The following actions target Node.js 20 but are being
forced to run on Node.js 24: actions/checkout@v4, actions/setup-java@v4,
actions/upload-artifact@v4, android-actions/setup-android@v3.
setup-java v4 is deprecated and will no longer receive updates. Please migrate
to actions/setup-java@v5.
```

做法不是继续赌这个 Action，而是：

- 四个 Action 全部升到当前大版本（`action.yml` 里的 `using` 已声明为 `node24`）：
  checkout `v4→v7`、setup-java `v4→v6`、upload-artifact `v4→v7`、
  gradle/actions 的 setup-gradle `v4→v6`。升之前逐个核过新版本 inputs 与旧用法兼容。
- **去掉对 setup-android 的依赖**。runner 镜像本来就预装了
  `ANDROID_HOME=/usr/local/lib/android/sdk`，平台清单里已有 `android-37.0`（rev 2）
  与 `build-tools 37.0.0`（`actions/runner-images` 的 `Ubuntu2404-Readme.md` 可查）。
  改成一段 shell：定位 `sdkmanager` → 接受许可 → **幂等**补装这两个组件 →
  打印实际装到的清单。

`raw.githubusercontent.com` 上把每个新版本 tag 的 `action.yml` 拉下来核过入参，
这一步是为了避免「升版本号 → 再空跑一轮 CI」的循环。

## 关于 `explorer` 那类路径定位

定位 `sdkmanager` 用的是「按候选路径逐个试」（显式 `latest` 路径优先，再通配版本目录），
而不是 `find ... | sort | tail -1`。两条理由，措辞上只当作「可能选错」而非「一定选错」：

1. 通配符会同时匹配到 `lib/sdkmanager-classpath.jar` 这类同名前缀文件，
   排序末位不一定落在 `bin/` 上；
2. `find` 默认不跟随符号链接目录（需要 `-L`），而 `[ -x ]` 直接访问是会跟随的。

本机用 POSIX 布局验过四种情形（只有版本目录 / `latest` 为真实目录 / 不存在 /
存在但缺 `+x`）：能选到的都选到了，选不到的走守卫分支报 `::error::` 并 `exit 1`。
**第 2 条没能在本机复现** —— Git Bash 的 `ln -s` 建出来的是真实目录而不是链接，
所以这条只作为「不依赖该行为」的写法，不作为已验证的故障记录。

## CI 现在守住了什么、还没守住什么

| 事项 | 状态 |
| --- | --- |
| 每次推送跑单测 / ktlint 门禁 / Lint | ✅ 已生效（`quality` job） |
| 每次推送真的走一次 R8 与资源压缩 | ✅ 已生效（`release` job，含体积下限守卫） |
| 推送时对 main 的改动做质量拦截 | ✅ 已生效 |
| 签名后的 release 包 | ❌ 未生效：四个 `ANDROID_*` secret 未配，跑的是 unsigned 包 |
| 真机 / 模拟器上的运行期行为 | ❌ 不在 CI 范围内（GitHub 托管的 runner 没有 Android 运行时；模拟器在嵌套虚拟化下不可靠）。这一层仍靠 `release-runtime-verification-2026-10-04.md` 那套脚本手工跑 |
| `ubuntu-latest` 的基线漂移 | ⚠️ 已收到提示：`ubuntu-latest` 将于 2026-10-19 迁到 Ubuntu 26（`actions/runner-images#14748`）。届时 `ANDROID_HOME` 路径与预装清单需要复核 |
