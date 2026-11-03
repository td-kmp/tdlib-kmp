// Not published: the worker this repository's web tests run on, and the sample for applications.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js {
        browser()
        useEsModules()
    }
    wasmJs {
        browser()
    }

    compilerOptions {
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    sourceSets {
        webMain.dependencies {
            api(libs.kotlinx.browser)
            implementation(npm("tdlib-worker", layout.projectDirectory.dir("web-worker").asFile))
        }
    }
}
