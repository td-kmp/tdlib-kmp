package io.xbot.tdlib.client

import androidx.test.platform.app.InstrumentationRegistry

actual fun testDatabaseDirectory(): String =
    InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.absolutePath
