@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.withAndroid
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

/**
 * Source-set hierarchy for tdlib-kmp.
 *
 * Analogous to `skikoSourceSetHierarchyTemplate` in JetBrains/skiko.
 *
 * ```
 * common
 * ├── jvmAndroid   (jvm + android — both use JNI)
 * │   ├── jvm
 * │   └── android
 * └── native
 *     ├── darwin
 *     │   ├── ios   (iosArm64, iosSimulatorArm64, iosX64)
 *     │   └── macos (macosArm64, macosX64)
 *     ├── linux     (linuxX64, linuxArm64)
 *     └── mingw     (mingwX64)
 * ```
 */
val tdlibSourceSetHierarchyTemplate = KotlinHierarchyTemplate {
    common {
        group("jvmAndroid") {
            withJvm()
            withAndroid()
        }

        group("native") {
            group("darwin") {
                group("ios") {
                    withIosArm64()
                    withIosSimulatorArm64()
                    withIosX64()
                }

                group("macos") {
                    withMacosX64()
                    withMacosArm64()
                }
            }

            group("linux") {
                withLinuxX64()
                withLinuxArm64()
            }

            group("mingw") {
                withMingwX64()
            }
        }
    }
}
