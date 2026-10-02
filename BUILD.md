# 构建与运行说明

> 本项目使用 Android Gradle Plugin 9.3.0 + Kotlin 2.3.21 + JDK 21 + Gradle 9.5。
>
> **Kotlin 停在 2.3.x 是硬约束，不要单独升级**：KSP 最高只到 2.3.11（不存在 2.4.x 对应版本），
> 而 KSP 与 Kotlin 版本必须匹配。
> 完整源码已就位于 `D:\DailySchedule`，已验证可编译出包 + 82 个单测全绿（2026-09-10）。

## 1. 环境准备

### 选项 A：Android Studio（推荐）

1. 下载 [Android Studio Koala 或更新](https://developer.android.com/studio)
2. 安装时使用默认设置（自带 JDK 21 + SDK Manager）
3. 通过 SDK Manager 安装：
   - Android SDK Platform 37
   - Android SDK Build-Tools 36 或更高
   - Android SDK Platform-Tools
4. **Open**（不是 New Project）打开 `D:\DailySchedule`

### 选项 B：仅命令行

```bash
# 1. JDK 21（已就绪在 D:\toolchain\jdk21\jdk-21.0.12.1+1）
export JAVA_HOME=/d/toolchain/jdk21/jdk-21.0.12.1+1
export PATH=$JAVA_HOME/bin:$PATH

# 2. Android SDK
# 接受许可（需先 cd 到 cmdline-tools/latest/bin）
sdkmanager --licenses

# 装平台与构建工具
sdkmanager "platform-tools" "platforms;android-37" "build-tools;36.0.0"

# 3. Gradle Wrapper（项目自带 gradlew，无需装）
cd /d/DailySchedule
./gradlew :app:assembleDebug         # 编译 APK
./gradlew :app:testDebugUnitTest     # 跑所有单元测试

# ⚠️ 中文用户名必读（否则测试跑不起来）
# 跑 testDebugUnitTest 时 Gradle 会 fork worker 进程，其 classpath 指向
# %USERPROFILE%\.gradle\...\gradle-worker.jar。中文路径会被 JVM 按错误字符集解码，
# 报 ClassNotFoundException: GradleWorkerMain。
# assembleDebug 不受影响（不 fork test worker），所以这个坑只在跑测试时暴露。
# 解法：把 GRADLE_USER_HOME 指到无中文路径（缓存需一并迁移或重新下载）。
export GRADLE_USER_HOME=D:/toolchain/gradle-home
```

### 验证过的版本组合（2026-09-10，勿随意改动）

| 项 | 版本 | 约束 |
| --- | --- | --- |
| Gradle | 9.5 | — |
| AGP | 9.3.0 | 内置 Kotlin，禁止再应用 `kotlin.android`；已移除 `BaseExtension` |
| Kotlin | 2.3.21 | 受 KSP 上限约束 |
| KSP | 2.3.11 | 仓库最高版本 |
| composeBom | 2026.08.00 | — |
| Hilt | 2.60.1 | 需适配 AGP 9；注意 2.52.0 是不存在的空档 |
| Room | 2.8.4 | 不存在 3.0.x |
| compileSdk / targetSdk | 37 | — |

## 2. 已知需要首次启动验证的项

### 已解决（2026-09-10 验证完毕，无需再试）

- ~~JDK 21 + Room 3.0.1 的 KSP 兼容性~~ → 采用 Room **2.8.4**（不存在 3.0.x）+ KSP **2.3.11**，已验证可编译。
- ~~AGP 9 内置 Kotlin 导致 `kotlin.android` 插件冲突~~ → 已从 `app/build.gradle.kts` 移除该插件。

### 仍需真机验证

- **部分唯一索引**：语义已修正为常量表达式键（详见 `AppDatabase.kt` 注释）。
  注意它不在 Room schema 元数据里，需在真机上**重启 App 两次**，
  确认第二次打开不抛 schema 校验错误；若报错，改为放进 `Migration(1,2)` 并把 `version` 升到 2。
- **16 KB page size**（Android 15+）：解压 `app-release.apk` 看 `lib/` 段的 align 是否为 16384，否则在新设备上启动即崩。
- **Monet 动态取色 / 深色模式跟随**：需 Android 12+ 真机。

## 3. 单测覆盖

| 文件 | 用例数 | 覆盖范围 |
| --- | --- | --- |
| `DayBoundaryTest` | 12 | 日切、跨月、跨年、跨时区 |
| `DurationCalculatorTest` | 10 | 时长算法、暂停累加、边界 |
| `DurationFormatterTest` | 9 | 时长/金额/时刻格式化 |
| `DatabaseConstraintsTest` | 18 | DB 层不变量 + 活动会话唯一索引（Robolectric） |
| `AppResultTest` | 8 | 成功/失败链、异常归类 |
| `ProjectUseCasesTest` | 7 | 名称/颜色/归档/删除 |
| `AddExpenseUseCaseTest` | 6 | 金额/分类校验 |
| `TimerLifecycleUseCaseTest` | 6 | 开始/暂停/继续/结束的完整状态机 |
| `TimerRebootRecoveryUseCaseTest` | 4 | 重启截断 + 改时间检测 |
| `ThemeHardcodeTest` | 2 | 源码扫描：页面不得硬编码颜色 |
| **合计** | **82** | 全部通过（2026-09-10） |

## 4. 第一次真机测试清单（T10 验收）

跑通即视为 MVP 计时子系统就绪：

1. 装上 App，第一次打开能看到 7 个预置分类被写入
2. 首页 → "记录" Tab → 点 "开始（不带项目）"
3. 通知栏出现常驻计时通知，标题"专注中"
4. 锁屏 5 分钟，回到 App，数字应正确增加 5 分钟
5. 在 App 里点"暂停"，数字停下；点"继续"恢复
6. 点"结束"，首页出现 "1 次专注" 计数
7. 杀掉 App 进程，重新打开：会话仍在跑（数据没丢）
8. `adb shell reboot` 重启手机，App 不在通知栏出现（已自动截断）
9. 修改系统时间（向前/向后各调 1 小时），回到 App：首页顶部"待确认"卡片出现
10. 导出 JSON（设置页 T11 实现）能完整看到刚才的会话

## 5. 后续阶段

- T11：项目页、统计页、设置页、记账页
- T12：导出/导入、桌面小组件
- T13：稳定性、国产 ROM 适配、电量优化
- Phase 8–13：见 `docs/phase7-implementation-plan.md`
