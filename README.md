# tdlib-kmp

[![Maven Central](https://img.shields.io/maven-central/v/io.github.xephosbot/tdlib-kmp?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.xephosbot/tdlib-kmp)

[TDLib](https://github.com/tdlib/td) for Kotlin Multiplatform, with a typed `td_api` and a coroutine client.

| Platform | Targets | TDLib runs |
|---|---|---|
| Android | `android` (minSdk 24) | In the process, natives included |
| JVM | `jvm` on macOS, Linux, Windows | In the process, natives included |
| Apple | `iosArm64`, `iosSimulatorArm64`, `iosX64`, `macosArm64`, `macosX64` | In the process, linked statically |
| Linux, Windows | `linuxX64`, `linuxArm64`, `mingwX64` | In the process, linked statically |
| Web | `js`, `wasmJs` | In a Web Worker your application provides |

## Install

The version is `<TDLib>-<library>`.

```toml
[versions]
tdlib = "1.8.69-1.0.0-alpha02"

[libraries]
tdlib = { module = "io.github.xephosbot:tdlib-kmp", version.ref = "tdlib" }
tdlib-client = { module = "io.github.xephosbot:tdlib-kmp-client", version.ref = "tdlib" }
```

```kotlin
commonMain.dependencies {
    implementation(libs.tdlib.client) // typed td_api + TdClient; libs.tdlib alone is the raw JSON API
}
```

The generated `td_api` is large. If a build runs out of memory, raise the heap in `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx3g
kotlin.daemon.jvmargs=-Xmx3g
```

## Sign in

Get `apiId` and `apiHash` at [my.telegram.org](https://my.telegram.org). This runs on every target; only `tdLib` differs:
`NativeTdLib` everywhere except the web, [`WorkerTdLib`](#web) there.

```kotlin
suspend fun signIn(tdLib: TdLib, databaseDirectory: String, askUser: suspend (String) -> String): TdClient {
    val client = TdClient.create(tdLib)
    client.send(GetOption(name = "version")) // TDLib starts an instance on its first request
    client.authorizationState.filterNotNull().first { state ->
        when (state) {
            is AuthorizationStateWaitTdlibParameters -> client.send(
                SetTdlibParameters(
                    databaseDirectory = databaseDirectory,
                    apiId = API_ID,
                    apiHash = API_HASH,
                    systemLanguageCode = "en",
                    deviceModel = "My device",
                    applicationVersion = "1.0",
                ),
            )
            is AuthorizationStateWaitPhoneNumber ->
                client.send(SetAuthenticationPhoneNumber(phoneNumber = askUser("Phone number")))
            is AuthorizationStateWaitCode ->
                client.send(CheckAuthenticationCode(code = askUser("Code")))
            is AuthorizationStateWaitPassword ->
                client.send(CheckAuthenticationPassword(password = askUser("Password")))
            else -> Unit
        }
        state is AuthorizationStateReady
    }
    return client
}
```

`databaseDirectory` is a writable path: on Android under `context.filesDir`, on the web under the worker's storage.

## Use the client

```kotlin
val me: User = client.send(GetMe)

scope.launch { client.updatesOf<UpdateNewMessage>().collect { println(it.message) } }

try {
    client.send(GetChat(chatId = 42))
} catch (e: TdException) {
    println("${e.code}: ${e.error.message}")
}

val entities = client.execute(GetTextEntities(text = "@telegram")) // the functions TDLib can run synchronously

client.close() // before the process exits, or data can be lost
```

- `td_api` is generated from [`td_api.tl`](https://github.com/tdlib/td/blob/master/td/generate/scheme/td_api.tl): functions and objects keep their names in `PascalCase`, fields in `camelCase`. The [TDLib docs](https://core.telegram.org/tdlib/getting-started) apply as they are.
- Subscribe to `updates` before the first `send`. `authorizationState` always holds the latest state.
- Each `TdClient.create` is a separate TDLib instance, so give each its own `databaseDirectory`.
- Without `tdlib-client`, `TdLib` is TDLib's JSON interface: `createClientId`, `send`, `receive`, `execute`.

## Web

`WorkerTdLib` is a `TdLib` that communicates with a [Web Worker](https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API), so TDLib's work stays off the main thread. To instantiate it, a worker that implements a simple communication protocol is required. The protocol is described in the [`WorkerTdLib` KDoc](tdlib/src/webMain/kotlin/io/xbot/tdlib/WorkerTdLib.kt).

The library does not ship a worker, but this repository contains a [worker implementation](tdlib-web-worker-test/web-worker/worker.js) that you can copy into your project. It uses the `@td-kmp/tdlib-wasm` package from [td-pack](https://github.com/td-kmp/td-pack) and stores `/tdlib` in IndexedDB. The worker is added as a local npm package, thanks to [Kotlin's support for npm dependencies](https://kotlinlang.org/docs/using-packages-from-npm.html):

```
worker/
├── package.json   { "name": "tdlib-worker", "version": "0.0.0" }
└── worker.js
```

```kotlin
kotlin {
    js { browser(); useEsModules(); binaries.executable() }
    wasmJs { browser(); binaries.executable() }

    sourceSets {
        webMain.dependencies {
            implementation(npm("tdlib-worker", layout.projectDirectory.dir("worker").asFile))
            implementation(npm("@td-kmp/tdlib-wasm", "https://github.com/td-kmp/td-pack/releases/download/v1.8.69/tdlib-web.tgz"))
        }
    }
}
```

The version in that URL must be the `<TDLib>` part of this library's version: `td_api` is generated for one TDLib version, and updates it cannot decode are lost.

Once a worker is set up, using TDLib on the web is similar to other platforms:

```kotlin
suspend fun createClient(): TdClient = TdClient.create(WorkerTdLib(createWorker()))

@OptIn(ExperimentalWasmJsInterop::class)
fun createWorker(): Worker =
    js("""new Worker(new URL("tdlib-worker/worker.js", import.meta.url), { type: "module" })""")
```

The worker owns storage. The sample writes `/tdlib` to IndexedDB every 5 s and before a client is reported closed, so put `databaseDirectory` under it, for example `/tdlib/db`. Anything else, such as a `filesDirectory` outside it, stays in memory and is gone with the worker. Run one worker per storage at a time: two would erase each other's files.

In the browser TDLib talks to Telegram over WebSockets only and has no proxy support. The build does not use `SharedArrayBuffer`, so no COOP/COEP headers are needed.

## License

[Boost Software License 1.0](LICENSE)
