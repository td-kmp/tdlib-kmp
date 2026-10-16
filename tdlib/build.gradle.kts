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
    }

    android {
        namespace = "io.xbot.tdlib"
        compileSdk {
            version = release(libs.versions.android.compilesdk.get().toInt()) {
                minorApiLevel = 1
            }
        }
        minSdk = libs.versions.android.minsdk.get().toInt()

        // td_jni.cpp binds the natives by name (RegisterNatives); keep them under R8.
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

    sourceSets {
        jvmMain.dependencies {
            // The JNI library for the consumer's platform (or all of them), see
            // build-logic/src/main/kotlin/TdlibJvmRuntimePlugins.kt.
            runtimeOnly(projects.tdlibJvmRuntime)
        }
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

configureTdlibPublishing(
    artifactId = TDLIB_ARTIFACT_PREFIX,
    pomName = "tdlib-kmp",
)
