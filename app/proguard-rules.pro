# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.aisoul.app.**$$serializer { *; }
-keepclassmembers class com.aisoul.app.** { *** Companion; }
-keepclasseswithmembers class com.aisoul.app.** { kotlinx.serialization.KSerializer serializer(...); }

# argon2kt bridges to native argon2 over JNI — signatures must survive R8
-keep class com.lambdapioneer.argon2kt.** { *; }

# the toolbox "native libs" are busybox/curl/jq executables, not JNI — but
# resource shrinking must never touch jniLibs (they're exec'd by path)
-keepclassmembers class * {
    native <methods>;
}

# okhttp/okio: silence platform-specific warnings (standard upstream advice)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Glance app widget (D-033) — receiver + generated session code
-keep class androidx.glance.appwidget.** { *; }
-keep class com.aisoul.app.widgets.launcher.** { *; }
