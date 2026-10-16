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

/**
 * Builds an Android `jniLibs` tree (`<abi>/libtdjsonjava.so`) from extracted
 * td-pack archives; registered with AGP as a generated jniLibs source directory.
 * Each archive names its ABI in tdlib.properties (`android.abi`).
 */
@CacheableTask
abstract class CollectTdlibAndroidJniLibs @Inject constructor(
    private val fs: FileSystemOperations,
) : DefaultTask() {

    /** Extracted td-pack Android JNI archive directories. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archives: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun collect() {
        fs.sync {
            archives.files.forEach { dir ->
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
