import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/**
 * Convention plugin that makes all build-logic classes
 * (TdlibDependencies, TdlibProjectContext, configuration helpers, etc.)
 * available on the classpath of the consuming project.
 *
 * Apply this plugin in your module's build.gradle.kts:
 *   plugins {
 *       id("tdlib-convention")
 *   }
 */
class TdlibConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val ext = extensions.create<TdlibExtension>("tdlib")
        val kotlin = extensions.getByType<KotlinMultiplatformExtension>()

        val tdlibDeps = TdlibDependencies(project, ext.version)
        val context = TdlibProjectContext(project, kotlin, tdlibDeps)

        tasks.register("updateTdlibNativesChecksums", UpdateTdlibChecksums::class.java) {
            group = "tdlib"
            description = "Refresh gradle/tdlib-natives.sha256 from the td-pack release for the current tdlib.version"
            tdlibVersion.set(ext.version)
            checksumFile.set(rootProject.layout.projectDirectory.file("gradle/tdlib-natives.sha256"))
        }

        kotlin.targets.all {
            when (platformType) {
                KotlinPlatformType.androidJvm -> context.configureAndroidTarget(this as KotlinMultiplatformAndroidLibraryTarget)
                KotlinPlatformType.jvm -> context.configureJvmTarget(this as KotlinJvmTarget)
                KotlinPlatformType.native -> context.configureNativeTarget(this as KotlinNativeTarget)
                else -> { /*Nothing to do*/ }
            }
        }
    }
}
