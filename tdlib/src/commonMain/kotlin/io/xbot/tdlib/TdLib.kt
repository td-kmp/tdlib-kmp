package io.xbot.tdlib

/** TDLib's JSON interface (td_json_client.h) and log message hook (td_log.h). */
object TdLib {
    fun createClientId(): Int = NativeBridge.createClientId()
    fun send(clientId: Int, request: String): Unit = NativeBridge.send(clientId, request)
    fun receive(timeout: Double): String? = NativeBridge.receive(timeout)
    fun execute(request: String): String? = NativeBridge.execute(request)

    /**
     * Receives TDLib's log messages up to [maxVerbosityLevel] (td_set_log_message_callback).
     * The handler is called synchronously from TDLib's threads; `null` removes it.
     */
    fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?): Unit =
        NativeBridge.setLogMessageHandler(maxVerbosityLevel, handler)
}

/** A TDLib log message: verbosity level (0 = fatal) and text. */
fun interface TdLogMessageHandler {
    fun onLogMessage(verbosityLevel: Int, message: String)
}
