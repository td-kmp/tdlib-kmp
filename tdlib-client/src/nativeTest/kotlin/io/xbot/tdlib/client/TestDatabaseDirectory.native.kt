package io.xbot.tdlib.client

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual fun testDatabaseDirectory(): String =
    (getenv("TMPDIR") ?: getenv("TEMP"))?.toKString()?.trimEnd('/', '\\') ?: "/tmp"
