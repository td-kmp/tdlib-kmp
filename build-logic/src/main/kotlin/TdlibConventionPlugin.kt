import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

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
    val dir = layout.buildDirectory.dir("tdlib-natives/$asset").get().asFile

    val interop = target.compilations.getByName("main").cinterops.create("tdjson") {
        definitionFile.set(file("src/nativeInterop/cinterop/tdjson.def"))
        includeDirs(dir.resolve("include"))
        // Not `libraryPaths` in the .def: cinterop would record that absolute path in the klib manifest.
        extraOpts("-libraryPath", dir.resolve("lib").absolutePath)
    }
    tasks.withType(CInteropProcess::class.java).matching { it.name == interop.interopProcessingTaskName }.configureEach {
        inputs.dir(extract.flatMap { it.outputDirectory })
            .withPropertyName("tdlibNatives")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

// Without OS/arch attributes :tdlib-jvm-runtime resolves to every platform's jar.
private fun Project.configureJvmTests(target: KotlinJvmTarget) {
    val host = TdlibJvmPlatform.host()
    configurations.matching { it.name == "${target.name}TestRuntimeClasspath" }.configureEach {
        attributes {
            attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named(host.gradleOs))
            attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(host.gradleArch))
        }
    }
}

private fun Project.configureAndroid(natives: TdlibNatives) {
    // Registered outside the configure block: a task cannot be registered while another one is configured.
    val extracts = TDLIB_ANDROID_ABIS.map { natives.extract(androidTdpackAsset(it)) }
    val collect = tasks.register<CollectTdlibAndroidJniLibs>("collectTdlibAndroidJniLibs") {
        group = "tdlib"
        extracts.forEach { extract -> archives.from(extract.flatMap { it.outputDirectory }) }
        outputDirectory.set(layout.buildDirectory.dir("tdlib-jniLibs"))
    }
    extensions.getByType<KotlinMultiplatformAndroidComponentsExtension>().onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(collect, CollectTdlibAndroidJniLibs::outputDirectory)
    }
}
