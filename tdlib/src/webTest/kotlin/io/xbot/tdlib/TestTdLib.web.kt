package io.xbot.tdlib

import io.xbot.tdlib.worker.createTestWorker

// One worker for the whole test bundle: like td_receive, a TdLib has one receiver, and its clients.
private val shared: TdLib by lazy { WorkerTdLib(createTestWorker()) }

actual fun testTdLib(): TdLib = shared
