package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib
import io.xbot.tdlib.api.SetOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private class HoldingTdLib(private val tdLib: FakeTdLib) : TdLib by tdLib {
    val holding = CompletableDeferred<Unit>()
    val released = CompletableDeferred<Unit>()

    override fun send(clientId: Int, request: String) {
        if (holding.complete(Unit)) runBlocking { released.await() }
        tdLib.send(clientId, request)
    }
}

class TdReceiverOrderTest {
    @Test
    fun requestsWaitingForTheirTurnAreSentInTheOrderTheyWereMade() = fakeTdLibTest { fake ->
        val tdLib = HoldingTdLib(fake)
        val client = TdClient.create(tdLib)
        val held = launch { client.send(SetOption(name = "held")) }
        tdLib.holding.await()
        val waiting = List(WAITING) {
            launch(start = CoroutineStart.UNDISPATCHED) { client.send(SetOption(name = "$it")) }
        }
        tdLib.released.complete(Unit)
        (waiting + held).joinAll()
        assertEquals(listOf("held") + List(WAITING) { "$it" }, List(WAITING + 1) { fake.sent.receive().name })
    }

    private companion object {
        const val WAITING = 20
    }
}
