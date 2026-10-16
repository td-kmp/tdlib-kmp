import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import javax.inject.Inject

class TdlibJvmRuntimePlatformPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val platform = TdlibJvmPlatform.byId(name)
        pluginManager.apply("java-library")
        pluginManager.apply("com.vanniktech.maven.publish")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = org.gradle.api.JavaVersion.VERSION_1_8
            targetCompatibility = org.gradle.api.JavaVersion.VERSION_1_8
        }

        val extract = TdlibNatives(this).extract(platform.tdpackAsset)
        val stage = tasks.register<StageTdlibJvmNatives>("stageTdlibJvmNatives") {
            group = "tdlib"
            description = "Lays out the ${platform.id} JNI library as jar resources"
            library.from(extract.map {
                it.outputDirectory.file("lib/${platform.libraryFile}")
            })
            resourceDir.set(platform.resourceDir)
            outputDirectory.set(layout.buildDirectory.dir("tdlib-jvm-resources"))
        }
        extensions.getByType<SourceSetContainer>().named("main") {
            resources.srcDir(stage)
        }

        configureTdlibPublishing(
            artifactId = platform.artifactId,
            pomName = "tdlib-kmp JVM runtime for ${platform.id}",
        )
    }
}

// One variant per OS/arch, plus an attribute-less "all" one that Gradle prefers when the
// consumer requests no platform (skiko's skiko-awt-runtime scheme).
abstract class TdlibJvmRuntimePlugin @Inject constructor(
    private val componentFactory: SoftwareComponentFactory,
) : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("base")
        pluginManager.apply("maven-publish")
        pluginManager.apply("com.vanniktech.maven.publish.base")

        val component = componentFactory.adhoc("tdlibJvmRuntime")

        TdlibJvmPlatform.entries.forEach { platform ->
            val variant = runtimeVariant("${platform.id}RuntimeElements", listOf(platform)) {
                attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named(platform.gradleOs))
                attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(platform.gradleArch))
            }
            // No Maven scope: Maven cannot select by platform, the POM lists only the "all" variant.
            component.addVariantsFromConfiguration(variant) {}
        }
        val all = runtimeVariant("allRuntimeElements", TdlibJvmPlatform.entries) {}
        component.addVariantsFromConfiguration(all) { mapToMavenScope("runtime") }

        extensions.getByType<PublishingExtension>().publications.create<MavenPublication>("maven") {
            from(component)
        }
        configureTdlibPublishing(
            artifactId = "$TDLIB_ARTIFACT_PREFIX-jvm-runtime",
            pomName = "tdlib-kmp JVM runtime",
        )
    }

    private fun Project.runtimeVariant(
        name: String,
        platforms: List<TdlibJvmPlatform>,
        platformAttributes: org.gradle.api.attributes.AttributeContainer.() -> Unit,
    ): Configuration = configurations.create(name) {
        isCanBeConsumed = true
        isCanBeResolved = false
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
            attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
            platformAttributes()
        }
        platforms.forEach { platform ->
            dependencies.add(project.dependencies.project(mapOf("path" to ":tdlib-jvm-runtime:${platform.id}")))
        }
    }
}

fun Project.configureTdlibPublishing(artifactId: String, pomName: String) {
    group = TDLIB_GROUP_ID
    version = providers.gradleProperty("tdlib.version").get()
    extensions.configure<MavenPublishBaseExtension> {
        publishToMavenCentral()
        // Only CI has the key; publishToMavenLocal must work without it.
        if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
        coordinates(TDLIB_GROUP_ID, artifactId, version.toString())
        pom {
            name.set(pomName)
            description.set("TDLib (Telegram Database Library) for Kotlin Multiplatform")
            inceptionYear.set("2026")
            url.set("https://github.com/xephosbot/tdlib-kmp")
            licenses {
                license {
                    name.set("Boost Software License 1.0")
                    url.set("https://www.boost.org/LICENSE_1_0.txt")
                    distribution.set("https://www.boost.org/LICENSE_1_0.txt")
                }
            }
            developers {
                developer {
                    id.set("xephosbot")
                    name.set("xephosbot")
                    email.set("xephosbot@gmail.com")
                    url.set("https://github.com/xephosbot/")
                    organization.set("xephosbot")
                    organizationUrl.set("https://github.com/xephosbot/")
                }
            }
            scm {
                url.set("https://github.com/xephosbot/tdlib-kmp/")
                connection.set("scm:git:git://github.com/xephosbot/tdlib-kmp.git")
                developerConnection.set("scm:git:ssh://git@github.com/xephosbot/tdlib-kmp.git")
            }
        }
    }
}
