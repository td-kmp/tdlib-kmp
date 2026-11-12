package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib
import io.xbot.tdlib.TdLibUnavailableException
import io.xbot.tdlib.TdLogMessageHandler
import io.xbot.tdlib.api.TdJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal typealias Answer = (request: JsonObject, clientId: Int) -> String?

internal val JsonObject.extra: Long get() = getValue("@extra").jsonPrimitive.long

internal val JsonObject.name: String get() = getValue("name").jsonPrimitive.content

internal val okAnswer: Answer = { request, clientId ->
    """{"@type":"ok","@extra":${request.extra},"@client_id":$clientId}"""
}

internal val noAnswer: Answer = { _, _ -> null }

/** A TDLib of the test's own: it keeps what is sent, answers with [answer] and hands out the events it is given. */
internal class FakeTdLib(private val answer: Answer = okAnswer) : TdLib {
    val sent = Channel<JsonObject>(Channel.UNLIMITED)
    private val events = Channel<String>(Channel.UNLIMITED)
    private var nextClientId = 1

    override fun createClientId(): Int = nextClientId++

    override fun send(clientId: Int, request: String) {
        val json = TdJson.parseToJsonElement(request).jsonObject
        sent.trySend(json)
        answer(json, clientId)?.let(::emit)
    }

    override suspend fun receive(timeout: Duration): String? = withTimeoutOrNull(timeout) { events.receive() }

    override suspend fun execute(request: String): String? = null

    override fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?) = Unit

    fun emit(event: String) {
        events.trySend(event)
    }

    /** An update of [client], framed as TDLib frames one. */
    fun emitUpdate(client: TdClient, update: String) = emit("""${update.dropLast(1)},"@client_id":${client.id}}""")

    /** TDLib is gone: [receive] throws from now on. */
    fun stop() {
        events.close(TdLibUnavailableException("stopped"))
    }
}

// TdReceiver's loop runs on its own dispatcher: on real time, not runTest's virtual clock.
internal fun fakeTdLibTest(answer: Answer = okAnswer, body: suspend CoroutineScope.(FakeTdLib) -> Unit): TestResult =
    runTest {
        withContext(Dispatchers.Default) {
            withTimeout(60.seconds) {
                val tdLib = FakeTdLib(answer)
                try {
                    body(tdLib)
                } finally {
                    tdLib.stop()
                }
            }
        }
    }
