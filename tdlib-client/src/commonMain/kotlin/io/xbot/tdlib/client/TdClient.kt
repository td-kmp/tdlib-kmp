package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib
import io.xbot.tdlib.api.AuthorizationState
import io.xbot.tdlib.api.AuthorizationStateClosed
import io.xbot.tdlib.api.Close
import io.xbot.tdlib.api.Error
import io.xbot.tdlib.api.TdFunction
import io.xbot.tdlib.api.TdObject
import io.xbot.tdlib.api.Update
import io.xbot.tdlib.api.UpdateAuthorizationState
import io.xbot.tdlib.api.parseTdObject
import io.xbot.tdlib.api.toTdJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.reflect.KClass

/**
 * One TDLib instance of a [TdLib]. TDLib starts it on the first [send]: subscribe to [updates] before
 * that, or read the latest state from [authorizationState], which is never missed.
 *
 * If the [TdLib] becomes unavailable (web: the worker failed), pending requests fail with
 * [io.xbot.tdlib.TdLibUnavailableException] and [authorizationState] becomes [AuthorizationStateClosed].
 */
public class TdClient private constructor(public val id: Int, private val receiver: TdReceiver) {
    private val flows = UpdateFlows()

    public val updates: SharedFlow<Update> get() = flows.all

    public val authorizationState: StateFlow<AuthorizationState?>
        field = MutableStateFlow<AuthorizationState?>(null)

    /** @throws TdException when TDLib answers with an error. */
    public suspend fun <R : TdObject> send(function: TdFunction<R>): R = result(receiver.request(this, function))

    /** Runs one of the functions TDLib can execute synchronously (getTextEntities, getOption, ...). */
    public suspend fun <R : TdObject> execute(function: TdFunction<R>): R =
        result(parseTdObject(checkNotNull(receiver.tdLib.execute(function.toTdJson())) { "TDLib returned no result" }))

    /** The updates of one class. Its collectors aren't resumed for the updates of other classes. */
    public inline fun <reified T : Update> updatesOf(): Flow<T> = updatesOf(T::class)

    @PublishedApi
    internal fun <T : Update> updatesOf(type: KClass<T>): Flow<T> = flows[type]

    /** Closes the instance and waits until TDLib has released it. */
    public suspend fun close() {
        if (authorizationState.value !is AuthorizationStateClosed) {
            runCatching { send(Close) }
            authorizationState.first { it is AuthorizationStateClosed }
        }
    }

    internal fun onUpdate(update: Update) {
        if (update is UpdateAuthorizationState) authorizationState.value = update.authorizationState
        flows.emit(update)
    }

    public companion object {
        /** A new instance of [tdLib]: [io.xbot.tdlib.NativeTdLib], or a [io.xbot.tdlib.WorkerTdLib] on the web. */
        public suspend fun create(tdLib: TdLib): TdClient {
            val receiver = TdReceiver.of(tdLib)
            return TdClient(tdLib.createClientId(), receiver).also { receiver.register(it) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <R : TdObject> result(value: TdObject): R = if (value is Error) throw TdException(value) else value as R
    }
}

public class TdException(public val error: Error) : Exception("TDLib error ${error.code}: ${error.message}") {
    public val code: Int get() = error.code
}
