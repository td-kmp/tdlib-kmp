package io.xbot.tdlib.client

import io.xbot.tdlib.api.AuthorizationStateClosed
import io.xbot.tdlib.api.GetOption
import io.xbot.tdlib.api.Ok
import io.xbot.tdlib.api.OptionValueString
import io.xbot.tdlib.api.SetOption
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TdReceiverTest {
    @Test
    fun concurrentRequestsReachTdLibInTheirOrder() = fakeTdLibTest(
        answer = { request, clientId ->
            """{"@type":"optionValueString","value":"${request.name}","@extra":${request.extra},"@client_id":$clientId}"""
        },
    ) { tdLib ->
        val client = TdClient.create(tdLib)
        val names = List(REQUESTS) { "option$it" }
        val answers = names.map { async { client.send(GetOption(name = it)) } }.awaitAll()
        assertEquals(names, answers.map { (it as OptionValueString).value })
        val extras = List(REQUESTS) { tdLib.sent.receive().extra }
        assertEquals(extras.sorted(), extras)
    }

    @Test
    fun anAnswerLaidOutAnotherWayIsRouted() = fakeTdLibTest(
        answer = { request, clientId ->
            """{ "@extra": ${request.extra}, "@client_id": $clientId, "@type": "ok" }"""
        },
    ) { tdLib ->
        val client = TdClient.create(tdLib)
        assertEquals(Ok, client.send(SetOption(name = "name")))
    }

    @Test
    fun anAnswerThatCannotBeDecodedFailsOnlyItsRequest() = fakeTdLibTest(
        answer = { request, clientId ->
            val type = if (request.name == "unknown") "noSuchType" else "ok"
            """{"@type":"$type","@extra":${request.extra},"@client_id":$clientId}"""
        },
    ) { tdLib ->
        val client = TdClient.create(tdLib)
        assertFailsWith<SerializationException> { client.send(SetOption(name = "unknown")) }
        assertEquals(Ok, client.send(SetOption(name = "name")))
    }

    @Test
    fun aRequestToAClosedClientGetsItsAnswer() = fakeTdLibTest(
        // What TDLib answers a closed client with.
        answer = { request, clientId ->
            """{"@type":"error","code":500,"message":"Request aborted","@extra":${request.extra},"@client_id":$clientId}"""
        },
    ) { tdLib ->
        val client = TdClient.create(tdLib)
        tdLib.emitUpdate(client, CLOSED)
        client.authorizationState.first { it is AuthorizationStateClosed }
        // No client is left, so the loop stops on a receive that brings nothing.
        tdLib.timeOut()
        assertEquals(500, assertFailsWith<TdException> { client.send(GetOption(name = "version")) }.code)
    }

    private companion object {
        const val REQUESTS = 200
        const val CLOSED =
            """{"@type":"updateAuthorizationState","authorization_state":{"@type":"authorizationStateClosed"}}"""
    }
}
