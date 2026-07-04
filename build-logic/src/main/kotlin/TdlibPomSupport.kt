import org.gradle.api.publish.maven.MavenPom

/** Maven group id shared by every published tdlib-kmp artifact. */
const val TDLIB_GROUP_ID = "io.github.xephosbot"

/**
 * Fills in the project-wide POM metadata required by Maven Central
 * (name, description, url, license, developers, scm).
 */
fun MavenPom.configureTdlibPom(pomName: String, pomDescription: String) {
    name.set(pomName)
    description.set(pomDescription)
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
