# libtdjsonjava binds its natives by name from JNI_OnLoad (RegisterNatives on
# io/xbot/tdlib/NativeBridge) and calls LogMessageHandler.onLogMessage(int, String)
# by name, so R8 must keep these exact names.
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
