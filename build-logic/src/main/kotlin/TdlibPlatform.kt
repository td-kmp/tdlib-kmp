/*
 * Platform model for tdlib-kmp.
 *
 * Contains:
 *  - TdlibOS / TdlibArch enums and their Konan converters.
 *  - TdlibTarget data class used as the single key for all path decisions.
 *  - Centralised path helpers on TdlibTarget:
 *      nativeLocalDir()  — local dir for K/N static artifacts  (e.g. linux-x86_64)
 *      jniLocalDir()     — local dir for JNI shared artifacts  (e.g. linux-x86_64-jni)
 *      jvmResourceDir()  — JAR resource path for fat JVM JAR  (e.g. natives/linux_64)
 */
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

enum class TdlibOS(val id: String) {
    Windows("windows"),
    Linux("linux"),
    MacOS("macos"),
    Android("android"),
    IOS("ios");
}

enum class TdlibArch(val id: String) {
    X64("x64"),
    Arm64("arm64");
}

fun Family.toTdlibOS(): TdlibOS = when (this) {
    Family.MINGW -> TdlibOS.Windows
    Family.LINUX -> TdlibOS.Linux
    Family.OSX -> TdlibOS.MacOS
    Family.ANDROID -> TdlibOS.Android
    Family.IOS -> TdlibOS.IOS
    else -> error("Unsupported family: $this")
}

fun Architecture.toTdlibArch(): TdlibArch = when (this) {
    Architecture.X64 -> TdlibArch.X64
    Architecture.ARM64 -> TdlibArch.Arm64
    else -> error("Unsupported arch: $this")
}

internal data class TdlibTarget(val os: TdlibOS, val arch: TdlibArch)

/**
 * Compose-style platform id used in published JVM natives artifact names,
 * e.g. `macos-arm64`, `linux-x64`, `windows-x64`.
 */
internal fun TdlibTarget.platformId(): String = "${os.id}-${arch.id}"

/** Parses a platform id (`macos-arm64`, `windows-x64`, …) back into a [TdlibTarget]. */
internal fun tdlibTargetFrom(platformId: String): TdlibTarget {
    val os = TdlibOS.entries.firstOrNull { platformId.startsWith("${it.id}-") }
        ?: error("Unknown OS in platform id: $platformId")
    val arch = TdlibArch.entries.firstOrNull { platformId == "${os.id}-${it.id}" }
        ?: error("Unknown arch in platform id: $platformId")
    return TdlibTarget(os, arch)
}

/**
 * Release asset name for the desktop JNI archive of this target,
 * e.g. `tdlib-macos-jni-arm64`, `tdlib-linux-jni-x86_64`, `tdlib-windows-jni-x64`.
 * (td-pack uses `x86_64` on macOS/Linux but `x64` on Windows.)
 */
internal fun TdlibTarget.jniAssetName(): String {
    val archId = when {
        arch == TdlibArch.Arm64 -> "arm64"
        os == TdlibOS.Windows -> "x64"
        else -> "x86_64"
    }
    return "tdlib-${os.id}-jni-$archId"
}

/** The desktop target of the machine running this build (used for jvmTest). */
internal fun currentHostTarget(): TdlibTarget {
    val osName = System.getProperty("os.name").lowercase()
    val os = when {
        osName.contains("mac") -> TdlibOS.MacOS
        osName.contains("linux") -> TdlibOS.Linux
        osName.contains("windows") -> TdlibOS.Windows
        else -> error("Unsupported build host OS: $osName")
    }
    val arch = when (val osArch = System.getProperty("os.arch")) {
        "aarch64", "arm64" -> TdlibArch.Arm64
        "x86_64", "amd64" -> TdlibArch.X64
        else -> error("Unsupported build host arch: $osArch")
    }
    return TdlibTarget(os, arch)
}

/**
 * True when this Konan target is an iOS/watchOS/tvOS simulator.
 *
 * Two cases must be handled:
 *  - New-style names contain "simulator" (e.g. iosSimulatorArm64 → "ios_simulator_arm64").
 *  - Legacy iosX64 target name is just "ios_x64" — no "simulator" in the name,
 *    but for iOS the x86_64 architecture is exclusively used by the simulator.
 */
internal val KonanTarget.isSimulator: Boolean
    get() = name.contains("simulator", ignoreCase = true) ||
            (family == Family.IOS && architecture == Architecture.X64)

/**
 * Local directory name for extracted native (K/N) artifacts.
 * e.g. linux-x86_64, macos-arm64, ios-arm64-simulator
 */
internal fun TdlibTarget.nativeLocalDir(isSimulator: Boolean = false): String {
    val tail = when {
        os == TdlibOS.IOS && isSimulator -> if (arch == TdlibArch.Arm64) "arm64-simulator" else "x86_64-simulator"
        os == TdlibOS.IOS                -> "arm64"
        os == TdlibOS.Linux              -> if (arch == TdlibArch.X64) "x86_64" else "arm64"
        os == TdlibOS.MacOS              -> if (arch == TdlibArch.X64) "x86_64" else "arm64"
        os == TdlibOS.Windows            -> if (arch == TdlibArch.X64) "x64"    else "arm64"
        else -> error("Unsupported: $os / $arch")
    }
    return "${os.id}-$tail"
}

/**
 * Local directory name for extracted JNI artifacts.
 * e.g. linux-x86_64-jni, macos-arm64-jni, windows-x64-jni
 */
internal fun TdlibTarget.jniLocalDir(): String = "${nativeLocalDir()}-jni"

/**
 * Resource path used inside the JVM fat JAR.
 * Follows the /natives/{os}_{arch}/ convention.
 * e.g. natives/linux_64, natives/macos_arm64, natives/windows_64
 */
internal fun TdlibTarget.jvmResourceDir(): String {
    val archSuffix = if (arch == TdlibArch.X64) "64" else "arm64"
    return "natives/${os.id}_$archSuffix"
}
