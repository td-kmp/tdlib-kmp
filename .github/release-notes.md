TDLib **@TDLIB@** for Kotlin Multiplatform: Android, JVM, iOS, macOS, Linux, Windows and the web.

```toml
[versions]
tdlib = "@VERSION@"

[libraries]
tdlib = { module = "io.github.xephosbot:tdlib-kmp", version.ref = "tdlib" }
tdlib-client = { module = "io.github.xephosbot:tdlib-kmp-client", version.ref = "tdlib" }
```

On the web your worker needs the matching TDLib package:

```
https://github.com/td-kmp/td-pack/releases/download/v@TDLIB@/tdlib-web.tgz
```

[Getting started](https://github.com/td-kmp/tdlib-kmp#readme) · [TDLib @TDLIB@ natives](https://github.com/td-kmp/td-pack/releases/tag/v@TDLIB@) · [Maven Central](https://central.sonatype.com/artifact/io.github.xephosbot/tdlib-kmp/@VERSION@)
