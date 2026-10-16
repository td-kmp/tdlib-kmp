# tdlib-kmp

Kotlin Multiplatform library wrapping [TDLib](https://github.com/tdlib/td) (Telegram Database Library).

The natives are built by [xephosbot/td-pack](https://github.com/xephosbot/td-pack) and
published there as release assets; this project wraps them into Kotlin Multiplatform
artifacts (the split JetBrains uses for skia-pack / skiko).

## Usage

```kotlin
dependencies {
    implementation("io.github.xephosbot:tdlib-kmp:<version>")
}
```

```kotlin
import io.xbot.tdlib.TdLib

TdLib.execute("""{"@type":"setLogVerbosityLevel","new_verbosity_level":1}""")
val clientId = TdLib.createClientId()
TdLib.send(clientId, """{"@type":"getOption","name":"version"}""")
val response = TdLib.receive(10.0)
TdLib.setLogMessageHandler(2) { level, message -> println("[$level] $message") }
```

Close every client (`close`, then wait for `authorizationStateClosed`) before the process
exits: process teardown otherwise destroys OpenSSL under TDLib's running threads.

## Platforms

| Platform | Targets | Natives |
|---|---|---|
| Android | `arm64-v8a` `armeabi-v7a` `x86_64` `x86` | `libtdjsonjava.so` in the AAR's `jni/` |
| JVM | macOS / Linux / Windows × x64 / arm64 | `tdlib-kmp-jvm-runtime-<os>-<arch>` jars (below) |
| iOS | `iosArm64` `iosSimulatorArm64` `iosX64` | `libtdjson.a` inside the cinterop klib |
| macOS | `macosArm64` `macosX64` | ″ |
| Linux | `linuxX64` `linuxArm64` | ″ |
| Windows | `mingwX64` | ″ |

Kotlin/Native: the klib carries one prelinked `libtdjson.a` (TDLib + OpenSSL + zlib); nothing
else is needed to link a binary or framework.

### JVM natives

As in skiko, each desktop platform's JNI library is its own artifact,
`tdlib-kmp-jvm-runtime-<os>-<arch>`, and `tdlib-kmp-jvm` depends on the umbrella
`tdlib-kmp-jvm-runtime`, whose Gradle variants select one by the standard
`OperatingSystemFamily` / `MachineArchitecture` attributes:

- **without** those attributes on your runtime classpath, Gradle picks the variant that
  depends on all six platforms (≈ 45 MB): works anywhere, including fat jars;
- **with** them, you get only the requested platform:

```kotlin
configurations.named("jvmRuntimeClasspath") { // or "runtimeClasspath" in a JVM project
    attributes {
        attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named(OperatingSystemFamily.MACOS))
        attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(MachineArchitecture.ARM64))
    }
}
```

Maven consumers get all six. At runtime the library is found, in order, in the
`tdlib.library.path` system property's directory, in the JVM's own library directory
(`java.home`/`lib`, `bin` on Windows — where packaged apps put natives), or extracted from
the runtime jar into a cache directory keyed by its SHA-256 (`tdlib.cache.dir`, default the
OS cache directory, e.g. `~/Library/Caches/tdlib-kmp`).

## Building

```bash
./gradlew :tdlib:jvmTest :tdlib:macosArm64Test       # tests on the host
./gradlew publishToMavenLocal                        # all artifacts
```

`tdlib.version` in `gradle.properties` is both the td-pack release used and the version
published. td-pack's archives are resolved from its GitHub release through an Ivy
repository (settings.gradle.kts), so Gradle downloads each once into its cache. To try
unreleased natives, point the build at td-pack's output:

```bash
./gradlew :tdlib:jvmTest -Ptdlib.packDir=../td-pack/out
```

### Layout

```
build-logic/src/main/kotlin/
  TdlibPlatform.kt              targets, JVM platforms, Android ABIs ↔ td-pack asset names
  TdlibNatives.kt               archive resolution (Ivy / tdlib.packDir) + extraction
  ExtractTdlibArchive.kt        .tar.zst extraction
  TdlibConventionPlugin.kt      :tdlib — cinterop, Android jniLibs, JVM test natives
  TdlibJvmRuntimePlugins.kt     :tdlib-jvm-runtime[:<os>-<arch>] — runtime jars + variants
  StageTdlibJvmNatives.kt       runtime jar resources (library + .sha256)
  CollectTdlibAndroidJniLibs.kt jniLibs tree for the AAR
tdlib/                          the library (src/nativeInterop/cinterop/tdjson.def)
tdlib-jvm-runtime/              JVM natives: umbrella + one project per platform
tdlib-client/                   [WIP] coroutine client on top of :tdlib
```
