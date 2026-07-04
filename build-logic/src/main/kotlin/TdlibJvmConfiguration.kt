import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

private val ANDROID_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

/**
 * JVM & Android target configuration for TDLib.
 *
 * Analogous to `JvmTasksConfiguration.kt` in JetBrains/skiko.
 *
 * JVM target:
 *   The published `tdlib-kmp-jvm` JAR contains no native libraries. They ship
 *   as per-platform runtime artifacts (`tdlib-kmp-jvm-natives-<os>-<arch>`,
 *   see the `:tdlib-jvm-natives:*` modules), mirroring how Compose Desktop
 *   publishes `desktop-jvm-<platform>`. At runtime `TdLibLoader` extracts the
 *   library for the current host from the classpath.
 *
 * Android target:
 *   The shared `.so` files go into `jniLibs/{abi}/` automatically via
 *   the Android Gradle Plugin, so we only need to copy the extracted
 *   artifacts to the right place.
 */

/**
 * Stages the *build host's* JNI library onto the jvmTest classpath so tests
 * can load TDLib without depending on the published natives artifacts.
 */
fun TdlibProjectContext.configureJvmTarget(target: KotlinJvmTarget) = with(project) {
    val host = currentHostTarget()
    val artifactTask = tdlibDeps.jniFor(host.os, host.arch)

    val stageTask = tasks.register<Copy>("stageHostJniForJvmTest") {
        group = "tdlib"
        description = "Stage host JNI library (${host.platformId()}) for jvmTest"
        // from(Provider<Directory>) establishes an implicit task dependency —
        // no explicit dependsOn needed.
        from(artifactTask.flatMap { it.outputDirectory.dir("lib") }) {
            include("*.so", "*.dylib", "*.dll")
            into(host.jvmResourceDir())
        }
        into(layout.buildDirectory.dir("jvmTestJniResources"))
    }

    // srcDir(taskProvider) wires both the directory and the task dependency.
    kotlin.sourceSets.getByName("jvmTest") {
        resources.srcDir(stageTask)
    }

    configureJvmNativesPublications()
}

/**
 * Configures Android target: copies `.so` files into the proper `{abi}/` directory.
 */
fun TdlibProjectContext.configureAndroidTarget(target: KotlinMultiplatformAndroidLibraryTarget) = with(project) {
    val jniLibsBaseDir = layout.buildDirectory.dir("androidJniLibs")

    // Per-ABI copy tasks: prebuilds/natives/android-{abi}/*.so → build/androidJniLibs/{abi}/*.so
    val copyTasks = ANDROID_ABIS.map { abi ->
        val suffix = abi.split("-").joinToString("") { it.replaceFirstChar(Char::titlecase) }
        val artifactTask = tdlibDeps.androidFor(abi)

        tasks.register<Copy>("copyAndroidJniLibs$suffix") {
            group = "tdlib"
            description = "Copy $abi .so libs for Android AAR"
            // from(Provider<Directory>) establishes an implicit task dependency —
            // no explicit dependsOn needed.
            from(artifactTask.flatMap { it.outputDirectory }) { include("*.so") }
            into(jniLibsBaseDir.map { it.dir(abi) })
        }
    }

    // Register assembled jniLibs directory with AGP via the Variant API.
    extensions.getByType(KotlinMultiplatformAndroidComponentsExtension::class.java)
        .onVariants { variant ->
            variant.sources.jniLibs?.addStaticSourceDirectory(jniLibsBaseDir.get().asFile.absolutePath)
        }

    // Wire copy tasks before AGP merges JNI libraries.
    tasks.configureEach {
        if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
            copyTasks.forEach { dependsOn(it) }
        }
    }
}
