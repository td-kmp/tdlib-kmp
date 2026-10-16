package io.xbot.tdlib

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Finds and loads libtdjsonjava for the running JVM, trying in order:
 *
 *  1. the directory in the `tdlib.library.path` system property;
 *  2. the JVM's own library directory (`java.home`/lib, or /bin on Windows), where
 *     packaged desktop apps (jpackage, Compose Desktop) usually put natives;
 *  3. the `tdlib-kmp-jvm-runtime-<os>-<arch>` jar on the classpath: the library is
 *     extracted once into a cache directory keyed by its SHA-256
 *     (`tdlib.cache.dir`, default: the OS cache directory) and loaded from there.
 */
internal actual object TdLibLoader {
    private const val LIBRARY = "tdjsonjava"

    @Volatile
    private var loaded = false

    actual fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loadLibrary()
            loaded = true
        }
    }

    private fun loadLibrary() {
        val fileName = System.mapLibraryName(LIBRARY)

        System.getProperty("tdlib.library.path")?.let { dir ->
            loadFile(File(dir, fileName))
            return
        }

        val jvmLibDir = File(System.getProperty("java.home"), if (isWindows) "bin" else "lib")
        File(jvmLibDir, fileName).takeIf { it.isFile }?.let {
            loadFile(it)
            return
        }

        val platform = platformId()
        val resource = "io/xbot/tdlib/natives/$platform/$fileName"
        val hash = javaClass.classLoader.getResourceAsStream("$resource.sha256")
            ?.use { it.bufferedReader().readLine()?.trim() }
            ?: throw UnsatisfiedLinkError(
                "No TDLib native library for $platform on the classpath ($resource). Add " +
                    "io.github.xephosbot:tdlib-kmp-jvm-runtime-$platform, or set tdlib.library.path."
            )
        loadFile(extract(resource, fileName, File(cacheRoot(), "$platform-$hash")))
    }

    /** Extracts [resource] into [dir] once; concurrent JVMs serialize on a lock file. */
    private fun extract(resource: String, fileName: String, dir: File): File {
        val target = File(dir, fileName)
        if (target.isFile) return target
        dir.mkdirs()
        RandomAccessFile(File(dir, ".lock"), "rw").use { lockFile ->
            lockFile.channel.lock().use {
                if (target.isFile) return target
                val temp = File.createTempFile(fileName, ".tmp", dir)
                try {
                    javaClass.classLoader.getResourceAsStream(resource)!!.use { input ->
                        Files.copy(input, temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } finally {
                    temp.delete()
                }
            }
        }
        return target
    }

    private fun loadFile(file: File) {
        try {
            System.load(file.absolutePath)
        } catch (e: UnsatisfiedLinkError) {
            // A native library can be loaded by one class loader only (e.g. two IDE
            // plugins bundling tdlib-kmp): load a private copy instead.
            if (e.message?.contains("already loaded in another classloader") != true) throw e
            val copy = Files.createTempDirectory("tdlib-kmp").toFile().resolve(file.name)
            file.copyTo(copy)
            copy.deleteOnExit()
            copy.parentFile.deleteOnExit()
            System.load(copy.absolutePath)
        }
    }

    private val isWindows: Boolean
        get() = System.getProperty("os.name").lowercase().startsWith("windows")

    /** os-arch, as in the runtime artifact names: macos-arm64, linux-x64, windows-x64, ... */
    private fun platformId(): String {
        val osName = System.getProperty("os.name").lowercase()
        val os = when {
            osName.startsWith("mac") -> "macos"
            osName.startsWith("linux") -> "linux"
            osName.startsWith("windows") -> "windows"
            else -> throw UnsatisfiedLinkError("TDLib has no JVM natives for $osName")
        }
        val arch = when (val a = System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64" -> "arm64"
            "amd64", "x86_64" -> "x64"
            else -> throw UnsatisfiedLinkError("TDLib has no JVM natives for $os/$a")
        }
        return "$os-$arch"
    }

    private fun cacheRoot(): File {
        System.getProperty("tdlib.cache.dir")?.let { return File(it) }
        val home = System.getProperty("user.home")
        val base = when {
            isWindows -> System.getenv("LOCALAPPDATA")?.let(::File) ?: File(home, "AppData/Local")
            System.getProperty("os.name").lowercase().startsWith("mac") -> File(home, "Library/Caches")
            else -> System.getenv("XDG_CACHE_HOME")?.let(::File) ?: File(home, ".cache")
        }
        return File(base, "tdlib-kmp")
    }
}
