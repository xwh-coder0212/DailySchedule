plugins {
    // 注意：AGP 9.0 起内置 Kotlin 支持，不再需要 org.jetbrains.kotlin.android
    // 详见 https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // 版本目录里一直声明着 ktlint，但此前没有任何模块 apply 它 ——
    // 于是 ktlintCheck 这个任务根本不存在，代码风格从未被真正检查过。
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.dailyschedule.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.dailyschedule.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // AppLogger 走 android.util.Log，纯 JVM 单测里它是抛异常的 stub。
            // 打开后 stub 返回默认值，日志调用变成 no-op，测试才能跑领域层逻辑。
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
}

/*
 * ktlint 源码检查（补丁 + 基线门禁）
 *
 * 背景：版本目录里一直声明着 ktlint 插件，但此前**没有任何模块 apply 它**，
 * 于是连 `ktlintCheck` 任务都不存在 —— 代码风格从未被检查过。
 *
 * 补上 apply 之后发现第二个问题：ktlint-gradle 12.3.0 认不出 AGP 9 的源集模型，
 * 只生成了 `.kts` 的检查任务（`runKtlintCheckOverKotlinScripts`）。
 * `ktlintCheck` 会「成功」，但 src/main 与 src/test 下几百个 .kt 文件一个都没扫 ——
 * 那是一次空过（false negative），比失败更危险。
 *
 * 因此这里直接用 ktlint CLI 把两个源集扫一遍，绕开插件的 Android 集成。
 *
 * 四个任务：
 *   ktlintSources  —— 只扫描并输出报告到 build/reports/ktlint/ktlint.out
 *   ktlintBaseline —— 用当前扫描结果刷新 app/config/ktlint-baseline.txt（主动确认存量时用）
 *   ktlintFormatAll —— ktlint -F，批量修掉存量违规（改完必须重编译 + 跑测试）
 *   ktlintGate     —— 与基线比对，**出现新增违规就失败**（已挂进 check）
 *
 * 基线按 (文件, 规则, 出现次数) 记录，不记行号。
 * 行号会随任何一次编辑整体位移，那种基线一改就满屏假警报，等于没有基线。
 */
val ktlintCli: Configuration by configurations.creating

dependencies {
    ktlintCli(libs.ktlint.cli)
}

val ktlintReport = layout.buildDirectory.file("reports/ktlint/ktlint.out")

tasks.register<JavaExec>("ktlintSources") {
    group = "verification"
    description = "用 ktlint CLI 扫描 src/main 与 src/test 下的 Kotlin 源码，报告写到 build/reports/ktlint/ktlint.out"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = rootDir
    args = listOf("app/src/**/*.kt", "!**/build/**")
    // 违规只报告、不阻断：是否放行交给 ktlintGate 依基线判定
    isIgnoreExitValue = true
    doFirst {
        val file = ktlintReport.get().asFile
        file.parentFile.mkdirs()
        standardOutput = file.outputStream()
    }
}

/**
 * 批量自动修复存量风格违规（`ktlint -F`）。
 *
 * ## 为什么不直接开 ktlint-gradle 的 format 任务
 * 同 [ktlintSources]：插件在这个 AGP 版本下扫不到 .kt 源集。
 *
 * ## 用它的顺序是固定的
 * 先跑 [ktlintBaseline] 把存量记下来，再跑本任务修，最后跑 [ktlintGate]。
 * 门禁此时应该报"低于基线"而不是失败。
 * 顺序反了（先修再出基线）等于把基线定成"修完之后剩下的量"，
 * 那它就不再能反映"这次改动有没有引入新违规"。
 *
 * ## 修完之后必须重新编译并跑测试
 * `-F` 只改格式，理论上不动语义，但"理论上"不足以当依据：
 * 它可能重排 import、拆合表达式。所以这一步的验收标准是
 * 编译通过 + 全部单测通过，而不是"命令退出码为 0"。
 */
// 名字不能叫 ktlintFormat：ktlint 插件自己就注册了同名任务，
// 即便它在这个 AGP 版本下扫不到 .kt 源集。加 All 以示区分。
tasks.register<JavaExec>("ktlintFormatAll") {
    group = "verification"
    description = "用 ktlint -F 批量修复存量风格违规。改完必须重新编译并跑测试"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = rootDir
    args = listOf("-F", "app/src/**/*.kt", "!**/build/**")
}

/** 从 ktlint 报告里抽出 (相对路径, 规则) 计数。报告格式：路径:行:列: 说明 (规则) */
fun parseKtlintReport(): Map<Pair<String, String>, Int> {
    val file = ktlintReport.get().asFile
    if (!file.exists()) {
        throw GradleException("找不到 ktlint 报告，请先跑 :app:ktlintSources")
    }
    val linePattern = Regex("""^(.+?):(\d+):(\d+):\s(.+?)\s\((?:standard:)?([a-z0-9-]+)\)\s*$""")
    val counts = mutableMapOf<Pair<String, String>, Int>()
    val rootPath = rootProject.rootDir.toPath()
    file.readLines().forEach { line ->
        val m = linePattern.matchEntire(line.trim()) ?: return@forEach
        val raw = m.groupValues[1]
        val asFile = File(raw)
        // 报告里是绝对路径，基线要存相对路径，否则换机器/换目录就失效
        val relative = if (asFile.isAbsolute) {
            runCatching { rootPath.relativize(asFile.toPath()).toString() }.getOrDefault(raw)
        } else {
            raw
        }
        val key = relative.replace('\\', '/') to m.groupValues[5]
        counts[key] = (counts[key] ?: 0) + 1
    }
    return counts
}

val ktlintBaselineFile = layout.projectDirectory.file("config/ktlint-baseline.txt")

fun readKtlintBaseline(): Map<Pair<String, String>, Int> {
    val file = ktlintBaselineFile.asFile
    if (!file.exists()) return emptyMap()
    return file.readLines()
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            (parts[0] to parts[1]) to (parts[2].toIntOrNull() ?: return@mapNotNull null)
        }
        .toMap()
}

tasks.register("ktlintBaseline") {
    group = "verification"
    description = "用当前扫描结果刷新 config/ktlint-baseline.txt"
    dependsOn("ktlintSources")
    doLast {
        val counts = parseKtlintReport()
        val file = ktlintBaselineFile.asFile
        file.parentFile.mkdirs()
        file.writeText(
            counts.entries
                .sortedWith(compareBy({ it.key.first }, { it.key.second }))
                .joinToString("") { (key, count) -> "${key.first}\t${key.second}\t$count\n" },
        )
        logger.lifecycle(
            "ktlint 基线已刷新：${counts.size} 个 (文件, 规则) 组合，" +
                "共 ${counts.values.sum()} 处违规",
        )
    }
}

tasks.register("ktlintGate") {
    group = "verification"
    description = "与 app/config/ktlint-baseline.txt 比对，出现新增或超量的违规就失败"
    dependsOn("ktlintSources")
    doLast {
        val baseline = readKtlintBaseline()
        val counts = parseKtlintReport()
        val introduced = mutableListOf<String>()
        val exceeded = mutableListOf<String>()
        val improved = mutableListOf<String>()
        counts.entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .forEach { (key, count) ->
                val allowed = baseline[key]
                when {
                    allowed == null -> introduced += "${key.first}\t${key.second}\t$count"
                    count > allowed -> exceeded += "${key.first}\t${key.second}\t$count > $allowed"
                    count < allowed -> improved += "${key.first}\t${key.second}\t$count < $allowed"
                }
            }
        if (improved.isNotEmpty()) {
            logger.lifecycle(
                "ktlint：${improved.size} 个组合低于基线（可以跑 :app:ktlintBaseline 收紧基线）",
            )
        }
        if (introduced.isEmpty() && exceeded.isEmpty()) {
            logger.lifecycle(
                "ktlint 门禁通过：本次改动没有引入新的风格违规" +
                    "（基线共 ${baseline.values.sum()} 处存量）",
            )
            return@doLast
        }
        val message = buildString {
            appendLine("ktlint 门禁未通过。")
            if (introduced.isNotEmpty()) {
                appendLine()
                appendLine("新增违规（基线里没有的 文件/规则 组合）：")
                introduced.take(40).forEach { appendLine("  $it") }
                if (introduced.size > 40) appendLine("  … 其余 ${introduced.size - 40} 处")
            }
            if (exceeded.isNotEmpty()) {
                appendLine()
                appendLine("已有组合超出基线数量：")
                exceeded.take(40).forEach { appendLine("  $it") }
                if (exceeded.size > 40) appendLine("  … 其余 ${exceeded.size - 40} 处")
            }
            appendLine()
            append("完整报告：build/reports/ktlint/ktlint.out")
        }
        throw GradleException(message)
    }
}

// 挂进 check：`./gradlew check`（以及 CI）会连带跑门禁，新增违规直接失败
tasks.named("check") {
    dependsOn("ktlintGate")
}
