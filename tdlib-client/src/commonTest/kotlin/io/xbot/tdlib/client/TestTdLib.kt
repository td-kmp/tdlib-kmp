package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib

/** The [TdLib] the tests run against: NativeTdLib, or on the web TDLib's worker from the td-pack archive. */
expect fun testTdLib(): TdLib

/** A directory TDLib may create its database in. */
expect fun testDatabaseDirectory(): String
