package io.xbot.tdlib.client

import io.xbot.tdlib.TdLibUnavailableException
import io.xbot.tdlib.api.AuthorizationStateClosed
import io.xbot.tdlib.api.GetOption
import kotlinx.coroutines.async
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TdClientFailureTest {
    @Test
    fun deadTdLibFailsRequestsAndClosesClients() = fakeTdLibTest(noAnswer) { tdLib ->
        val client = TdClient.create(tdLib)
        val request = async { runCatching { client.send(GetOption(name = "version")) } }
        tdLib.sent.receive()

        tdLib.stop()

        assertFailsWith<TdLibUnavailableException> { request.await().getOrThrow() }
        client.close()
        assertIs<AuthorizationStateClosed>(client.authorizationState.value)
    }
}
