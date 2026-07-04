import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.net.HttpURLConnection
import java.net.URI

/**
 * Regenerates the committed `gradle/tdlib-natives.sha256` file for the current
 * `tdlib.version`.
 *
 * Prefers the `SHA256SUMS` asset attached to the td-pack release; falls back
 * to the per-asset `digest` field of the GitHub Releases API for releases
 * published before td-pack started shipping a checksums manifest.
 *
 * Maintenance task — run manually after bumping `tdlib.version`, then commit
 * the updated file.
 */
@UntrackedTask(because = "Always refreshes checksums from the GitHub release")
abstract class UpdateTdlibChecksums : DefaultTask() {

    @get:Input
    abstract val tdlibVersion: Property<String>

    @get:OutputFile
    abstract val checksumFile: RegularFileProperty

    @TaskAction
    fun run() {
        val version = tdlibVersion.get()
        val content = fetchSha256Sums(version) ?: fetchFromApiDigests(version)
        checksumFile.get().asFile.writeText(content)
        logger.lifecycle("Wrote checksums for td-pack v{} to {}", version, checksumFile.get().asFile)
    }

    private fun fetchSha256Sums(version: String): String? {
        val url = "https://github.com/$TD_PACK_REPO/releases/download/v$version/SHA256SUMS"
        return try {
            val text = httpGet(url)
            // Keep only archive entries — .tl schema files are not consumed by the build.
            text.lineSequence()
                .filter { it.contains(".tar.") }
                .sortedBy { it.substringAfter("  ") }
                .joinToString("\n", postfix = "\n")
        } catch (e: Exception) {
            logger.lifecycle("No SHA256SUMS asset for v$version ({}), falling back to GitHub API digests", e.message)
            null
        }
    }

    private fun fetchFromApiDigests(version: String): String {
        val body = httpGet("https://api.github.com/repos/$TD_PACK_REPO/releases/tags/v$version")
        val release = JsonSlurper().parseText(body) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val assets = release["assets"] as List<Map<*, *>>
        val lines = assets
            .filter { (it["name"] as String).contains(".tar.") }
            .map { asset ->
                val name = asset["name"] as String
                val digest = (asset["digest"] as? String)?.removePrefix("sha256:")
                    ?: error("GitHub API returned no sha256 digest for $name")
                "$digest  $name"
            }
            .sortedBy { it.substringAfter("  ") }
        check(lines.isNotEmpty()) { "Release v$version has no .tar.* assets" }
        return lines.joinToString("\n", postfix = "\n")
    }

    private fun httpGet(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "tdlib-kmp-gradle")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        if (connection.responseCode !in 200..299) {
            error("HTTP ${connection.responseCode} for $url")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
