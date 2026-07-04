import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Desktop JVM platforms that get their own natives artifact.
 */
private val JVM_NATIVES_TARGETS = listOf(
    TdlibTarget(TdlibOS.MacOS, TdlibArch.Arm64),
    TdlibTarget(TdlibOS.MacOS, TdlibArch.X64),
    TdlibTarget(TdlibOS.Linux, TdlibArch.Arm64),
    TdlibTarget(TdlibOS.Linux, TdlibArch.X64),
    TdlibTarget(TdlibOS.Windows, TdlibArch.Arm64),
    TdlibTarget(TdlibOS.Windows, TdlibArch.X64),
)

/**
 * Publishes the desktop JNI libraries as per-platform runtime artifacts,
 * following JetBrains/skiko (`skiko-awt-runtime-<os>-<arch>`) and Compose
 * Desktop (`desktop-jvm-<platform>`):
 *
 *   io.github.xephosbot:tdlib-kmp-jvm-natives-macos-arm64
 *   io.github.xephosbot:tdlib-kmp-jvm-natives-linux-x64
 *   … plus tdlib-kmp-jvm-natives-all bundling every platform.
 *
 * Each artifact is a plain JAR with the library at `natives/<os>_<arch>/`
 * (the classpath location `TdLibLoader` probes) and a POM dependency on the
 * core `tdlib-kmp` module, so one dependency is enough on the consumer side.
 *
 * Publications are declared with the `tdlib-` project-name prefix; the
 * vanniktech plugin's `coordinates("…", "tdlib-kmp", …)` rewrites it to the
 * published `tdlib-kmp-` prefix, same as it does for `tdlib-jvm` → `tdlib-kmp-jvm`.
 * The plugin also attaches an empty javadoc JAR to every publication and signs
 * them, so only a sources JAR needs to be added here (Maven Central requires both).
 */
fun TdlibProjectContext.configureJvmNativesPublications() = with(project) {
    plugins.apply("maven-publish")
    val publishing = extensions.getByType<PublishingExtension>()
    val tdlibVersion = extensions.getByType<TdlibExtension>().version

    val emptySourcesJar = tasks.register<Jar>("tdlibJvmNativesEmptySourcesJar") {
        archiveClassifier.set("sources")
        archiveBaseName.set("tdlib-jvm-natives-empty")
    }

    fun MavenPublication.dependOnCoreModule() = pom.withXml {
        asNode().appendNode("dependencies").appendNode("dependency").apply {
            appendNode("groupId", TDLIB_GROUP_ID)
            appendNode("artifactId", "tdlib-kmp")
            appendNode("version", tdlibVersion.get())
            appendNode("scope", "compile")
        }
    }

    fun Jar.configureReproducibleNativesJar() {
        group = "tdlib"
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }

    val pomNameForPublication = mutableMapOf<String, String>()

    JVM_NATIVES_TARGETS.forEach { target ->
        val platformId = target.platformId()
        val suffix = platformId.split("-")
            .joinToString("") { it.replaceFirstChar(Char::titlecase) }
        val artifactTask = tdlibDeps.jniFor(target.os, target.arch)

        val jarTask = tasks.register<Jar>("tdlibJvmNatives${suffix}Jar") {
            configureReproducibleNativesJar()
            description = "Package $platformId JNI library as a runtime JAR"
            archiveBaseName.set("tdlib-jvm-natives-$platformId")
            from(artifactTask.flatMap { it.outputDirectory.dir("lib") }) {
                include("*.so", "*.dylib", "*.dll")
                into(target.jvmResourceDir())
            }
        }

        publishing.publications.create<MavenPublication>("tdlibJvmNatives$suffix") {
            artifactId = "tdlib-jvm-natives-$platformId"
            artifact(jarTask)
            artifact(emptySourcesJar)
            dependOnCoreModule()
            pomNameForPublication[name] = "TDLib JNI native library for JVM desktop ($platformId)"
        }
    }

    val allJar = tasks.register<Jar>("tdlibJvmNativesAllJar") {
        configureReproducibleNativesJar()
        description = "Package JNI libraries of all desktop platforms as a single runtime JAR"
        archiveBaseName.set("tdlib-jvm-natives-all")
        JVM_NATIVES_TARGETS.forEach { target ->
            from(tdlibDeps.jniFor(target.os, target.arch).flatMap { it.outputDirectory.dir("lib") }) {
                include("*.so", "*.dylib", "*.dll")
                into(target.jvmResourceDir())
            }
        }
    }

    publishing.publications.create<MavenPublication>("tdlibJvmNativesAll") {
        artifactId = "tdlib-jvm-natives-all"
        artifact(allJar)
        artifact(emptySourcesJar)
        dependOnCoreModule()
        pomNameForPublication[name] = "TDLib JNI native libraries for all JVM desktop platforms"
    }

    // The shared `pom { }` block in tdlib/build.gradle.kts runs after this
    // plugin and overwrites name/description on every publication; restore
    // the per-publication values once the project is fully configured.
    afterEvaluate {
        pomNameForPublication.forEach { (publicationName, pomName) ->
            publishing.publications.named(publicationName, MavenPublication::class.java) {
                pom.name.set(pomName)
                pom.description.set(pomName)
            }
        }
    }
}
