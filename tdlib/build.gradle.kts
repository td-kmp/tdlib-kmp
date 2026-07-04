@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.tdlib.convention)
    alias(libs.plugins.vanniktech.maven.publish)
}

val tdlibVersion = providers.gradleProperty("tdlib.version").get()
group = TDLIB_GROUP_ID
version = tdlibVersion

tdlib {
    version = tdlibVersion
}

kotlin {
    applyHierarchyTemplate(tdlibSourceSetHierarchyTemplate)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "io.xbot.tdlib"
        compileSdk {
            version = release(libs.versions.android.compilesdk.get().toInt()) {
                minorApiLevel = 1
            }
        }
        minSdk = libs.versions.android.minsdk.get().toInt()

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    linuxX64()
    linuxArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = TDLIB_GROUP_ID,
        artifactId = "tdlib-kmp",
        version = tdlibVersion
    )

    pom {
        configureTdlibPom(
            pomName = "tdlib-kmp",
            pomDescription = "TDLib Kotlin Multiplatform library",
        )
    }
}
