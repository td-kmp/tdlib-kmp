# libtdjsonjava binds these by name (RegisterNatives / GetMethodID).
-keepclasseswithmembernames,includedescriptorclasses class io.xbot.tdlib.NativeBridge {
    native <methods>;
}
-keepnames class io.xbot.tdlib.NativeBridge
-keep interface io.xbot.tdlib.NativeBridge$LogMessageHandler {
    void onLogMessage(int, java.lang.String);
}
-keepclassmembers class * implements io.xbot.tdlib.NativeBridge$LogMessageHandler {
    void onLogMessage(int, java.lang.String);
}
