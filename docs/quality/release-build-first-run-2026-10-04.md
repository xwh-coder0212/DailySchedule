# release 包首次构建与静态验证（2026-10-04）

## 为什么有这份文档

在此之前这个工程**一个 release 包都没产出过**：

| 证据 | 结果 |
| --- | --- |
| `app/build/outputs/apk/` | 只有 `debug/app-debug.apk` |
| `docs/quality/quality-gate-final.txt` | 全文不含 `release`，只跑过 `:assembleDebug` |
| `signingConfigs` / `storeFile` / `key.properties` | 在 `app/build.gradle.kts`、`build.gradle.kts`、`gradle.properties` 中零命中 |

也就是说 `isMinifyEnabled = true`、`isShrinkResources = true` 这两行开关一直开着，
但 **R8 与资源压缩从未真正执行过一次**，`app/proguard-rules.pro` 是一份没被判过分的规则。
更直接的问题是：没有签名配置，`assembleRelease` 就算跑通，产出的也是**装不上设备**的
unsigned 包 —— 而这一点从构建输出里完全看不出来。

## 本轮改了什么

1. `make_release_keystore.sh` 生成发布密钥（仓库外）：
   `dailyschedule-release.jks`，PKCS12，RSA 4096，有效期至 2056-09-26。
   证书 SHA-256：`1A:C9:0D:20:34:B4:8B:BD:B8:8E:CC:7E:07:CF:5A:3D:BB:BB:DB:AE:97:EF:99:3B:7B:93:57:AD:39:8F:D6:45`
2. `key.properties`（仓库根，已被 `.gitignore:17` 挡住，`git check-ignore` 已确认）。
3. `app/build.gradle.kts` 增加 `signingConfigs`，**只在 `key.properties` 存在且四个键齐全时生效**。
   没密钥时退回 unsigned 并**在构建期打一条警告**，因为「装不上的包 + BUILD SUCCESSFUL」
   是最容易被忽略的组合。

## 产物

| 项 | release | debug |
| --- | --- | --- |
| 字节数 | **7,379,541**（7.03 MB） | 28,235,569（26.93 MB） |
| dex 个数 | **1** | 21 |
| 主要 dex 大小 | 3,360,988 字节 | classes.dex 44,833,176 字节 |

R8 + `shrinkResources` 让包体降到原来的 **26.1%**（减少 73.9%）。
构建日志中没有出现「本次 release 包不会签名」那条警告，`writeReleaseSigningConfigVersions`
任务存在，说明签名配置确实被吃到了。

签名验证（`apksigner.bat verify --print-certs`，build-tools 37.0.0）：

```text
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
V2 Signer: certificate DN: CN=DailySchedule, OU=Personal, O=xwh-coder0212, C=CN
V2 Signer: certificate SHA-256 digest: 1ac90d2034b48bbdb88ecc7e07cf5a3dbbbbdbae97ef993b7b9357ad398fd645
V2 Signer: key algorithm: RSA / key size (bits): 4096
```

release 包指纹：7,379,541 字节，
SHA-256 `7812a54492d3ac2f559af5e823b9d6d38184071b57e78af2ce3dec0fe84017a6`。

v1（JAR signing）为 `false` 是**预期的**：`minSdk = 26`，Android 7.0 起只认 v2 也够，
AGP 在 `minSdk >= 24` 时本来就不生成 v1 签名。

## R8 有没有伤到 kotlinx.serialization（这是本轮最该查的一件事）

JSON 完整备份是这个 App 的数据迁移闭环。R8 一旦把生成的 `$serializer` 裁掉或改名，
表现是运行期抛 `SerializationException: Serializer for class 'X' is not found`，
而构建阶段一切正常 —— 属于「装到手机上才炸」的那一类。

### 排查经过（两次都以为发现了缺陷，两次都被证据推翻）

**第一轮：在 release dex 里 grep `$$serializer`，结果 0 命中。**

- debug dex 里有 10 个：`BackupDocument$$serializer`、`BackupData$$serializer`、
  `BackupCounts$$serializer`、`CategoryRecord$$serializer`、`ExpenseRecord$$serializer`、
  `ProjectRecord$$serializer`、`SessionRecord$$serializer` 以及三个导航参数类。
- release dex 里一个都没有 —— 看起来像被删了。
- **推翻**：`mapping.txt` 里它们都在，只是改了名：

  ```
  com.dailyschedule.app.core.transfer.BackupDocument$$serializer -> tg:
      com.dailyschedule.app.core.transfer.BackupDocument$$serializer INSTANCE -> a
  ```

  grep 不到是因为类描述符已经变成 `Ltg;`，`serializer` 这个子串不存在了。

**第二轮：`mapping.txt` 显示枚举常量字段被改名，怀疑 JSON 里的枚举值会跟着变。**

```
com.dailyschedule.app.core.model.SessionStatus RUNNING -> f
com.dailyschedule.app.core.model.SessionStatus COMPLETED -> h
com.dailyschedule.app.core.model.SessionMode STOPWATCH -> e
```

如果 kotlinx.serialization 是通过反射读字段名来写 JSON，备份文件里的枚举值就会从
`"RUNNING"` 变成 `"f"` —— 那就不是崩溃，而是**静默的格式污染**，比崩溃更糟。

- **推翻**：`dexdump -d` 反汇编改名后的枚举，`<clinit>` 里传给 `java.lang.Enum.<init>` 的
  名字字符串是**原样**的：

  ```
  const-string v1, "RUNNING"
  const-string v2, "PAUSED"
  const-string v3, "COMPLETED"
  const-string v4, "DISCARDED"
  ```

  `Enum.name()` 返回的是构造时传进去的那个字符串，与字段叫什么无关。
  R8 改名的是**字段**，不是**名字**。四个枚举（`SessionStatus` / `SessionMode` /
  `SessionSource` / `ExpenseType`）全部核对过，无一例外。

### 顺带查清的一个现象

`SessionSource` 的 `TIMER` 静态字段在 `mapping.txt` 里查不到，而 `DEFAULT -> e` 在。
源码里 `DEFAULT` 是 companion 的一个 `val DEFAULT: SessionSource = TIMER`，
R8 做了值传播：读 `SessionSource.TIMER` 的地方直接读 `DEFAULT`，于是 `TIMER` 字段成了死代码被删。
`values()` 数组与实例的 `name()` 都不受影响，所以对序列化与 `valueOf` 都无影响。

### 结论

R8 **没有**破坏 kotlinx.serialization。原因不必猜 —— 已解析的 R8 配置（`configuration.txt`）
里能直接看到 `kotlinx-serialization-core-jvm:1.9.0` 自带的 consumer 规则
（`META-INF/com.android.tools/r8/kotlinx-serialization-r8.pro` 与 `kotlinx-serialization-common.pro`），
AGP 自动应用：

```
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers public class **$$serializer { private ** descriptor; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
```

枚举一侧由 AGP 默认配置护住：

```
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
```

这也意味着 `app/proguard-rules.pro` 里手写的那几条 kotlinx.serialization 规则**基本是冗余的**。
保留它们无成本，但要知道真正的保险来自库自带的 consumer 规则，不是手写的那几行。

备份 JSON 的所有字段名字面量（`schemaVersion`、`exportedAtIso`、`accumulatedPauseMs`、
`pauseStartElapsedMs`、`dailyTargetMinutes` …）在 release dex 的字符串表里逐条查过，**全部存在**。

## 驱动脚本上踩到的两个坑（和产物正确性无关，但会让证据失真）

1. **`--continue` 不能省。** 一次运行 `:app:testDebugUnitTest :app:ktlintGate :app:lintDebug
   :app:assembleRelease` 时，`ktlintGate` 失败会让后面的 `lintDebug` 与 `assembleRelease`
   直接不执行 —— 一次运行只能看到一个关卡。加上 `--continue` 之后四个关卡各自出结论，
   退出码仍然是失败。
2. **归档用的那次必须加 `--rerun-tasks`。** 源码没变时 `testDebugUnitTest` 会被判为最新而跳过，
   日志里只有 `BUILD SUCCESSFUL`，证明不了测试真的跑过。

另外记录一条**本机沙箱**的限制，不是工程问题：沙箱里的 Gradle 守护进程写
`GRADLE_USER_HOME/caches/build-cache-1` 会被拒（`Failed to store cache entry … .part (拒绝访问。)`）。
同一目录、同一套动作由 Bash 直接启动的 JVM 做完全成功
（`D:/toolchain/probe/CacheWriteProbe.java`：建 `.part` → 写 8MB → `ATOMIC_MOVE` → 删除，四步全过），
所以判据是**进程身份**而不是路径或 ACL。绕过方式是 `--no-build-cache`，只损失构建速度。
在普通终端里跑 `./gradlew` 不受影响。

## 仍未覆盖

| 项 | 状态 |
| --- | --- |
| **release 包的真机端到端** | **未验**。需要装上 release 包，跑一次 JSON 导出 → 导入，并确认导入后的库与导出文件逐字段一致。静态验证再强也替代不了这一步 —— 它证明的是「类与字符串还在」，不是「这条代码路径真的能跑通」。 |
| release 包在设备上的安装 | 未验 |
| keystore 的备份 | **未做**。keystore 与口令只存在于本机、仓库之外的同一个目录里，既不在仓库内，也没有任何离线备份。签名私钥不能重新生成 —— 丢了就无法再给已安装的 App 发升级包（Android 靠签名区分「同一应用的新版本」与「另一个应用」），只能换 `applicationId` 重装，用户数据要重新导。**这是当前最该补的一件事。** |

## 签名身份（已定，且为什么必须现在定）

证书 DN 最终是：

```text
CN=DailySchedule, OU=Personal, O=xwh-coder0212, C=CN
```

第一版生成时按 keytool 的默认写法带上了 `L=Beijing, ST=Beijing`。那两个字段是占位值、
不代表任何已确认的信息，但它们会被**原样写进每一个已分发 APK 的签名里**，而
**签名私钥不能更换** —— 一旦对外发过包，这个 DN 就跟着那批包走到底，改不了。
当时一个包都还没分发出去，重生成的成本是零，所以去掉了 `L` / `ST`，
只留能核验身份的字段（Android Studio 自带的自签名证书用的也是 `CN=…, O=Android, C=US`
这类占位写法，这些字段与 Android 的签名校验逻辑无关）。

重生成之后 release 包也重新签了一遍，指纹见上文。这是在**任何分发动作之前**做的，
所以没有任何已安装的包因此失效。
