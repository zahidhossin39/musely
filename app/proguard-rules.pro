# Defaults from the AndroidX libraries cover the rest of the app.

# --- NewPipe Extractor (downloads) ---
# Time-ago patterns are loaded by class name at runtime.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
# Protobuf-lite messages are read through their generated fields.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

# Rhino evaluates YouTube's player JavaScript and relies on reflection internally.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
# Desktop-only scripting APIs referenced by Rhino but never used on Android.
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
