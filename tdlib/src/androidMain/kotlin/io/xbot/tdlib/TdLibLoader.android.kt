package io.xbot.tdlib

internal actual object TdLibLoader {
    @Volatile
    private var loaded = false

    actual fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("tdjsonjava")
            loaded = true
        }
    }
}
