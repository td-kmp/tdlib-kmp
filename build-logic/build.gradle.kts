plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("tdlib") {
            id = libs.plugins.tdlib.convention.get().pluginId
            implementationClass = "TdlibConventionPlugin"
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-plugin", libs.versions.kotlin.get()))
    implementation(libs.android.gradle.plugin)
    implementation(libs.commons.compress)
    implementation(libs.zstd.jni)
}
