# tdlib-kmp

Kotlin Multiplatform library wrapping [TDLib](https://github.com/tdlib/td) (Telegram Database Library).

Architecture inspired by [JetBrains/skiko](https://github.com/JetBrains/skiko).

## Supported platforms

| Platform | Target | Linking |
|----------|--------|---------|
| Android  | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` | JNI shared (`libtdjsonjava.so`, bundled in the AAR) |
| JVM      | Windows x64/arm64, Linux x64/arm64, macOS x64/arm64 | JNI shared, per-platform runtime artifacts (extracted at runtime) |
| iOS      | `arm64`, `simulatorArm64` | cinterop + static |
| macOS    | `arm64`, `x64` | cinterop + static |
| Linux    | `x64`, `arm64` | cinterop + static |

## Project structure

```
tdlib-kmp/
├── build.gradle.kts                    — Root project (plugin declarations)
├── settings.gradle.kts                 — Includes :tdlib submodule
├── build-logic/src/main/kotlin/
│   ├── TdlibPlatform.kt               — Platform enums, path helpers (nativeLocalDir / jniLocalDir / jvmResourceDir)
│   ├── TdlibDependencies.kt           — Artifact download & extract tasks
│   ├── TdlibProjectContext.kt         — Central project context
│   ├── TdlibConventionPlugin.kt       — Convention plugin wiring all targets
│   ├── TdlibNativeConfiguration.kt    — Native target config (cinterop, static linking)
│   ├── TdlibJvmConfiguration.kt       — JVM/Android JNI staging & AAR packaging
│   ├── TdlibJvmNativesPublications.kt — Per-platform JVM natives publications (skiko-style)
│   ├── TdlibSourceHierarchy.kt        — Source set hierarchy
│   └── WriteTdlibCInteropDef.kt       — Dynamic .def file generation
├── tdlib/                              — Core library module
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/io/xbot/tdlib/
│       │   ├── TdLib.kt                — Public API
│       │   └── NativeBridge.kt         — expect declarations
│       ├── nativeMain/kotlin/io/xbot/tdlib/
│       │   └── NativeBridge.native.kt  — cinterop implementation
│       ├── jvmMain/kotlin/io/xbot/tdlib/
│       │   ├── NativeBridge.jvm.kt     — JNI implementation
│       │   └── TdLibLoader.kt          — Native lib extractor/loader
│       ├── androidMain/kotlin/io/xbot/tdlib/
│       │   └── NativeBridge.android.kt — Android JNI implementation
│       └── commonTest/kotlin/
│           └── TdLibTest.kt            — Shared tests
├── prebuilds/natives/                  — Extracted native artifacts (gitignored, survives ./gradlew clean)
│   ├── linux-x86_64[-jni]/             — Linux desktop & JNI libs
│   ├── macos-arm64[-jni]/              — macOS desktop & JNI libs
│   ├── windows-x64-jni/               — Windows JNI libs
│   ├── ios-arm64[-simulator]/          — iOS static libs
│   └── android-{abi}/                  — Android .so files
└── .github/workflows/
    ├── build.yml                        — CI build
    └── test.yml                         — CI test on PR
```

## Usage

```kotlin
dependencies {
    implementation("io.github.xephosbot:tdlib-kmp:<version>")
}
```

### JVM desktop: add the natives artifact

The `tdlib-kmp-jvm` JAR contains no native libraries. Add the runtime artifact
for the platform(s) you ship — the same model as Compose Desktop's
`desktop-jvm-<platform>` / skiko's `skiko-awt-runtime-<os>-<arch>`:

```kotlin
dependencies {
    implementation("io.github.xephosbot:tdlib-kmp:<version>")
    // pick the platform(s) you distribute to:
    runtimeOnly("io.github.xephosbot:tdlib-kmp-jvm-natives-macos-arm64:<version>")
}
```

To resolve the current build host automatically (equivalent of the deprecated
`compose.desktop.currentOs`):

```kotlin
val osName = System.getProperty("os.name")
val hostOs = when {
    osName.startsWith("Mac") -> "macos"
    osName.startsWith("Win") -> "windows"
    else -> "linux"
}
val hostArch = when (System.getProperty("os.arch")) {
    "aarch64", "arm64" -> "arm64"
    else -> "x64"
}

dependencies {
    runtimeOnly("io.github.xephosbot:tdlib-kmp-jvm-natives-$hostOs-$hostArch:<version>")
}
```

For a single artifact bundling every desktop platform (larger, but
distribution-agnostic) use `tdlib-kmp-jvm-natives-all`.

### API

```kotlin
import io.xbot.tdlib.TdLib

val clientId = TdLib.createClientId()
TdLib.send(clientId, """{"@type":"getOption","name":"version"}""")
val response = TdLib.receive(10.0)
val syncResult = TdLib.execute("""{"@type":"getOption","name":"version"}""")
```

## Building

```bash
# Build all targets (native artifacts are downloaded and extracted lazily on first build)
./gradlew :tdlib:build
```

Extracted artifacts are cached in `prebuilds/natives/` at the project root and **survive
`./gradlew clean`**.

Every downloaded archive is verified against the SHA-256 checksums pinned in
`gradle/tdlib-natives.sha256`. After bumping `tdlib.version` in `gradle.properties`,
refresh and commit the checksums:

```bash
./gradlew updateTdlibNativesChecksums
```

## TDLib version

Current: **1.8.65** (from [xephosbot/td-pack](https://github.com/xephosbot/td-pack/releases))
