enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "tdlib-kmp"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // td-pack's GitHub release assets as dependencies: "io.github.xephosbot.td-pack:<asset>:<version>@tar.zst".
        ivy("https://github.com/td-kmp/td-pack/releases/download") {
            name = "td-pack"
            patternLayout { artifact("v[revision]/[module].[ext]") }
            metadataSources { artifact() }
            content { includeGroup("io.github.xephosbot.td-pack") }
        }
    }
}

include(":tdlib")
include(":tdlib-client")

include(":tdlib-jvm-runtime")
listOf("macos-arm64", "macos-x64", "linux-x64", "linux-arm64", "windows-x64", "windows-arm64").forEach {
    include(":tdlib-jvm-runtime:$it")
}
