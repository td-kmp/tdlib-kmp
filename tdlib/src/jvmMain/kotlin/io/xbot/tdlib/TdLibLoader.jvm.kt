package io.xbot.tdlib

import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal actual object TdLibLoader {
    @Volatile private var loaded = false

    actual fun load() {
        if (loaded) return
        loadSynchronous()
    }

    @Synchronized
    private fun loadSynchronous() {
        // Double-checked locking
        if (loaded) return
        val libName = System.mapLibraryName("tdjsonjava")
        val resourcePath = "/natives/${osId()}_${archResourceId()}/$libName"

        val resourceStream = TdLibLoader::class.java.getResourceAsStream(resourcePath)
            ?: error(
                "TDLib native library not found on the classpath ($resourcePath). " +
                    "Add the runtime artifact for this platform to your dependencies:\n" +
                    "  implementation(\"io.github.xephosbot:tdlib-kmp-jvm-natives-${osId()}-${archId()}:<version>\")"
            )

        val tempFile = Files.createTempFile(libName, null).apply { toFile().deleteOnExit() }
        resourceStream.use { stream ->
            Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING)
        }
        System.load(tempFile.toFile().canonicalPath)
        loaded = true
    }

    private fun osId(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac")     -> "macos"
            os.contains("linux")   -> "linux"
            os.contains("windows") -> "windows"
            else -> error("Unsupported OS: $os")
        }
    }

    private fun archId(): String = when (System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "arm64"
        else -> "x64"
    }

    /** Directory suffix inside the JAR: historical layout uses `64` for x64. */
    private fun archResourceId(): String = if (archId() == "arm64") "arm64" else "64"
}