package io.xbot.tdlib

/**
 * The natives of libtdjsonjava. td_jni.cpp registers them on this class by name
 * (io/xbot/tdlib/NativeBridge) from JNI_OnLoad and calls
 * [LogMessageHandler.onLogMessage] by name: keep both names stable (consumer-rules.pro).
 */
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

    /** The callback type td_jni.cpp expects: NativeBridge$LogMessageHandler. */
    fun interface LogMessageHandler {
        fun onLogMessage(verbosityLevel: Int, message: String)
    }

    @JvmStatic private external fun nativeCreateClientId(): Int
    @JvmStatic private external fun nativeSend(clientId: Int, request: String)
    @JvmStatic private external fun nativeReceive(timeout: Double): String?
    @JvmStatic private external fun nativeExecute(request: String): String?
    @JvmStatic private external fun nativeSetLogMessageHandler(maxVerbosityLevel: Int, handler: LogMessageHandler?)
}
