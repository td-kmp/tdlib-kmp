import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.jetbrains.kotlin.konan.target.KonanTarget

const val TDLIB_GROUP_ID = "io.github.xephosbot"
const val TDLIB_ARTIFACT_PREFIX = "tdlib-kmp"

/** macos_arm64 → macosArm64 */
val KonanTarget.gradleName: String
    get() = name.split('_').mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar(Char::uppercase) }
        .joinToString("")

val KonanTarget.tdpackAsset: String get() = "tdlib-$gradleName"

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
    val resourceDir: String get() = "io/xbot/tdlib/natives/$id"

    companion object {
        fun byId(id: String): TdlibJvmPlatform =
            entries.firstOrNull { it.id == id } ?: error("Unknown JVM platform '$id'")

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

val TDLIB_ANDROID_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

fun androidTdpackAsset(abi: String): String = "tdlib-jni-android-$abi"
