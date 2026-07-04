import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

/**
 * Manages TDLib native artifact downloads from GitHub releases.
 *
 * Archive layout:
 *   include/ …
 *   lib/ …
 */
class TdlibDependencies(
    private val project: Project,
    private val tdlibVersion: Property<String>,
) {
    /** Persistent prebuilds directory. */
    private val prebuildsDir: File = project.rootProject.rootDir.resolve("prebuilds/natives")

    /** Committed checksums for all release assets of the pinned version. */
    private val checksumFile = project.rootProject.layout.projectDirectory
        .file("gradle/tdlib-natives.sha256")

    /** Map of asset names to their corresponding task providers. */
    private val taskProviders = mutableMapOf<String, TaskProvider<TdlibArtifactTask>>()

    /** JNI shared library for the given OS/arch (used by JVM target). */
    fun jniFor(os: TdlibOS, arch: TdlibArch): TaskProvider<TdlibArtifactTask> {
        val target = TdlibTarget(os, arch)
        return getOrCreateTask(target.jniAssetName(), target.jniLocalDir())
    }

    /** Static / native library for a K/N target. */
    fun nativeFor(target: KotlinNativeTarget): TaskProvider<TdlibArtifactTask> {
        val os = target.konanTarget.family.toTdlibOS()
        val arch = target.konanTarget.architecture.toTdlibArch()
        val isSimulator = target.konanTarget.isSimulator
        val tdTarget = TdlibTarget(os, arch)
        val asset = assetName(os, arch, isSimulator)
        val local = tdTarget.nativeLocalDir(isSimulator)
        return getOrCreateTask(asset, local)
    }

    /** Android SO for a given ABI string (e.g. "arm64-v8a"). */
    fun androidFor(abi: String): TaskProvider<TdlibArtifactTask> =
        getOrCreateTask("tdlib-android-$abi", "android-$abi")

    private fun getOrCreateTask(assetName: String, localDir: String): TaskProvider<TdlibArtifactTask> {
        return taskProviders.getOrPut(assetName) {
            val suffix = assetName.removePrefix("tdlib-")
                .split("-")
                .joinToString("") { it.replaceFirstChar(Char::titlecase) }

            val taskName = "downloadAndExtractTdlib$suffix"
            val targetDir = prebuildsDir.resolve(localDir)

            project.tasks.register(taskName, TdlibArtifactTask::class.java) {
                group = "tdlib"
                description = "Download and extract TDLib asset: $assetName"
                tdlibVersion.set(this@TdlibDependencies.tdlibVersion)
                this.assetName.set(assetName)
                this.checksumFile.set(this@TdlibDependencies.checksumFile)
                this.outputDirectory.set(targetDir)
            }
        }
    }

    private fun assetName(os: TdlibOS, arch: TdlibArch, sim: Boolean): String {
        val tail = when {
            os == TdlibOS.IOS && sim  -> if (arch == TdlibArch.Arm64) "arm64-simulator" else "x86_64-simulator"
            os == TdlibOS.IOS         -> "arm64"
            os == TdlibOS.Linux       -> if (arch == TdlibArch.X64) "x86_64" else "arm64"
            os == TdlibOS.MacOS       -> if (arch == TdlibArch.X64) "x86_64" else "arm64"
            os == TdlibOS.Windows     -> if (arch == TdlibArch.X64) "x64"    else "arm64"
            else -> error("Unsupported: $os / $arch")
        }
        return "tdlib-${os.id}-$tail"
    }
}
