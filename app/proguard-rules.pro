# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.toolbox.alltools.ToolModule { *; }
-keep class com.toolbox.alltools.modules.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
