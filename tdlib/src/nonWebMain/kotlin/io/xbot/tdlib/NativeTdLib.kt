package io.xbot.tdlib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** TDLib linked into the process (JNI on the JVM and Android, cinterop on Kotlin/Native): one per process, like `td_receive`. */
public object NativeTdLib : TdLib {
    // td_receive must not run on two threads at once.
    private val receiving = Mutex()

    override fun createClientId(): Int = NativeBridge.createClientId()

    override fun send(clientId: Int, request: String): Unit = NativeBridge.send(clientId, request)

    // Taken on the caller's thread if queued (a hop was a fifth of a burst's time); only a wait blocks an IO thread.
    override suspend fun receive(timeout: Duration): String? = receiving.withLock {
        NativeBridge.receive(0.0)
            ?: withContext(Dispatchers.IO) { NativeBridge.receive(timeout.toDouble(DurationUnit.SECONDS)) }
    }

    override suspend fun execute(request: String): String? = NativeBridge.execute(request)

    override fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?): Unit =
        NativeBridge.setLogMessageHandler(maxVerbosityLevel, handler)
}
