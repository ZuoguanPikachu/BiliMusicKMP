import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

val appVersion: String = providers.gradleProperty("app.version").get()

val appVersionCode: Int = run {
    val parts = appVersion.substringBefore("-").split(".")
    fun part(index: Int) = parts.getOrNull(index)?.toIntOrNull() ?: 0
    part(0) * 10_000 + part(1) * 100 + part(2)
}

val generateAppVersion by tasks.registering {
    val version = appVersion
    val outputDir = layout.buildDirectory.dir("generated/appVersion/kotlin")
    inputs.property("appVersion", version)
    outputs.dir(outputDir)
    doLast {
        val target = outputDir.get().asFile.resolve("com/zuoguan/bilimusickmp/AppVersion.kt")
        target.parentFile.mkdirs()
        target.writeText(
            """
            |package com.zuoguan.bilimusickmp
            |
            |/** 应用版本号；由 Gradle 依据 gradle.properties 的 app.version 生成，请勿手改。 */
            |object AppVersion {
            |    const val NAME: String = "$version"
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvm()
    
    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.datasource.okhttp)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.exoplayer.dash)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(compose.materialIconsExtended)
            implementation(libs.materialkolor)
            // Release 更新说明是 Markdown，用它排版；-m3 变体自带 Material3 默认样式
            implementation(libs.multiplatform.markdown.renderer.m3)
            implementation(libs.okhttp)
            implementation(libs.org.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kamel.image.default)
            implementation(libs.gson)
            implementation(libs.jsoup)
            implementation(libs.kotbase.couchbase.lite)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.reorderable)
            implementation(libs.quickjs.kt)
        }
        // 版本常量由 Gradle 生成后并入 commonMain，Android 与桌面共用同一份
        getByName("commonMain").kotlin.srcDir(generateAppVersion)
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.vlcj)
            implementation(libs.jna)
            implementation(libs.jna.platform)

        }
    }
}

android {
    namespace = "com.zuoguan.bilimusickmp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.zuoguan.bilimusickmp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersion
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.zuoguan.bilimusickmp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "BiliMusic"
            packageVersion = appVersion
            windows {
                iconFile.set(project.file("src/jvmMain/composeResources/drawable/bili_music.ico"))
            }
        }
    }
}
