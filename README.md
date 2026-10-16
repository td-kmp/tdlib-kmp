# tdlib-kmp

[![Maven Central](https://img.shields.io/maven-central/v/io.github.xephosbot/tdlib-kmp?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.xephosbot/tdlib-kmp)

[TDLib](https://github.com/tdlib/td) for Kotlin Multiplatform: Android, JVM (macOS, Linux, Windows), iOS, macOS, Linux, Windows (mingw).
Version is `<TDLib>-<library>`.

```toml
[versions]
tdlib = "1.8.68-1.0.0-alpha01"

[libraries]
tdlib = { module = "io.github.xephosbot:tdlib-kmp", version.ref = "tdlib" }
tdlib-client = { module = "io.github.xephosbot:tdlib-kmp-client", version.ref = "tdlib" }
```

```kotlin
commonMain.dependencies {
    implementation(libs.tdlib.client) // typed td_api + coroutine client; libs.tdlib alone is the raw JSON API
}
```

```kotlin
val client = TdClient.create()
launch { client.updatesOf<UpdateNewMessage>().collect { println(it.message) } }

client.send(SetTdlibParameters(databaseDirectory = "tdlib", apiId = API_ID, apiHash = API_HASH, /* ... */))
val me: User = client.send(GetMe)
client.close()
```
