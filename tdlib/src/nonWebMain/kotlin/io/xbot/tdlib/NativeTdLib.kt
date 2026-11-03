package io.xbot.tdlib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** TDLib linked into the process (JNI on the JVM and Android, cinterop on Kotlin/Native): one per process, like `td_receive`. */
public object NativeTdLib : TdLib {
    // td_receive blocks and must not run on two threads at once.
    private val receiver = Dispatchers.IO.limitedParallelism(1, "tdlib-receiver")

    override fun createClientId(): Int = NativeBridge.createClientId()

    override fun send(clientId: Int, request: String): Unit = NativeBridge.send(clientId, request)

    override suspend fun receive(timeout: Duration): String? =
        withContext(receiver) { NativeBridge.receive(timeout.toDouble(DurationUnit.SECONDS)) }

    override suspend fun execute(request: String): String? = NativeBridge.execute(request)

    override fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?): Unit =
        NativeBridge.setLogMessageHandler(maxVerbosityLevel, handler)
}
