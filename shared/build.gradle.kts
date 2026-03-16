import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            //网络
            implementation("io.ktor:ktor-client-core:3.1.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.1.3")
            //数据序列化
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
            //跨平台存储
            implementation("com.russhwolf:multiplatform-settings-no-arg:1.1.1")
            implementation(libs.koin.core)

        }
        androidMain.dependencies {
            implementation("androidx.annotation:annotation:1.9.1")
            implementation("io.ktor:ktor-client-okhttp:3.1.3")
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("MusicDatabase") {
            packageName.set("com.example.mymusicapplication.db")
        }
    }
}

android {
    namespace = "com.example.mymusicapplication.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
