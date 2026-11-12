package io.xbot.tdlib.client

import io.xbot.tdlib.api.Update
import io.xbot.tdlib.api.UpdateFile
import io.xbot.tdlib.api.UpdateOption
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals

class TdClientUpdatesTest {
    @Test
    fun updatesOfAClassCollectsOnlyThatClass() = fakeTdLibTest { tdLib ->
        val client = TdClient.create(tdLib)
        val files = async(start = CoroutineStart.UNDISPATCHED) { client.updatesOf<UpdateFile>().take(1).toList() }
        val options = async(start = CoroutineStart.UNDISPATCHED) { client.updatesOf<UpdateOption>().take(2).toList() }
        val all = async(start = CoroutineStart.UNDISPATCHED) { client.updatesOf<Update>().take(3).toList() }
        listOf(OPTION, FILE, OPTION).forEach { tdLib.emitUpdate(client, it) }
        assertEquals(7, files.await().single().file.id)
        assertEquals(2, options.await().size)
        assertEquals(listOf(UpdateOption::class, UpdateFile::class, UpdateOption::class), all.await().map { it::class })
    }

    @Test
    fun everyCollectorOfAClassGetsItsUpdates() = fakeTdLibTest { tdLib ->
        val client = TdClient.create(tdLib)
        val collectors = List(COLLECTORS) {
            async(start = CoroutineStart.UNDISPATCHED) { client.updatesOf<UpdateFile>().take(1).toList() }
        }
        tdLib.emitUpdate(client, FILE)
        assertEquals(List(COLLECTORS) { 7 }, collectors.map { it.await().single().file.id })
    }

    @Test
    fun anUpdateWithItsClientIdFirstStillArrives() = fakeTdLibTest { tdLib ->
        val client = TdClient.create(tdLib)
        val files = async(start = CoroutineStart.UNDISPATCHED) { client.updatesOf<UpdateFile>().take(1).toList() }
        tdLib.emit("""{"@client_id":${client.id},${FILE.drop(1)}""")
        assertEquals(7, files.await().single().file.id)
    }

    private companion object {
        const val COLLECTORS = 10
        const val OPTION = """{"@type":"updateOption","name":"version","value":{"@type":"optionValueString","value":"1.8.69"}}"""
        const val FILE = """{"@type":"updateFile","file":{"@type":"file","id":7,"local":{"@type":"localFile"},"remote":{"@type":"remoteFile"}}}"""
    }
}
