import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.gradle.kotlin.dsl.register
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/**
 * Wires td-pack's natives into the `:tdlib` Kotlin Multiplatform library:
 *
 *  - Kotlin/Native: a `tdjson` cinterop (src/nativeInterop/cinterop/tdjson.def) over
 *    the target's libtdjson.a; cinterop embeds the archive in the klib.
 *  - Android: all ABIs' libtdjsonjava.so as a generated jniLibs directory (→ AAR).
 *  - JVM: natives are separate runtime artifacts (:tdlib-jvm-runtime, depended on
 *    from jvmMain); this build's own JVM tests resolve just the host's one.
 */
class TdlibConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val natives = TdlibNatives(this)
        val kotlin = extensions.getByType<KotlinMultiplatformExtension>()

        kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach {
            configureNativeTarget(this, natives)
        }
        kotlin.targets.withType(KotlinJvmTarget::class.java).configureEach {
            configureJvmTests(this)
        }
        pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            configureAndroid(natives)
        }
    }
}

private fun Project.configureNativeTarget(target: KotlinNativeTarget, natives: TdlibNatives) {
    val asset = target.konanTarget.tdpackAsset
    val extract = natives.extract(asset)
    // Paths are fixed (build/tdlib-natives/<asset>), so cinterop options can be plain strings.
    val dir = layout.buildDirectory.dir("tdlib-natives/$asset").get().asFile

    val interop = target.compilations.getByName("main").cinterops.create("tdjson") {
        definitionFile.set(file("src/nativeInterop/cinterop/tdjson.def"))
        includeDirs(dir.resolve("include"))
        extraOpts("-libraryPath", dir.resolve("lib").absolutePath)
    }
    tasks.withType(CInteropProcess::class.java).matching { it.name == interop.interopProcessingTaskName }.configureEach {
        // Also the task dependency on the extraction, and a rerun when the archive changes.
        inputs.dir(extract.flatMap { it.outputDirectory })
            .withPropertyName("tdlibNatives")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

private fun Project.configureJvmTests(target: KotlinJvmTarget) {
    // jvmMain depends on :tdlib-jvm-runtime, whose variants are keyed by OS/arch.
    // Asking for the host's makes tests resolve one JNI library instead of all six.
    val host = TdlibJvmPlatform.host()
    configurations.matching { it.name == "${target.name}TestRuntimeClasspath" }.configureEach {
        attributes {
            attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named(host.gradleOs))
            attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(host.gradleArch))
        }
    }
}

private fun Project.configureAndroid(natives: TdlibNatives) {
    // Registered up front: tasks cannot be registered while another one is configured.
    val extracts = TDLIB_ANDROID_ABIS.map { natives.extract(androidTdpackAsset(it)) }
    val collect = tasks.register<CollectTdlibAndroidJniLibs>("collectTdlibAndroidJniLibs") {
        group = "tdlib"
        description = "Collects libtdjsonjava.so for every Android ABI into a jniLibs tree"
        extracts.forEach { extract -> archives.from(extract.flatMap { it.outputDirectory }) }
        outputDirectory.set(layout.buildDirectory.dir("tdlib-jniLibs"))
    }
    extensions.getByType<KotlinMultiplatformAndroidComponentsExtension>().onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(collect, CollectTdlibAndroidJniLibs::outputDirectory)
    }
}
