import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType


// 插件块：引入构建项目所需的 Gradle 插件
plugins {
    id("java") // Java 语言支持
    alias(libs.plugins.kotlin) // Kotlin 语言支持
    alias(libs.plugins.composeCompiler) // Compose Compiler 插件 - 必须在 compose 插件之前
    alias(libs.plugins.compose) // Jetpack Compose for Desktop 支持
    alias(libs.plugins.intelliJPlatform) // IntelliJ 平台官方 Gradle 插件，用于插件开发
    alias(libs.plugins.changelog) // 管理插件更新日志 (CHANGELOG.md) 的插件
    alias(libs.plugins.qodana) // JetBrains 的代码质量扫描插件
    alias(libs.plugins.kover) // Kotlin 代码测试覆盖率插件
    kotlin("plugin.serialization") version "2.3.0"
}

// 从 gradle.properties 中读取插件组 ID 和版本号
group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// 设置构建项目时使用的 JVM 版本
kotlin {
    jvmToolchain(21)
}

// 禁用 Gradle 版本警告（临时方案）
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:none")
}

// 配置依赖仓库
repositories {
    mavenCentral()
    google()  // 添加 Google Maven 仓库以支持 AndroidX 依赖

    // IntelliJ 平台专用的仓库配置
    intellijPlatform {
        defaultRepositories()

    }
}


dependencies {

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.foundation)

    // Exposed 数据库框架
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    implementation("io.ktor:ktor-client-core-jvm:3.0.2") // 接口
    implementation("io.ktor:ktor-client-cio:3.0.2") // 干活的
    implementation("ch.qos.logback:logback-classic:1.5.25") // 日志
    implementation("io.ktor:ktor-client-content-negotiation:3.0.2") //接口
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2") //  接口

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // 干活的


    // IntelliJ 平台相关的依赖配置
    intellijPlatform {
        // 基于 platformVersion 属性引入指定的 IntelliJ IDEA 核心库
        intellijIdea(providers.gradleProperty("platformVersion"))

        // 引入 gradle.properties 中定义的内置插件依赖
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // 引入 gradle.properties 中定义的第三方插件依赖
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // 引入 gradle.properties 中定义的内置模块依赖
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        // 引入测试框架
        testFramework(TestFrameworkType.Platform)

    }
}

// 配置 IntelliJ 平台插件 - 核心配置
intellijPlatform {
    // 禁用字节码增强，避免下载 java-compiler-ant-tasks 依赖
    instrumentCode = false

    pluginConfiguration {
        // 插件名称
        name = providers.gradleProperty("pluginName")
        // 插件版本
        version = providers.gradleProperty("pluginVersion")

        // 自动从 README.md 中提取 <!-- Plugin description --> 部分作为插件描述
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        // 自动从 CHANGELOG.md 中提取最新版本的更新内容
        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        // 配置支持的 IDE 版本范围
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }



    }

    // 插件签名配置（发布到插件市场时需要）
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // 插件发布配置
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // 根据版本号中的后缀（如 -alpha）自动决定发布渠道
        channels = providers.gradleProperty("pluginVersion")
            .map {
                listOf(
                    it.substringAfter('-', "")
                        .substringBefore('.')
                        .ifEmpty { "default" })
            }
    }

    // 插件验证配置：检查插件是否与目标 IDE 版本兼容
    pluginVerification {
        ides {
            recommended()
        }
    }
}

// 更新日志插件配置
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// 测试覆盖率插件配置
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

// 自定义 Gradle 任务
tasks {
    // 禁用 buildSearchableOptions 任务，解决 Locale 导致的构建失败问题
    buildSearchableOptions {
        enabled = false
    }

    // 设置 Gradle Wrapper 的版本
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    // 发布插件前自动更新日志
    publishPlugin {
        dependsOn(patchChangelog)
    }
}

tasks.withType<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask> {
    disabledPlugins.add("com.intellij.kubernetes")
}


// UI 测试相关配置
intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}
// 运行时排除这些模块
configurations.runtimeClasspath {
    // 排除所有 kotlinx 协程
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")

    // 排除所有 Kotlin 标准库及其变体
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-annotation-processing-gradle")

    // 排除 Skiko（如果你确认使用 IDE 自带的渲染器）
//    exclude(group = "org.jetbrains.skiko", module = "skiko-awt")
//    exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-windows-x64")

    // 排除通用注解
    exclude(group = "org.jetbrains", module = "annotations")
    exclude(group = "androidx.annotation", module = "annotation-jvm")
}

configurations.testRuntimeClasspath {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")

    // 排除所有 Kotlin 标准库及其变体
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-annotation-processing-gradle")

    // 排除 Skiko（如果你确认使用 IDE 自带的渲染器）
//    exclude(group = "org.jetbrains.skiko", module = "skiko-awt")
//    exclude(group = "org.jetbrains.skiko", module = "skiko-awt-runtime-windows-x64")

    // 排除通用注解
    exclude(group = "org.jetbrains", module = "annotations")
    exclude(group = "androidx.annotation", module = "annotation-jvm")
}