import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 签名信息放在 keystore.properties（不入库）。文件缺失时回退到 debug 签名，保证任何机器都能构建。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

android {
    namespace = "com.offline.due"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.offline.due"
        minSdk = 26          // java.time 直接用，不需要 core library desugaring
        targetSdk = 35
        versionCode = 82
        versionName = "0.58.18-beta"
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有正式密钥就用正式密钥；没有就退回 debug 签名，仍然可安装
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

/**
 * Compose 编译器配置。
 *
 * ## 为什么需要 stabilityConfigurationFiles
 *
 * Compose 判断一个参数能不能"跳过重组"，靠的是**类型稳定性**。
 * 而它的推断是保守的：只认识基本类型、String、enum，以及被 `@Immutable` /
 * `@Stable` 标记的类。**`java.time` 不在其中。**
 *
 * 于是 `CountdownEvent`（含 `LocalDate`）和 `CountdownInfo`（含三个 `LocalDate`）
 * 全被判成 unstable。后果是：任何接收它们的 Composable 都**永远不会被跳过**，
 * 哪怕值一个字节都没变——列表每滚动一帧，每行都要重新执行一遍。
 *
 * `LocalDate` 其实是不可变的（所有字段 final，方法都返回新实例），
 * 所以这个判定是错的。这份配置就是在纠正它。
 *
 * 为什么不直接给类加 `@Immutable`：那会让 data / domain 层依赖
 * `androidx.compose.runtime`。数据层不该知道界面框架的存在——
 * 而且小组件跑在 RemoteViews 上，根本不参与 Compose 重组。
 * 用编译器配置，约束留在**界面层**，数据层保持干净。
 */
composeCompiler {
    // 报告输出到 build/compose_reports，用来验证优化有没有真的生效。
    // 不看报告就"感觉快了"，是这个领域最常见的自欺。
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
    reportsDestination = layout.buildDirectory.dir("compose_reports")
}

// 稳定性配置走编译器参数，而不是 `composeCompiler { stabilityConfigurationFiles }`：
// 后者在 Kotlin 2.0.21 配套的 Compose 插件里还没有（会报 Unresolved reference）。
// `-P plugin:...` 是插件一直支持的通道，跨版本可靠。
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=" +
                project.file("compose_stability.conf").absolutePath,
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
