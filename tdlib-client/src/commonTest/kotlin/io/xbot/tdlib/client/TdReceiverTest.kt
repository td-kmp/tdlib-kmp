package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib
import io.xbot.tdlib.TdLogMessageHandler
import io.xbot.tdlib.api.GetOption
import io.xbot.tdlib.api.OptionValueString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Answers every request at once with the option's name, as TDLib frames an answer.
private class EchoTdLib : TdLib {
    val extras = mutableListOf<Long>()
    private val events = Channel<String>(Channel.UNLIMITED)

    override fun createClientId(): Int = 1
    override fun send(clientId: Int, request: String) {
        val extra = checkNotNull(request.trailingExtra())
        extras += extra
        val name = request.substringAfter("\"name\":\"").substringBefore('"')
        events.trySend("""{"@type":"optionValueString","value":"$name","@extra":$extra,"@client_id":$clientId}""")
    }
    override suspend fun receive(timeout: Duration): String? = withTimeoutOrNull(timeout) { events.receive() }
    override suspend fun execute(request: String): String? = null
    override fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?) = Unit
}

class TdReceiverTest {
    @Test
    fun readsExtraAndClientIdOffTheEnd() {
        assertEquals(7L, """{"@type":"ok","@extra":7,"@client_id":2}""".trailingExtra())
        assertEquals(2, """{"@type":"ok","@extra":7,"@client_id":2}""".trailingClientId())
        val update = """{"@type":"updateOption","name":"a,\"@extra\":5","value":{"@type":"optionValueEmpty"},"@client_id":3}"""
        assertEquals(null, update.trailingExtra())
        assertEquals(3, update.trailingClientId())
    }

    @Test
    fun concurrentRequestsReachTdLibInTheirOrder() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) {
                val tdLib = EchoTdLib()
                val client = TdClient.create(tdLib)
                val names = (1..200).map { "option$it" }
                val answers = names.map { async { client.send(GetOption(name = it)) } }.awaitAll()
                assertEquals(names, answers.map { (it as OptionValueString).value })
                assertEquals(tdLib.extras.sorted(), tdLib.extras)
            }
        }
    }
}
