plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("tdlib") {
            id = "tdlib-convention"
            implementationClass = "TdlibConventionPlugin"
        }
        register("tdlibJvmRuntime") {
            id = "tdlib-jvm-runtime"
            implementationClass = "TdlibJvmRuntimePlugin"
        }
        register("tdlibJvmRuntimePlatform") {
            id = "tdlib-jvm-runtime-platform"
            implementationClass = "TdlibJvmRuntimePlatformPlugin"
        }
    }
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.vanniktech.maven.publish.plugin)
    // td-pack ships .tar.zst; Gradle's tarTree only knows gzip/bzip2.
    implementation(libs.aircompressor)
    implementation(libs.commons.compress)
}
