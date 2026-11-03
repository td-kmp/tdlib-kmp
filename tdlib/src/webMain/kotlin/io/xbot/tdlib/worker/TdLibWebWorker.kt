package io.xbot.tdlib.worker

import io.xbot.tdlib.TdLibUnavailableException
import kotlinx.coroutines.channels.Channel
import org.w3c.dom.Worker

internal external interface ReceiveResult : JsAny {
    val timeout: Double
}

private external interface ExecuteResult : JsAny {
    val result: String?
}

private fun eventCount(result: ReceiveResult): Int = js("result.events.length")
private fun eventAt(result: ReceiveResult, index: Int): String = js("result.events[index]")

internal fun ReceiveResult.events(): List<String> = List(eventCount(this)) { eventAt(this, it) }

/** The commands of the protocol documented on [io.xbot.tdlib.WorkerTdLib]. */
internal class TdLibWebWorker(worker: Worker) : CoroutineWebWorker(worker) {
    /** A signal that `receive` may have something new: a WebSocket event in the worker. */
    val wake = Channel<Unit>(Channel.CONFLATED)

    var logHandler: ((level: Int, message: String) -> Unit)? = null

    fun create() = sendRequest(WorkerCommand("create"))

    fun send(clientId: Int, request: String) = sendRequest(
        WorkerCommand("send") {
            this.clientId = clientId
            this.request = request
        },
    )

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    suspend fun receive(): ReceiveResult = awaitRequest(WorkerCommand("receive")) as ReceiveResult

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    suspend fun execute(request: String): String? =
        (awaitRequest(WorkerCommand("execute") { this.request = request }) as ExecuteResult).result

    fun setLogMessageHandler(maxVerbosityLevel: Int, enabled: Boolean) = sendRequest(
        WorkerCommand("setLogMessageHandler") {
            this.maxVerbosityLevel = maxVerbosityLevel
            this.enabled = enabled
        },
    )

    override fun onEvent(message: WorkerMessage) {
        val level = message.level
        when {
            message.wake == true -> wake.trySend(Unit)
            level != null -> logHandler?.invoke(level.toInt(), message.message.orEmpty())
        }
    }

    override fun onFailure(cause: TdLibUnavailableException) {
        wake.close(cause)
    }
}
