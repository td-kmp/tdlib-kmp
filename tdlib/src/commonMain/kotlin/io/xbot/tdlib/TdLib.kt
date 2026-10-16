package io.xbot.tdlib

object TdLib {
    fun createClientId(): Int = NativeBridge.createClientId()
    fun send(clientId: Int, request: String): Unit = NativeBridge.send(clientId, request)
    fun receive(timeout: Double): String? = NativeBridge.receive(timeout)
    fun execute(request: String): String? = NativeBridge.execute(request)

    /** The handler is called synchronously on TDLib's own threads; `null` removes it. */
    fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?): Unit =
        NativeBridge.setLogMessageHandler(maxVerbosityLevel, handler)
}

fun interface TdLogMessageHandler {
    fun onLogMessage(verbosityLevel: Int, message: String)
}
