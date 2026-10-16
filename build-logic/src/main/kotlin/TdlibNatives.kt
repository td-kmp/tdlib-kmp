import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/**
 * Where td-pack's native archives come from, and their extracted form.
 *
 * Archives are resolved as dependencies from the "td-pack" Ivy repository declared
 * in settings.gradle.kts (GitHub release assets), so Gradle downloads each one once
 * into its cache. Setting the Gradle property `tdlib.packDir` (e.g.
 * `-Ptdlib.packDir=../td-pack/out`) uses locally built archives instead, which is
 * how unreleased td-pack changes are tested.
 *
 * Each archive is extracted by one [ExtractTdlibArchive] task per project into
 * `build/tdlib-natives/<asset>/`.
 */
class TdlibNatives(private val project: Project) {

    /** td-pack release to use; the root gradle.properties' `tdlib.version`. */
    val version: Provider<String> = project.providers.gradleProperty("tdlib.version")

    private val packDir: Provider<String> = project.providers.gradleProperty("tdlib.packDir")

    private val extractTasks = mutableMapOf<String, TaskProvider<ExtractTdlibArchive>>()

    /** Extracted archive directory for [asset] (its `lib/` + `include/` + properties). */
    fun extract(asset: String): TaskProvider<ExtractTdlibArchive> = extractTasks.getOrPut(asset) {
        project.tasks.register<ExtractTdlibArchive>("extract${asset.toTaskSuffix()}") {
            group = "tdlib"
            description = "Extracts the td-pack archive $asset"
            archive.from(archiveFiles(asset))
            outputDirectory.set(project.layout.buildDirectory.dir("tdlib-natives/$asset"))
        }
    }

    private fun archiveFiles(asset: String): FileCollection {
        val local = packDir.orNull
        if (local != null) {
            val file = project.rootProject.file(local).resolve("$asset.tar.zst")
            return project.files(file)
        }
        return archiveConfiguration(asset)
    }

    private fun archiveConfiguration(asset: String): Configuration =
        project.configurations.create("tdpack${asset.toTaskSuffix()}") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
            description = "td-pack release asset $asset"
            dependencies.addLater(version.map { v ->
                project.dependencies.create("io.github.xephosbot.td-pack:$asset:$v@tar.zst")
            })
        }
}

/** "tdlib-jni-android-arm64-v8a" → "TdlibJniAndroidArm64V8a". */
internal fun String.toTaskSuffix(): String =
    split('-', '_', '.').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar(Char::uppercase) }
