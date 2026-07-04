@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.maven.publish)
}

val tdlibVersion = providers.gradleProperty("tdlib.version").get()
group = "io.github.xephosbot"
version = tdlibVersion

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "io.xbot.tdlib.client"
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
        commonMain.dependencies {
            api(projects.tdlib)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
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
        groupId = "io.github.xephosbot",
        artifactId = "tdlib-kmp-client",
        version = tdlibVersion
    )

    pom {
        name.set("tdlib-kmp-client")
        description.set("Coroutine-first TDLib client API for Kotlin Multiplatform")
        inceptionYear.set("2026")
        url.set("https://github.com/xephosbot/tdlib-kmp")

        licenses {
            license {
                name.set("Boost Software License 1.0")
                url.set("https://www.boost.org/LICENSE_1_0.txt")
                distribution.set("https://www.boost.org/LICENSE_1_0.txt")
            }
        }

        developers {
            developer {
                id.set("xephosbot")
                name.set("xephosbot")
                email.set("xephosbot@gmail.com")
                url.set("https://github.com/xephosbot/")
                organization.set("xephosbot")
                organizationUrl.set("https://github.com/xephosbot/")
            }
        }

        scm {
            url.set("https://github.com/xephosbot/tdlib-kmp/")
            connection.set("scm:git:git://github.com/xephosbot/tdlib-kmp.git")
            developerConnection.set("scm:git:ssh://git@github.com/xephosbot/tdlib-kmp.git")
        }
    }
}
