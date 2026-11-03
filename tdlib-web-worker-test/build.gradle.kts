// Not published: the worker this repository's web tests run on, and the sample for applications.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.tdlib.convention)
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
            implementation(npm("tdlib-test-worker", layout.projectDirectory.dir("web-worker").asFile))
            implementation(tdlibWasmNpm(project))
        }
    }
}
