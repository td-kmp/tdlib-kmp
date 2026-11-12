package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib
import io.xbot.tdlib.TdLogMessageHandler
import io.xbot.tdlib.api.Update
import io.xbot.tdlib.api.UpdateFile
import io.xbot.tdlib.api.UpdateOption
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Hands out the events it is given, as TDLib frames updates of client 1.
private class UpdatesTdLib : TdLib {
    private val events = Channel<String>(Channel.UNLIMITED)

    fun emit(event: String) {
        events.trySend(event.dropLast(1) + ",\"@client_id\":1}")
    }

    fun emitWithClientIdFirst(event: String) {
        events.trySend("{\"@client_id\":1," + event.drop(1))
    }

    override fun createClientId(): Int = 1
    override fun send(clientId: Int, request: String) = Unit
    override suspend fun receive(timeout: Duration): String? = withTimeoutOrNull(timeout) { events.receive() }
    override suspend fun execute(request: String): String? = null
    override fun setLogMessageHandler(maxVerbosityLevel: Int, handler: TdLogMessageHandler?) = Unit
}

private const val OPTION = """{"@type":"updateOption","name":"version","value":{"@type":"optionValueString","value":"1.8.69"}}"""
private const val FILE = """{"@type":"updateFile","file":{"@type":"file","id":7,"local":{"@type":"localFile"},"remote":{"@type":"remoteFile"}}}"""

class TdClientUpdatesTest {
    @Test
    fun updatesOfACollectsOnlyItsClass() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) {
                val tdLib = UpdatesTdLib()
                val client = TdClient.create(tdLib)
                val files = async(start = CoroutineStart.UNDISPATCHED) { client.updatesOf<UpdateFile>().take(1).toList() }
                val options = async(start = CoroutineStart.UNDISPATCHED) { client.updatesOf<UpdateOption>().take(2).toList() }
                val all = async(start = CoroutineStart.UNDISPATCHED) { client.updatesOf<Update>().take(3).toList() }
                listOf(OPTION, FILE, OPTION).forEach(tdLib::emit)
                assertEquals(7, files.await().single().file.id)
                assertEquals(2, options.await().size)
                assertTrue(all.await().map { it::class } == listOf(UpdateOption::class, UpdateFile::class, UpdateOption::class))
            }
        }
    }

    @Test
    fun anUpdateWithItsClientIdFirstStillArrives() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) {
                val tdLib = UpdatesTdLib()
                val client = TdClient.create(tdLib)
                val files = async(start = CoroutineStart.UNDISPATCHED) { client.updatesOf<UpdateFile>().take(1).toList() }
                tdLib.emitWithClientIdFirst(FILE)
                assertEquals(7, files.await().single().file.id)
            }
        }
    }
}
