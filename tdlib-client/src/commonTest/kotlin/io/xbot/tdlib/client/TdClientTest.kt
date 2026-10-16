package io.xbot.tdlib.client

import io.xbot.tdlib.api.AuthorizationStateClosed
import io.xbot.tdlib.api.AuthorizationStateWaitPhoneNumber
import io.xbot.tdlib.api.AuthorizationStateWaitTdlibParameters
import io.xbot.tdlib.api.Error
import io.xbot.tdlib.api.GetOption
import io.xbot.tdlib.api.GetTextEntities
import io.xbot.tdlib.api.OptionValueString
import io.xbot.tdlib.api.SetLogVerbosityLevel
import io.xbot.tdlib.api.SetTdlibParameters
import io.xbot.tdlib.api.TestReturnError
import io.xbot.tdlib.api.TextEntities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

expect fun testDatabaseDirectory(): String

class TdClientTest {
    init {
        TdClient.execute(SetLogVerbosityLevel(newVerbosityLevel = 1))
    }

    // TDLib answers on its own threads: run on real time, not runTest's virtual clock.
    private fun test(body: suspend CoroutineScope.() -> Unit) = runTest {
        withContext(Dispatchers.Default) { withTimeout(60.seconds) { body() } }
    }

    private fun parameters() = SetTdlibParameters(
        useTestDc = true,
        databaseDirectory = "${testDatabaseDirectory()}/tdlib-kmp-test-${Random.nextLong().toULong()}",
        apiId = 1,
        apiHash = "0".repeat(32),
        systemLanguageCode = "en",
        deviceModel = "tdlib-kmp test",
        applicationVersion = "1.0",
    )

    @Test
    fun execute() {
        val entities = TdClient.execute(GetTextEntities(text = "@telegram https://telegram.org"))
        assertIs<TextEntities>(entities)
        assertEquals(2, entities.entities.size)
    }

    @Test
    fun typedRequestsAndErrors() = test {
        val client = TdClient.create()
        try {
            assertEquals(OptionValueString(value = "1.8.68"), client.send(GetOption(name = "version")))
            // Before initialization TDLib leaves async getOption for unknown names unanswered; testReturnError always answers.
            val error = assertFailsWith<TdException> { client.send(TestReturnError(error = Error(code = 400, message = "boom"))) }
            assertEquals(400, error.code)
            assertEquals("boom", error.error.message)
        } finally {
            client.close()
        }
    }

    @Test
    fun concurrentRequestsGetTheirOwnAnswers() = test {
        val client = TdClient.create()
        try {
            val texts = (1..50).map { "#tag$it" }
            val answers = texts.map { async { client.send(GetTextEntities(text = it)) } }.awaitAll()
            assertEquals(texts.map { 1 }, answers.map { it.entities.size })
        } finally {
            client.close()
        }
    }

    @Test
    fun authorizationFlowAndClose() = test {
        val client = TdClient.create()
        client.send(GetOption(name = "version"))
        client.authorizationState.first { it is AuthorizationStateWaitTdlibParameters }
        client.send(parameters())
        client.authorizationState.first { it is AuthorizationStateWaitPhoneNumber }
        client.close()
        assertIs<AuthorizationStateClosed>(client.authorizationState.value)
    }

    @Test
    fun clientsDoNotShareUpdates() = test {
        val first = TdClient.create()
        val second = TdClient.create()
        first.send(GetOption(name = "version"))
        first.authorizationState.first { it is AuthorizationStateWaitTdlibParameters }
        // The second instance was never started, so it must not have seen the first one's updates.
        assertEquals(null, second.authorizationState.value)
        second.send(GetOption(name = "version"))
        second.authorizationState.first { it is AuthorizationStateWaitTdlibParameters }
        first.close()
        second.close()
    }
}
