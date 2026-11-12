package io.xbot.tdlib.client

import io.xbot.tdlib.api.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.updateAndFetch
import kotlin.reflect.KClass

// A client's updates: a flow of them all and a flow a class, so a collector of one class isn't resumed for every
// other update.
@OptIn(ExperimentalAtomicApi::class)
internal class UpdateFlows {
    val all: SharedFlow<Update>
        field = unbounded()

    // td_api's updates are final classes of one sealed interface, so an update's class is its whole key.
    private val byClass = AtomicReference(emptyMap<KClass<out Update>, MutableSharedFlow<Update>>())

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Update> get(type: KClass<T>): Flow<T> = when (type) {
        Update::class -> all
        else -> byClass.updateAndFetch { if (type in it) it else it + (type to unbounded()) }.getValue(type)
    } as Flow<T>

    fun emit(update: Update) {
        all.tryEmit(update)
        byClass.load()[update::class]?.tryEmit(update)
    }
}

// Unbounded, so the shared receive loop never waits for a slow collector.
private fun unbounded() = MutableSharedFlow<Update>(extraBufferCapacity = Int.MAX_VALUE)
