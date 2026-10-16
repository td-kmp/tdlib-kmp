/*
 * What td-pack ships, and how its release assets are named
 * (see td-pack's README: "Artifacts").
 */
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.jetbrains.kotlin.konan.target.KonanTarget

/** Maven coordinates shared by every module. */
const val TDLIB_GROUP_ID = "io.github.xephosbot"
const val TDLIB_ARTIFACT_PREFIX = "tdlib-kmp"

/** The Gradle-style target name K/N uses (macos_arm64 → macosArm64). */
val KonanTarget.gradleName: String
    get() = name.split('_').mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar(Char::uppercase) }
        .joinToString("")

/** td-pack asset holding the static archive for a Kotlin/Native target. */
val KonanTarget.tdpackAsset: String get() = "tdlib-$gradleName"

/**
 * A desktop JVM platform: one td-pack JNI asset, one runtime artifact.
 * [id] is the JVM-side name (os-arch, as skiko uses), also the resource directory
 * and the runtime artifact suffix.
 */
enum class TdlibJvmPlatform(
    val id: String,
    val libraryFile: String,
    val gradleOs: String,
    val gradleArch: String,
) {
    MacosArm64("macos-arm64", "libtdjsonjava.dylib", OperatingSystemFamily.MACOS, MachineArchitecture.ARM64),
    MacosX64("macos-x64", "libtdjsonjava.dylib", OperatingSystemFamily.MACOS, MachineArchitecture.X86_64),
    LinuxX64("linux-x64", "libtdjsonjava.so", OperatingSystemFamily.LINUX, MachineArchitecture.X86_64),
    LinuxArm64("linux-arm64", "libtdjsonjava.so", OperatingSystemFamily.LINUX, MachineArchitecture.ARM64),
    WindowsX64("windows-x64", "tdjsonjava.dll", OperatingSystemFamily.WINDOWS, MachineArchitecture.X86_64),
    WindowsArm64("windows-arm64", "tdjsonjava.dll", OperatingSystemFamily.WINDOWS, MachineArchitecture.ARM64);

    val tdpackAsset: String get() = "tdlib-jni-$id"
    val artifactId: String get() = "$TDLIB_ARTIFACT_PREFIX-jvm-runtime-$id"

    /** Resource path inside the runtime jar; unique per platform, so jars can coexist. */
    val resourceDir: String get() = "io/xbot/tdlib/natives/$id"

    companion object {
        fun byId(id: String): TdlibJvmPlatform =
            entries.firstOrNull { it.id == id } ?: error("Unknown JVM platform '$id'")

        /** The platform of the JVM running Gradle (for this build's own tests). */
        fun host(): TdlibJvmPlatform {
            val os = System.getProperty("os.name").lowercase()
            val arm = System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")
            return when {
                os.startsWith("mac") -> if (arm) MacosArm64 else MacosX64
                os.startsWith("linux") -> if (arm) LinuxArm64 else LinuxX64
                os.startsWith("windows") -> if (arm) WindowsArm64 else WindowsX64
                else -> error("Unsupported host OS: $os")
            }
        }
    }
}

/** Android ABIs shipped in the AAR (td-pack: tdlib-jni-android-<abi>). */
val TDLIB_ANDROID_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

fun androidTdpackAsset(abi: String): String = "tdlib-jni-android-$abi"
