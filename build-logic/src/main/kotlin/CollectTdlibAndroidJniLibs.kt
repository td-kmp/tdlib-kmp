import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties
import javax.inject.Inject

@CacheableTask
abstract class CollectTdlibAndroidJniLibs @Inject constructor(
    private val fs: FileSystemOperations,
) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archives: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun collect() {
        fs.sync {
            archives.files.forEach { dir ->
                // The ABI is only recorded in the archive's tdlib.properties.
                val properties = Properties().apply { dir.resolve("tdlib.properties").reader().use(::load) }
                val abi = checkNotNull(properties.getProperty("android.abi")) { "No android.abi in $dir" }
                from(dir.resolve("lib")) {
                    include("*.so")
                    into(abi)
                }
            }
            into(outputDirectory)
        }
    }
}
