# Web (js + wasmJs) target: design

> Superseded in part (2026-11-03): the current shape is Room's. `WorkerTdLib(worker)` in `tdlib` forwards the `TdLib` calls
> over a documented protocol and polls in `receive`. The application owns the worker and its storage. td-pack ships only the
> Emscripten module and a thin `index.mjs`. tdweb-style file storage is planned as a later layer in `tdlib-client`. Sections
> below describe earlier iterations where they differ.

Status: branch `feature/web-wasm-target`, now on the shape of §10.4: `WorkerTdLib` binds td-pack's
`client.mjs` through `@JsModule`, and the klib depends on `@td-kmp/tdlib-wasm` by td-pack's release
URL (`tdlib-web.tgz`), so no npm registry is involved (decision of 2026-11-03: GitHub releases are the
only infrastructure). `tdlib.version` is 1.8.69, the first td-pack release that carries the web
package. Facts confirmed while implementing are marked ✔.

## 1. What the web platform changes

TDLib compiled with Emscripten (`td/telegram/td_emscripten.cpp`, `tdutils/port/config.h`):

| Native / JVM | Web (Emscripten) |
|---|---|
| TDLib has its own threads; `td_receive(timeout)` blocks | `TD_THREAD_UNSUPPORTED`: no threads, `td_receive(0)` never blocks, `td_emscripten_get_timeout()` says when to poll next |
| The library is linked into the process and ready at once | `td_wasm.wasm` is fetched and instantiated asynchronously (`createTdlibModule()` returns a Promise) |
| TCP sockets, proxies | WebSockets to `*.web.telegram.org/apiws` (`apiws_test` for the test DC); `addProxy` unsupported |
| Real file system | MEMFS (volatile) or IDBFS (IndexedDB, needs explicit `syncfs`) |
| Runs on any thread | Must not run on the UI thread: TDLib does its crypto, JSON and DB work synchronously, so it belongs in a Web Worker |

Consequences for the Kotlin API:

* `execute` and `receive` cannot be synchronous on web (they cross a worker boundary).
* `createClientId` and `send` *can* stay synchronous: the page numbers clients itself and
  `postMessage` preserves order, so nothing waits for the wasm to load (td-pack prototype, see §3).
* Something must own the Worker. Like Room 3's `WebWorkerSQLiteDriver(worker: Worker)`, the
  application creates it and hands it over: bundlers need a statically analysable
  `new Worker(new URL(..., import.meta.url))` in the *app's* bundle, the app owns the worker's
  lifetime, and it can substitute a `SharedWorker` or a test double later.

## 2. Target layering

```
tdlib-client (commonMain)      TdClient, TdReceiver          — depend only on the TdLib interface
tdlib        (commonMain)      interface TdLib, TdLogMessageHandler
  ├─ nonWebMain                NativeTdLib : TdLib (singleton)   jvmAndroid (JNI) + native (cinterop)
  │    ├─ jvmAndroidMain       NativeBridge (JNI), TdLibLoader
  │    └─ nativeMain           NativeBridge (cinterop)
  └─ webMain                   WorkerTdLib(worker) : TdLib        js + wasmJs
       ├─ jsMain               tiny js() helpers for message objects
       └─ wasmJsMain           tiny js() helpers for message objects
td-pack (web target)           npm @td-kmp/tdlib-wasm: worker.mjs + td_wasm.js + td_wasm.wasm
```

Hierarchy template additions (`build-logic/TdlibSourceHierarchy.kt`): a `nonWeb` group wrapping the
existing `jvmAndroid` and `native` groups, plus `group("web") { withJs(); withWasmJs() }` (the same
group KGP 2.4.20's default template uses, so the source set is `webMain`).

## 3. The `TdLib` interface (tdlib / commonMain)

`td_json_client.h` in Kotlin, with `suspend` where the web platform forces it:

```kotlin
public interface TdLib {
    /** Synchronous everywhere. TDLib starts the instance on the first [send]. */
    public fun createClientId(): Int
    /** Synchronous everywhere; requests reach TDLib in order. */
    public fun send(clientId: Int, request: String)
    /** The next event of any client, or null after [timeout]. One receiver at a time (td_receive). */
    public suspend fun receive(timeout: Duration): String?
    /** Functions documented "Can be called synchronously"; a round trip to the worker on web. */
    public suspend fun execute(request: String): String?
    public fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?)
}
```

Why pull-based `receive` and not a `Flow<String>`: it is 1:1 with the C API, `TdReceiver` stays
one platform-agnostic loop, and the web prototype already implements exactly these semantics.
Lifetime stays with the caller: no `close()` on the interface; TDLib instances are closed with the
`close` request as today, the Worker is the application's.

### 3.1 Non-web: `NativeTdLib` (nonWebMain) ✔

`public object NativeTdLib : TdLib` — a singleton because `td_receive` serves every client of the
process. `receive` runs `NativeBridge.receive` on `Dispatchers.IO.limitedParallelism(1)` (no two
calls at once, no permanent thread; `td_receive` needs no thread affinity, only the returned pointer
is consumed at once); `execute` calls `NativeBridge.execute` directly. Extra non-suspend
`executeSync(request)` for the JVM/native convenience the old API offered. `TdLibLoader` is unchanged.
`tdlib` now depends on kotlinx-coroutines-core (`api`).

### 3.2 Web: `WorkerTdLib` (webMain)

`public fun TdLib(worker: Worker): TdLib` and `public class WorkerTdLib(worker: Worker)`, both in
`webMain`. ✔ kotlinx-browser 0.5.0 declares `org.w3c.dom.Worker`, `MessageEvent` and
`postMessage(JsAny?)` in its own `webMain`, and plain `external interface ... : JsAny` declarations
compile in our `webMain` for both js and wasmJs (Kotlin 2.4.20). Only two helpers are per platform:
`newWorkerRequest()` (`js("({})")`) and `WorkerMessage.eventList()`. The whole module opts in to
`kotlin.js.ExperimentalWasmJsInterop`.

It is a Kotlin port of the prototype's `index.mjs`:

* `createClientId()`: `id = next++`, post `create`; the worker maps page ids to TDLib ids if they ever differ.
* `send()`: post `send` (the worker polls right after).
* `receive(timeout)`: return the queued event if any; otherwise install the single waiter and
  `withTimeoutOrNull(timeout)`; a second concurrent receiver is an error (same as td_receive).
* `execute()`: `CompletableDeferred` keyed by an id, post `execute`, await.
* Incoming: `events` (batch of JSON strings) → queue / resolve waiter; `executed` → complete;
  `log` → handler; `error` (wasm failed to load, TDLib aborted) → fail every pending and future call
  with `TdLibUnavailableException`.

Worker protocol (page → worker): `create{clientId}`, `send{clientId, request}`, `execute{id, request}`,
`setLogMessageHandler{maxVerbosityLevel, enabled}`, plus new `init{…}`; worker → page: `ready{version}`,
`events{events: string[]}`, `executed{id, result}`, `log{level, message}`, `error{message}`.
Messages are plain objects (structured clone); constructed by one `js()` helper per platform source
set, read through `external interface` declarations in webMain.

Version handshake ✔: the generated `td_api` in tdlib-client is for one TDLib version
(`tdlib.version` in gradle.properties). `WorkerTdLib` compares the worker's `ready.version` with
`io.xbot.tdlib.TDLIB_VERSION` (generated into `tdlib`'s commonMain by build-logic's
`GenerateTdLibVersion`) and fails the instance on a mismatch, since the npm package version is chosen
by the app, not by this library.

### 3.3 Application side (web)

```kotlin
// wasmJsMain / jsMain of the app
fun tdlibWorker(): Worker = js("""new Worker(new URL("@td-kmp/tdlib-wasm/worker.mjs", import.meta.url), { type: "module" })""")

val tdLib = TdLib(tdlibWorker())
val client = TdClient.create(tdLib)
```

```kotlin
// app build.gradle.kts
webMain.dependencies { implementation(npm("@td-kmp/tdlib-wasm", "1.8.68")) }
```

Only one TDLib per database at a time: two tabs writing the same IDBFS database corrupt it
(tdweb solves this with a BroadcastChannel election). Document it; a `SharedWorker` variant is the
real fix and fits the protocol since the page side only needs a `postMessage`/`onmessage` pair.

## 4. tdlib-client on top of the abstraction ✔

* `TdClient.create(tdLib: TdLib)` and `TdClient.execute(tdLib, function)` in commonMain.
  nonWebMain adds `TdClient.create()` and `TdClient.executeSync(function)` over `NativeTdLib`.
* `TdReceiver` becomes `internal class TdReceiver(tdLib: TdLib)`, one per `TdLib` instance
  (registry guarded by a `Mutex`; `synchronized` is not available on web). Its loop is unchanged in
  shape — `receive(1.seconds)`, dispatch by `@extra` / `@client_id`, stop when no clients remain —
  but runs on `Dispatchers.Default` and is now platform-independent.
* Failure path (implemented as the simple variant): when `receive` throws, pending requests complete
  exceptionally with the cause and every registered client gets a synthetic
  `AuthorizationStateClosed`, so `close()` and `authorizationState.first { Closed }` return. A
  dedicated `TdClient.failure` can be added if callers need the cause.
* `TdException`, typed `send`, `updates`, `authorizationState`: unchanged.

## 5. td-pack: the `web` target

The prototype exists in td-pack history as an orphan branch (`f5d6091` → `c3f246a` "web: run TDLib
in a Web Worker" → `92da032` "web: keep polling TDLib after events"), built on top of the `pack.sh`
rework; its CI runs on 2026-10-18 passed, including the `web` job with `TDPACK_NETWORK_TEST=1` (the
MTProto handshake with the test DC over `wss://` from headless Chrome). ✔ Restored as the patch
`git diff main 92da032 -- . ':!td'` on branch `feature/web` (`scripts/pack/web.sh`, `web/`,
`tests/web/`, `HAS_WEB` in `common.sh`, EMSDK 6.0.12 pinned). Changes on top:

1. Drop `index.mjs` from what Kotlin uses (its logic moves to `WorkerTdLib`); keep it in the npm
   package as the plain-JS API, so the worker protocol stays the public contract.
2. ✔ `worker.mjs`: `ready{version}` after the module loads; IDBFS mounted at `/tdlib` with
   `syncfs(true)` before the first message is handled; `syncfs(false)` every 5 s and on
   `authorizationStateClosed` (tdweb's `DbFileSystem`). `database_directory` / `files_directory`
   must live under `/tdlib`. No `init` message: the worker queues what arrives while loading.
3. Keep the prototype's polling rule: poll again at once after events and at least every second
   (`td_emscripten_get_timeout` does not cover work TDLib queues for itself, e.g. the steps of
   closing an instance).
4. Publish: GitHub release asset `tdlib-web.tar.zst` (for this repo's tests via the Ivy repo) and the
   npm package `@td-kmp/tdlib-wasm` on npmjs (for applications). Version = TDLib version.
5. Build flags stay: `-sMODULARIZE -sEXPORT_ES6 -sENVIRONMENT=web,worker -sWEBSOCKET_URL=wss:#`
   `-sALLOW_MEMORY_GROWTH -sALLOW_TABLE_GROWTH -lidbfs.js`, LTO `-Os`, `tests/web/smoke.mjs` in
   headless Chrome via CDP (`web-verify`).

## 6. Build logic in this repo

* ✔ `tdlib`, `tdlib-client`: `js { browser(); useEsModules() }` (`import.meta.url` in the tests
  needs ES modules) and `wasmJs { browser() }`, Karma on headless Chrome; `webMain` depends on
  `kotlinx-browser`; the test compilations get the worker package through
  `npm("@td-kmp/tdlib-wasm", <dir>)` where `<dir>` is the extracted td-pack archive
  (`TdlibNatives.extract("tdlib-web")`; KGP wants the directory to exist at configuration time, so
  it is created empty and filled by the extract task, on which the package.json and npm-install
  tasks depend; `-Ptdlib.packDir=../td-pack/out` for local work).
* `webpack.config.d/`: `.wasm` as `asset/resource` so webpack does not try to parse Emscripten's
  module as its own wasm; the dev server serves `application/wasm`.
* Tests: `TdLibTest` and `TdClientTest` become `runTest`-based against an `expect fun testTdLib(): TdLib`
  (`NativeTdLib` on non-web; `TdLib(worker)` on web, the worker URL in a `js()` function of the test
  source set). `testDatabaseDirectory()` on web returns `/tdlib/test-<random>`. CI adds a Chrome job.
* Publishing: no npm dependency in the published klibs (the app declares it), so Maven consumers see
  nothing new; `kotlinx-browser` becomes a normal Maven dependency of the web variants.

## 7. Phases

| # | Scope | Exit criterion |
|---|---|---|
| 0 | Spike: web targets compile with stubs; confirm `Worker`/`external interface` in webMain, `js()` helpers, Karma + worker in Chrome | A webTest that round-trips one message through a trivial worker on js and wasmJs |
| 1 | td-pack: `client.mjs` (`attach()`), IDBFS, version handshake, `.tgz` release asset | ✔ PR td-kmp/td-pack#41 merged (`a74c9f4`); release v1.8.69 published 2026-11-03 with `tdlib-web.tar.zst` and `tdlib-web.tgz`. No npm registry: the klib depends on the release `.tgz` URL; the whole test matrix passes against it |
| 2 | `tdlib`: `TdLib` interface, `NativeTdLib`, `WorkerTdLib`, tests on every target | ✔ `TdLibTest` green on JVM, macOS, js, wasmJs |
| 3 | `tdlib-client`: per-instance `TdReceiver`, `TdClient.create(tdLib)`, failure path | ✔ `TdClientTest` and `TdApiTest` green on JVM, macOS, js, wasmJs (auth flow reaches `WaitPhoneNumber` in the worker) |
| 4 | README, version handshake, release notes (API break: `TdLib.*` → `NativeTdLib.*`) | README and handshake ✔; publishing the alpha is next once td-pack v1.8.69 is out |
| later | Default worker convenience (`npm` dep inside the klib), `SharedWorker`, OPFS (needs WasmFS → pthreads → COOP/COEP), file blobs like tdweb's `inputFileBlob` | — |

## 8. Risks and open questions

* Kotlin/Wasm is Beta (2.4.20); wasmJs needs Wasm GC browsers (Chrome 119+, Firefox 120+, Safari 18.2+). js target covers the rest.
* `webMain` commonization of `JsAny`/externals is new (2.2.20+); the spike decides between shared externals and expect/actual.
* IDBFS: whole-file writes to IndexedDB on each sync; acceptable for the TDLib DB, measure.
* Single-threaded TDLib: heavy operations stall the worker (not the UI); no proxies; Telegram's `wss` endpoints only.
* Two tabs, one database → corruption without an election or a `SharedWorker`.
* `receive` on non-web now costs two thread hops per event (receiver thread ↔ `Dispatchers.Default`); batch-drain with `receive(0)` if it shows up in profiles.

## 9. Notes from the implementation

* Gradle with `FAIL_ON_PROJECT_REPOS`: KGP adds its own Ivy repositories for Node.js and Yarn, which
  that mode rejects. They are declared in `settings.gradle.kts` instead, and KGP's are switched off in
  the root build script through the env specs (`NodeJsEnvSpec` per project, `YarnRootEnvSpec` in the
  root; the `NodeJsRootExtension.downloadBaseUrl` var is deprecated).
* The npm package needs `"./worker.mjs"` in its `exports` map, or webpack refuses
  `new URL("@td-kmp/tdlib-wasm/worker.mjs", import.meta.url)`.
* Karma serves webpack's emitted assets (the worker chunk, `td_wasm.wasm`) without extra
  configuration; no `webpack.config.d` was needed.
* KGP's browser test reporter records ~0 s for every async test; failures after real suspension are
  reported (checked with a deliberately failing test), so the numbers are a reporter artifact.
* Emscripten's SDK needs Python ≥ 3.10; macOS ships 3.9, so `EMSDK_PYTHON=/opt/homebrew/bin/python3.13`
  (Homebrew `python@3.13`) for `./pack.sh web`.
* `td_wasm.wasm` is 18 MB (5.9 MB gzipped) with the prototype's `-Os` LTO link; the TDLib compile
  itself took 3 min and the LTO link 3 min on an M-series Mac.

## 10. Research: how TDLib is bound elsewhere, and what KMP can carry (2026-11-03)

### 10.1 TDLib bindings in other ecosystems

| Binding | FFI | Receive model | Typed API | Who ships TDLib |
|---|---|---|---|---|
| C++ `ClientManager` (`td/telegram/Client.h`) | — | `receive` from one thread at a time, `send` from any, `execute` sync | `td_api.h` | — |
| Java, TDLib's own (`example/java`) | JNI (`td_jni.cpp`) | daemon "TDLib thread" calling `nativeClientReceive(…, 100000.0)` with batches of up to 1000 events; handlers in static `ConcurrentHashMap`s keyed by query id and client id | `TdApi.java` + JNI (de)serialization generated by `tl_writer_java` / `tl_writer_jni_*`, no JSON | app |
| .NET, TDLib's own (`ClientDotNet.cpp`) | C++/CLI over `ClientManager::get_manager_singleton()->receive(300.0)` on a thread | handlers in `ConcurrentDictionary` | `tl_writer_dotnet` | app |
| Python example / aiotdlib | ctypes over tdjson | blocking `td_receive(1.0)` loop | generated from `td_api.tl` (aiotdlib) | aiotdlib bundles prebuilt binaries, pinned to one TDLib version |
| Swift example / TDLibKit | C | `DispatchQueue("TDLib")` loop with `td_receive(10)`; TDLibKit: "create only one manager, since td_receive can be only called from a single thread" | tl2swift from `td_api.tl` | TDLibFramework XCFramework (~300 MB) via SPM |
| Node `tdl` | N-API addon | promise `invoke`, `on('update')` / `iterUpdates()`; "can be used in only one thread" | TS types in `prebuilt-tdlib` | `prebuilt-tdlib` npm, per platform. "cannot be used in the browser"; "the official tdweb library is outdated" |
| Rust `tdlib-rs` | FFI | thread loop | `tdlib-tl-gen` from TL | `download-tdlib` (GitHub releases) / `local-tdlib` / `pkg-config` / `static` |
| Go `go-tdlib` | cgo | result-handler callback | generated from TL + JSON schema | consumer installs tdjson (friction) |
| Java `tdlight-java` | JNI | thread | generated | `tdlight-natives` artifacts by classifier + BOM (our `tdlib-jvm-runtime-<platform>` split) |
| Web `tdweb`, TDLib's own | `postMessage` | Worker polls `td_receive(0)` on timers / requests / socket events | none (plain JSON) | npm, Emscripten 3.1.1; rebuilt by DIBgram / awesomegram |

Same shape everywhere: a thin FFI to five functions, one receive loop with dispatch by `@extra` and
client id, a typed API generated from `td_api.tl`, and the *binding* ships the prebuilt TDLib. On
the web the FFI is `postMessage` and the loop is a poll in the Worker.

### 10.2 What TDLib hands out for the web

`td/telegram/td_emscripten.cpp` is the whole web "header": five `EMSCRIPTEN_KEEPALIVE` functions —
`td_emscripten_create_client_id`, `td_emscripten_send`, `td_emscripten_receive` (always `td_receive(0)`),
`td_emscripten_execute`, `td_emscripten_get_timeout` — plus `main` calling
`emscripten_exit_with_live_runtime`. `CMakeLists.txt` adds `MODULARIZE`, `EXPORT_NAME=createTdwebModule`,
`EXPORTED_RUNTIME_METHODS=['FS','cwrap']`, `-lidbfs.js -lworkerfs.js`, `WEBSOCKET_URL=wss:#`, and
`post.js` exposes `FS`. There is no `.d.ts`; `tdclientjson_export_list` is for the shared library. The
only declaration of a page-side API is td-pack's `index.d.ts`.

### 10.3 What a klib can carry on the web (KGP 2.4.20 sources, published klibs)

* **npm dependencies travel in the klib.** `kotlin-react-js.klib` has a root `package.json` with
  `"react": "^19.3.1"`; KGP: "The npm dependencies are specified in the `package.json` file that is
  packed into the `.klib`" (`GradleDependencies.kt`), read back by `GradleNodeModuleBuilder`, which
  also copies `.js`/`.mjs` files out of the klib into the imported node module. `PublicPackageJsonTask`
  removes every `file:` dependency, so a local-directory npm dependency never reaches consumers.
* **JS files can sit in the klib** (`skiko-js.klib`, `skiko-wasm-js.klib` carry `pre-setup.mjs`,
  `skikoCallbacks.js`; the archive task adds `processResources`). Useful for small glue, not for the
  binary.
* **Kotlin/Wasm emits `@JsModule` specifiers verbatim** into `<app>.import-object.mjs`
  (`import * as alias from "<specifier>"`, `WasmOutputGenerationPipelinePhase.kt`). A relative path
  resolves against the application's output, a bare name through `node_modules`. Skiko's
  `@file:JsModule("./skiko.mjs")` only works because the Compose Gradle plugin unpacks the
  `org.jetbrains.skiko` runtime jars into `build/compose/skiko-<target>-runtime` and feeds them to
  `processResources` (JS) or the link-sync task (Wasm).
* **`.wasm` and worker files reach an application only three ways:** an npm package the bundler
  resolves (`new URL(..., import.meta.url)`, `new Worker(new URL(...))`); a Gradle plugin that copies
  (Compose); or consumer wiring (SQLDelight: `CopyWebpackPlugin` for `sql-wasm.wasm`; Room: a local npm
  module `@androidx/sqlite-web-worker` depending on `@sqlite.org/sqlite-wasm`).
* **The cinterop analogy has a limit.** Our cinterop klib carries
  `default/targets/ios_arm64/included/libtdjson.a` (37 MB), so the binary travels with the klib. On
  the web the klib can carry the *reference* (the npm dependency) and the *declarations*, not a
  bundler-usable binary. `@WasmImport` ("imported into Wasm module without type adapters") does not
  change this: Emscripten's TDLib has its own memory and JS runtime (sockets, FS, timers) and must
  live in a Worker.
* **Generators:** Karakum turns `.d.ts` into Kotlin declarations (CLI + Gradle plugin, alpha). For a
  six-function API, hand-written externals diffed against `index.d.ts` in CI are simpler.
* ✔ A bare `@file:JsModule("@td-kmp/tdlib-wasm")` external block compiles in the shared `webMain`
  for js and wasmJs (probe, 2026-11-03).

### 10.4 The native-like shape for the web

```
td-pack  (the "header" and the "binary")        npm @td-kmp/tdlib-wasm@<TDLib version>
  td_wasm.js, td_wasm.wasm                       Emscripten module (ES6 factory)
  worker.mjs                                     reference worker: the protocol server
  client.mjs + index.d.ts                        page-side API: attach(worker) → { createClientId, send,
                                                 receive(timeout): Promise, execute: Promise, setLogMessageHandler }
tdlib-kmp (the bindings)
  webMain  @file:JsModule("@td-kmp/tdlib-wasm") externals mirroring index.d.ts
           webMain.dependencies { api(npm("@td-kmp/tdlib-wasm", TDLIB_VERSION)) }   ← klib pins the binary, like included/libtdjson.a
           WorkerTdLib(worker): Promise → suspend, JS rejection → TdLibUnavailableException
  TdReceiver / TdClient                          unchanged: the receive loop every binding has
consumer
  creates the Worker: new Worker(new URL("@td-kmp/tdlib-wasm/worker.mjs", import.meta.url), {type:"module"}),
  or its own npm worker module that imports @td-kmp/tdlib-wasm/td_wasm.js (SharedWorker, other FS, own build)
```

Compared with the current state: the protocol client moves from `WorkerTdLib` into td-pack's
`client.mjs` (today's `index.mjs` plus `attach(worker)`), Kotlin binds it, and the duplicated ~80
lines go away; the npm dependency moves into the klib, so the TDLib version is pinned at build time.
What stays: the `TdLib` interface, `TdReceiver`, the tests, the consumer-owned Worker.

Implemented as: the klib's `api(npm("@td-kmp/tdlib-wasm", "<td-pack release URL>/tdlib-web.tgz"))`
(npm and yarn accept tarball URLs as versions; KGP writes the string into the klib's `package.json`
verbatim and only strips `file:` versions, so consumers resolve it from GitHub releases). With
`-Ptdlib.packDir` the dependency is the extracted local archive instead. `WorkerTdLib` maps every JS
rejection or throw to `TdLibUnavailableException`; `attach(worker, TDLIB_VERSION)` makes the client
fail on a version mismatch. Kotlin/Wasm is Beta.
