package io.xbot.tdlib.client

import io.xbot.tdlib.NativeTdLib

/** A new instance of the process's TDLib, [NativeTdLib]. */
public suspend fun TdClient.Companion.create(): TdClient = create(NativeTdLib)
