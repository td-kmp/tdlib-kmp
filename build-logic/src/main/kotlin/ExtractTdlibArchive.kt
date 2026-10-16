import io.airlift.compress.zstd.ZstdInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

// Gradle's tarTree cannot read zstd.
@CacheableTask
abstract class ExtractTdlibArchive : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archive: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun extract() {
        val source = archive.singleFile
        check(source.isFile) { "td-pack archive not found: $source" }
        val dest = outputDirectory.get().asFile
        dest.deleteRecursively()
        dest.mkdirs()
        val root = dest.canonicalFile

        TarArchiveInputStream(ZstdInputStream(source.inputStream().buffered())).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val target = File(dest, entry.name).canonicalFile
                check(target.toPath().startsWith(root.toPath())) { "Unsafe path in $source: ${entry.name}" }
                when {
                    entry.isDirectory -> target.mkdirs()
                    entry.isFile -> {
                        target.parentFile.mkdirs()
                        target.outputStream().use { tar.copyTo(it) }
                        if (entry.mode and 0b001_001_001 != 0) target.setExecutable(true, false)
                    }
                    else -> logger.warn("Skipping ${entry.name} in $source (not a regular file)")
                }
            }
        }
    }
}
