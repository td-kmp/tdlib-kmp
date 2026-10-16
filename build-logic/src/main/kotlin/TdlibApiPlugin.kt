import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Generates the td_api DTOs of the `tdlib.version` schema (or `-Ptdlib.schema=<file>`) into commonMain. */
class TdlibApiPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        val version = providers.gradleProperty("tdlib.version")
        val localSchema = providers.gradleProperty("tdlib.schema")
        // td-pack publishes the schema next to the natives: td-api-v<version>.tl.
        val releasedSchema = configurations.create("tdApiSchema") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
            dependencies.addLater(version.map { v ->
                project.dependencies.create("io.github.xephosbot.td-pack:td-api-v$v:$v@tl")
            })
        }
        val generate = tasks.register<GenerateTdApi>("generateTdApi") {
            group = "tdlib"
            if (localSchema.isPresent) schema.from(rootProject.file(localSchema.get())) else schema.from(releasedSchema)
            packageName.set("io.xbot.tdlib.api")
            outputDirectory.set(layout.buildDirectory.dir("generated/tdApi/commonMain/kotlin"))
        }
        extensions.getByType<KotlinMultiplatformExtension>().sourceSets.named("commonMain") {
            kotlin.srcDir(generate.flatMap { it.outputDirectory })
        }
        useHostJvmNativesInTests()
    }
}
