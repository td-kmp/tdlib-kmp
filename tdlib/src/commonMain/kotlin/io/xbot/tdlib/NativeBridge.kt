package io.xbot.tdlib

internal expect object NativeBridge {
    fun createClientId(): Int
    fun send(clientId: Int, request: String)
    fun receive(timeout: Double): String?
    fun execute(request: String): String?
    fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?)
}
