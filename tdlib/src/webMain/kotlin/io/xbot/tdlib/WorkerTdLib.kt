package io.xbot.tdlib

import io.xbot.tdlib.worker.TdLibWebWorker
import io.xbot.tdlib.worker.events
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.w3c.dom.Worker
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * TDLib in a Web Worker the application provides, the way `WebWorkerSQLiteDriver` works in Room. It takes over
 * `worker.onmessage` and `worker.onerror`. The worker's TDLib must be the version in this library's version,
 * `<TDLib>-<library>`: `td_api` is generated for one version and updates it cannot decode are lost.
 *
 * The worker loads the npm package `@td-kmp/tdlib-wasm` and answers the commands below.
 * `tdlib-web-worker-test/web-worker/worker.js` in this repository is a complete one to copy. It also owns storage:
 * what is mounted where, and when it is written, is the application's. Paths in `setTdlibParameters` are paths in
 * the worker's file system.
 *
 * Requests are `{ id, data: { cmd, ... } }`, answered with `{ id, data: { ... } }` or `{ id, error }`. Without an `id` a
 * request has no answer. The worker reports a failure with `{ error }`, after which every call throws
 * [TdLibUnavailableException].
 *
 * | `cmd` | Fields | Worker does | Answer |
 * |---|---|---|---|
 * | `create` | | `createClientId()` | |
 * | `send` | `clientId`, `request` | `send(clientId, request)` | |
 * | `receive` | | `receive()` until `null`, then `getTimeout()` | `{ events: string[], timeout }` |
 * | `execute` | `request` | `execute(request)` | `{ result }` |
 * | `setLogMessageHandler` | `maxVerbosityLevel`, `enabled` | `setLogMessageHandler(...)`, posting `{ level, message }` | |
 *
 * The worker also posts `{ wake: true }` on every WebSocket event: TDLib only runs inside `receive`, so data from a
 * socket waits for the next one.
 */
public class WorkerTdLib(worker: Worker) : TdLib {
    private val tdWorker = TdLibWebWorker(worker)
    private val events = ArrayDeque<String>()
    private var nextClientId = 1
    private var receiving = false

    override fun createClientId(): Int {
        tdWorker.create()
        return nextClientId++
    }

    override fun send(clientId: Int, request: String) {
        tdWorker.send(clientId, request)
        tdWorker.wake.trySend(Unit)
    }

    override suspend fun receive(timeout: Duration): String? {
        check(!receiving) { "receive() is already waiting: TDLib allows one receiver at a time" }
        receiving = true
        try {
            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (true) {
                events.removeFirstOrNull()?.let { return it }
                // Events must not be lost if the caller is cancelled while waiting for them.
                val result = withContext(NonCancellable) { tdWorker.receive() }
                events.addAll(result.events())
                if (events.isNotEmpty()) continue

                val left = -deadline.elapsedNow()
                if (left <= Duration.ZERO) return null
                // The timeout is a hint: work TDLib queues for itself does not show in it, so at least every second.
                val hint = if (result.timeout.isFinite()) {
                    result.timeout.seconds.coerceIn(1.milliseconds, 1.seconds)
                } else {
                    1.seconds
                }
                withTimeoutOrNull(minOf(left, hint)) {
                    tdWorker.wake.receive()
                }
            }
        } finally {
            receiving = false
        }
    }

    override suspend fun execute(request: String): String? = tdWorker.execute(request)

    override fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?) {
        tdWorker.logHandler = handler?.let { it::onLogMessage }
        tdWorker.setLogMessageHandler(maxVerbosityLevel, handler != null)
    }
}
