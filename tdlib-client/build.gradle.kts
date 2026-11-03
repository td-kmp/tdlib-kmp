import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.tdlib.api)
    alias(libs.plugins.vanniktech.maven.publish)
}

kotlin {
    applyHierarchyTemplate(tdlibSourceSetHierarchyTemplate)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // JsAny, JsArray, js(): the Kotlin/Wasm interop API (also reported on the js target via webMain).
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
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
    mingwX64()
    js {
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
        useEsModules()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        webTest.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(projects.tdlibWebWorkerTest)
        }
        commonMain.dependencies {
            api(projects.tdlib)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}

configureTdlibPublishing(
    artifactId = "$TDLIB_ARTIFACT_PREFIX-client",
    pomName = "tdlib-kmp client",
)
