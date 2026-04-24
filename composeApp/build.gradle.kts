import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    kotlin("plugin.serialization") version "2.3.0"
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
            implementation("androidx.datastore:datastore-preferences:1.2.0")
            implementation("androidx.media3:media3-exoplayer:1.9.2")
            implementation("androidx.media3:media3-datasource-okhttp:1.9.2")
            implementation("androidx.media3:media3-session:1.9.2")
            implementation("androidx.media3:media3-ui:1.9.2")
            implementation("androidx.media3:media3-exoplayer-hls:1.9.2")
            implementation("androidx.media3:media3-exoplayer-dash:1.9.2")
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
            implementation("com.squareup.okhttp3:okhttp:5.3.2")
            implementation("org.json:json:20251224")
            implementation("io.insert-koin:koin-core:4.1.1")
            implementation("io.insert-koin:koin-compose:4.1.1")
            implementation("media.kamel:kamel-image-default:1.0.9")
            implementation("com.google.code.gson:gson:2.11.0")
            implementation("org.jsoup:jsoup:1.22.1")
            implementation("dev.kotbase:couchbase-lite:3.2.4-1.2.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            implementation("sh.calvin.reorderable:reorderable:3.1.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation("uk.co.caprica:vlcj:4.12.1")
            implementation("net.java.dev.jna:jna:5.18.1")
            implementation("net.java.dev.jna:jna-platform:5.18.1")

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
        versionCode = 1
        versionName = "1.0.1"
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
            packageVersion = "1.0.0"
            windows {
                iconFile.set(project.file("src/jvmMain/composeResources/drawable/bili_music.ico"))
            }
        }
    }
}
