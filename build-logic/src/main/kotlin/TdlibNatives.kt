import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/** td-pack archives: from the "td-pack" Ivy repository, or from `-Ptdlib.packDir`. */
class TdlibNatives(private val project: Project) {

    val version: Provider<String> = project.providers.gradleProperty("tdlib.version")

    private val packDir: Provider<String> = project.providers.gradleProperty("tdlib.packDir")

    private val extractTasks = mutableMapOf<String, TaskProvider<ExtractTdlibArchive>>()

    fun extract(asset: String): TaskProvider<ExtractTdlibArchive> = extractTasks.getOrPut(asset) {
        project.tasks.register<ExtractTdlibArchive>("extract${asset.toTaskSuffix()}") {
            group = "tdlib"
            description = "Extracts the td-pack archive $asset"
            archive.from(archiveFiles(asset))
            outputDirectory.set(project.layout.buildDirectory.dir("tdlib-natives/$asset"))
        }
    }

    private fun archiveFiles(asset: String): FileCollection {
        val local = packDir.orNull ?: return archiveConfiguration(asset)
        return project.files(project.rootProject.file(local).resolve("$asset.tar.zst"))
    }

    private fun archiveConfiguration(asset: String): Configuration =
        project.configurations.create("tdpack${asset.toTaskSuffix()}") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
            dependencies.addLater(version.map { v ->
                project.dependencies.create("io.github.xephosbot.td-pack:$asset:$v@tar.zst")
            })
        }
}

internal fun String.toTaskSuffix(): String =
    split('-', '_', '.').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar(Char::uppercase) }
