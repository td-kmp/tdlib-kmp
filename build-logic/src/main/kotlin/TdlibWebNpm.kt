import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinPackageJsonTask

private val NPM_INSTALL_TASKS = setOf("rootPackageJson", "wasmRootPackageJson", "kotlinNpmInstall", "kotlinWasmNpmInstall")

/** td-pack's npm package for this repository's web tests: by its release URL, or the local archive with -Ptdlib.packDir. */
fun KotlinDependencyHandler.tdlibWasmNpm(project: Project): Dependency {
    val natives = TdlibNatives(project)
    if (!project.providers.gradleProperty("tdlib.packDir").isPresent) {
        return npm(TDLIB_WEB_NPM_PACKAGE, tdlibWebNpmUrl(natives.version.get()))
    }
    val extract = natives.extract(TDLIB_WEB_ASSET)
    // KGP wants the directory to exist at configuration time; the extract task fills it.
    val dir = project.layout.buildDirectory.dir("tdlib-natives/$TDLIB_WEB_ASSET").get().asFile.apply { mkdirs() }
    project.tasks.withType(KotlinPackageJsonTask::class.java).configureEach { dependsOn(extract) }
    project.rootProject.tasks.matching { it.name in NPM_INSTALL_TASKS }.configureEach { dependsOn(extract) }
    return npm(TDLIB_WEB_NPM_PACKAGE, dir)
}
