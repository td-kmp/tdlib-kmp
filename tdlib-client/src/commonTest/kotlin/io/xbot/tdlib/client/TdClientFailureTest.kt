package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib
import io.xbot.tdlib.TdLibUnavailableException
import io.xbot.tdlib.TdLogMessageHandler
import io.xbot.tdlib.api.AuthorizationStateClosed
import io.xbot.tdlib.api.GetOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private class DyingTdLib : TdLib {
    val sent = CompletableDeferred<Unit>()
    val death = CompletableDeferred<String?>()

    override fun createClientId(): Int = 1
    override fun send(clientId: Int, request: String) {
        sent.complete(Unit)
    }
    override suspend fun receive(timeout: Duration): String? = death.await()
    override suspend fun execute(request: String): String? = null
    override fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?) = Unit
}

class TdClientFailureTest {
    @Test
    fun deadTdLibFailsRequestsAndClosesClients() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) {
                val tdLib = DyingTdLib()
                val client = TdClient.create(tdLib)
                val request = async { runCatching { client.send(GetOption(name = "version")) } }
                tdLib.sent.await()

                val cause = TdLibUnavailableException("worker died")
                tdLib.death.completeExceptionally(cause)

                assertFailsWith<TdLibUnavailableException> { request.await().getOrThrow() }
                client.close()
                assertIs<AuthorizationStateClosed>(client.authorizationState.value)
            }
        }
    }
}
