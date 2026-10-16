package io.xbot.tdlib

import io.xbot.tdlib.cinterop.td_create_client_id
import io.xbot.tdlib.cinterop.td_execute
import io.xbot.tdlib.cinterop.td_receive
import io.xbot.tdlib.cinterop.td_send
import io.xbot.tdlib.cinterop.td_set_log_message_callback
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlin.concurrent.Volatile

@OptIn(ExperimentalForeignApi::class)
internal actual object NativeBridge {
    actual fun createClientId(): Int = td_create_client_id()
    actual fun send(clientId: Int, request: String) = td_send(clientId, request)
    actual fun receive(timeout: Double): String? = td_receive(timeout)?.toKString()
    actual fun execute(request: String): String? = td_execute(request)?.toKString()

    actual fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?) {
        logMessageHandler = handler
        if (handler == null) {
            td_set_log_message_callback(0, null)
        } else {
            td_set_log_message_callback(maxVerbosityLevel, staticCFunction(::onLogMessage))
        }
    }
}

// A C callback cannot capture state, so the handler lives in a global.
@Volatile
private var logMessageHandler: TdLogMessageHandler? = null

@OptIn(ExperimentalForeignApi::class)
private fun onLogMessage(verbosityLevel: Int, message: CPointer<ByteVar>?) {
    logMessageHandler?.onLogMessage(verbosityLevel, message?.toKString().orEmpty())
}
