package io.xbot.tdlib

import kotlin.time.Duration

/**
 * TDLib's JSON interface (`td_json_client.h`) as one API for every target.
 *
 * [createClientId] and [send] are synchronous everywhere. [receive] and [execute] suspend because on the
 * web TDLib runs in a Web Worker; on the other targets they return at once or block only their own thread.
 *
 * Implementations: [NativeTdLib] (JVM, Android, Kotlin/Native: TDLib linked into the process) and
 * [WorkerTdLib] (Kotlin/JS, Kotlin/Wasm: TDLib in a Web Worker the application creates).
 */
public interface TdLib {
    /** A new TDLib instance. It sends nothing until its first [send]. */
    public fun createClientId(): Int

    /** Requests reach TDLib in order; answers come back through [receive] with the request's `@extra`. */
    public fun send(clientId: Int, request: String)

    /**
     * The next update or answer of any client (with `@client_id`), or null after [timeout].
     * Like `td_receive`, only one caller may wait at a time.
     *
     * @throws TdLibUnavailableException when TDLib is gone (web: the worker failed or TDLib aborted).
     */
    public suspend fun receive(timeout: Duration): String?

    /** Runs one of the functions TDLib documents as "Can be called synchronously" (getTextEntities, setLogVerbosityLevel, ...). */
    public suspend fun execute(request: String): String?

    /** `null` removes the handler. Called on TDLib's own thread on native targets, on the page's on the web. */
    public fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?)
}

public fun interface TdLogMessageHandler {
    public fun onLogMessage(verbosityLevel: Int, message: String)
}

/** TDLib cannot serve requests anymore: its Web Worker failed to load, or TDLib aborted. */
public class TdLibUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
