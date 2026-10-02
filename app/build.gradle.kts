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
 * ktlint 源码检查（补丁）
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
 * 依赖全部来自本地 Gradle 缓存，可离线运行。
 */
val ktlintCli: Configuration by configurations.creating

dependencies {
    ktlintCli("com.pinterest.ktlint:ktlint-cli:1.0.1")
}

tasks.register<JavaExec>("ktlintSources") {
    group = "verification"
    description = "用 ktlint CLI 扫描 src/main 与 src/test 下的 Kotlin 源码"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = rootDir
    args = listOf("app/src/**/*.kt", "!**/build/**")
    // 违规只报告、不阻断构建，便于先把清单打出来再决定怎么改
    isIgnoreExitValue = true
}
