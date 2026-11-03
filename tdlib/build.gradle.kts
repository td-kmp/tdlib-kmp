import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.tdlib.convention)
    alias(libs.plugins.vanniktech.maven.publish)
}

kotlin {
    applyHierarchyTemplate(tdlibSourceSetHierarchyTemplate)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    android {
        namespace = "io.xbot.tdlib"
        compileSdk {
            version = release(libs.versions.android.compilesdk.get().toInt()) {
                minorApiLevel = 1
            }
        }
        minSdk = libs.versions.android.minsdk.get().toInt()

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }

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
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        webMain.dependencies {
            api(libs.kotlinx.browser)
        }
        jvmMain.dependencies {
            runtimeOnly(projects.tdlibJvmRuntime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        webTest.dependencies {
            implementation(projects.tdlibWebWorkerTest)
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
    artifactId = TDLIB_ARTIFACT_PREFIX,
    pomName = "tdlib-kmp",
)
