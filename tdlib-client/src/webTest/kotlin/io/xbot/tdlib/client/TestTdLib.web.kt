package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib
import io.xbot.tdlib.WorkerTdLib
import io.xbot.tdlib.worker.createTestWorker

private val shared: TdLib by lazy { WorkerTdLib(createTestWorker()) }

actual fun testTdLib(): TdLib = shared

// The test worker keeps /tdlib in IndexedDB.
actual fun testDatabaseDirectory(): String = "/tdlib"
