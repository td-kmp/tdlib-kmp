package io.xbot.tdlib

// td_jni.cpp binds the natives and LogMessageHandler.onLogMessage by name: keep the names (consumer-rules.pro).
internal actual object NativeBridge {
    init {
        TdLibLoader.load()
    }

    actual fun createClientId(): Int = nativeCreateClientId()
    actual fun send(clientId: Int, request: String): Unit = nativeSend(clientId, request)
    actual fun receive(timeout: Double): String? = nativeReceive(timeout)
    actual fun execute(request: String): String? = nativeExecute(request)
    actual fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?) =
        nativeSetLogMessageHandler(maxVerbosityLevel, handler?.let { LogMessageHandler(it::onLogMessage) })

    fun interface LogMessageHandler {
        fun onLogMessage(verbosityLevel: Int, message: String)
    }

    @JvmStatic private external fun nativeCreateClientId(): Int
    @JvmStatic private external fun nativeSend(clientId: Int, request: String)
    @JvmStatic private external fun nativeReceive(timeout: Double): String?
    @JvmStatic private external fun nativeExecute(request: String): String?
    @JvmStatic private external fun nativeSetLogMessageHandler(maxVerbosityLevel: Int, handler: LogMessageHandler?)
}
