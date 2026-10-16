import org.gradle.api.Project

private val JETPACK_VERSION = Regex("""\d+\.\d+\.\d+(-(alpha|beta|rc)\d{2})?""")

/** `<tdlib.version>-<library.version>`, e.g. 1.8.68-1.0.0-alpha01. */
fun Project.tdlibArtifactVersion(): String {
    val tdlib = providers.gradleProperty("tdlib.version").get()
    val library = providers.gradleProperty("library.version").get()
    require(JETPACK_VERSION.matches(library)) {
        "library.version must be x.y.z with an optional -alphaNN, -betaNN or -rcNN suffix, got '$library'"
    }
    return "$tdlib-$library"
}
