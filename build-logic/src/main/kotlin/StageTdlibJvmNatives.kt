import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

/**
 * Lays out one JNI library as runtime-jar resources:
 *   <resourceDir>/<library>          the library itself
 *   <resourceDir>/<library>.sha256   its hash; the loader keys its on-disk cache by it
 */
@CacheableTask
abstract class StageTdlibJvmNatives : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val library: ConfigurableFileCollection

    @get:Input
    abstract val resourceDir: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        val lib = library.singleFile
        check(lib.isFile) { "JNI library not found: $lib" }
        val root = outputDirectory.get().asFile
        root.deleteRecursively()
        val dir = root.resolve(resourceDir.get()).apply { mkdirs() }
        lib.copyTo(dir.resolve(lib.name))
        val sha = MessageDigest.getInstance("SHA-256").digest(lib.readBytes())
            .joinToString("") { "%02x".format(it) }
        dir.resolve("${lib.name}.sha256").writeText("$sha\n")
    }
}
