import com.github.luben.zstd.ZstdInputStream
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

/** GitHub repository that hosts the prebuilt TDLib release assets. */
const val TD_PACK_REPO = "xephosbot/td-pack"

/**
 * Downloads a TDLib native archive from a td-pack GitHub release, verifies it
 * against the checksum pinned in [checksumFile] and extracts it into
 * [outputDirectory].
 *
 * The checksums are committed to the repository (SHA256SUMS format), so a
 * regular build performs no GitHub API calls and fails loudly if a release
 * asset is ever re-uploaded with different content. Regenerate the file with
 * the `updateTdlibNativesChecksums` task after bumping `tdlib.version`.
 */
@CacheableTask
abstract class TdlibArtifactTask : DefaultTask() {

    @get:Input
    abstract val tdlibVersion: Property<String>

    /** Asset name without extension, e.g. `tdlib-macos-jni-arm64`. */
    @get:Input
    abstract val assetName: Property<String>

    /** Committed SHA256SUMS file: `<sha256><space><space><asset file name>` per line. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val checksumFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val version = tdlibVersion.get()
        // The full archive file name (and thus the compression format — releases
        // up to v1.8.63 ship .tar.gz, later ones .tar.zst) comes from the pinned
        // checksums file, so a version bump changes format without code edits.
        val (archiveName, expectedSha) = readPinnedEntry(assetName.get())
        val archiveUrl = "https://github.com/$TD_PACK_REPO/releases/download/v$version/$archiveName"

        val archiveFile = File(temporaryDir, archiveName)
        download(archiveUrl, archiveFile)

        val actualSha = sha256Hex(archiveFile)
        check(actualSha == expectedSha) {
            "SHA-256 mismatch for $archiveUrl:\n" +
                "  expected: $expectedSha (from ${checksumFile.get().asFile})\n" +
                "  actual:   $actualSha\n" +
                "If the release was intentionally repackaged, run: ./gradlew updateTdlibNativesChecksums"
        }

        val outDir = outputDirectory.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        extractTar(archiveFile, outDir)
        archiveFile.delete()
    }

    /** Returns the archive file name and its pinned sha256 for [asset]. */
    private fun readPinnedEntry(asset: String): Pair<String, String> {
        val file = checksumFile.get().asFile
        val entry = file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].startsWith("$asset.tar.") }
            ?: error(
                "No checksum for $asset.tar.* in $file.\n" +
                    "Run: ./gradlew updateTdlibNativesChecksums (after bumping tdlib.version)"
            )
        return entry[1] to entry[0].lowercase()
    }

    private fun download(url: String, dest: File) {
        val attempts = 3
        var lastError: Exception? = null
        for (attempt in 1..attempts) {
            try {
                logger.lifecycle("Downloading {} (attempt {}/{})", url, attempt, attempts)
                val connection = URI(url).toURL().openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 120_000
                connection.setRequestProperty("User-Agent", "tdlib-kmp-gradle")
                connection.inputStream.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                return
            } catch (e: Exception) {
                lastError = e
                logger.warn("Download failed ({}), retrying…", e.message)
                Thread.sleep(1_000L * attempt)
            }
        }
        throw RuntimeException("Failed to download $url after $attempts attempts", lastError)
    }

    private fun extractTar(archive: File, outDir: File) {
        val outRoot = outDir.canonicalFile.toPath()
        // zstd-jni's ZstdInputStream is used directly (not through the
        // commons-compress facade): commons-compress may be loaded from a
        // parent Gradle classloader (AGP bundles it) that can't see zstd-jni.
        val decompressed = when {
            archive.name.endsWith(".tar.zst") -> ZstdInputStream(BufferedInputStream(archive.inputStream()))
            archive.name.endsWith(".tar.gz") -> GZIPInputStream(BufferedInputStream(archive.inputStream()))
            else -> error("Unsupported archive format: ${archive.name}")
        }
        TarArchiveInputStream(decompressed).use { tar ->
            generateSequence { tar.nextEntry }.forEach { entry ->
                val target = outDir.resolve(entry.name)
                // Refuse entries that would escape the output directory ("zip slip").
                check(target.canonicalFile.toPath().startsWith(outRoot)) {
                    "Archive entry escapes output directory: ${entry.name}"
                }
                when {
                    entry.isDirectory -> target.mkdirs()
                    entry.isSymbolicLink -> {
                        target.parentFile.mkdirs()
                        Files.createSymbolicLink(target.toPath(), Paths.get(entry.linkName))
                    }
                    else -> {
                        target.parentFile.mkdirs()
                        target.outputStream().use { output -> tar.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
