package io.xbot.tdlib

/** Android: libtdjsonjava.so ships in the AAR's jniLibs, the system linker finds it. */
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
