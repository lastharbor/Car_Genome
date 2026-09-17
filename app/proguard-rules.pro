# kotlinx.serialization rules
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName *;
}
-keepclassmembers class com.cargenome.** {
    *** Companion;
}
-keepclasseswithmembers class com.cargenome.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit interfaces and response types
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Room generated implementations
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ML Kit & CameraX
-keep class com.google.mlkit.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ---------------------------------------------------------
# Security, Obfuscation & Anti-Reverse Engineering Rules
# ---------------------------------------------------------

# Flatten package hierarchy: repacks all internal classes into a flat package 'a'
# This scrambles the original package structure in decompiler file trees (JADX, Bytecode Viewer)
-repackageclasses com.cargenome.app.a
-allowaccessmodification

# Strip file name attributes in release builds
-renamesourcefileattribute SourceFile

# Strip verbose and debug logging in release builds to prevent leaking internal logic
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Keep security integrity checks from being stripped
-keep class com.cargenome.app.domain.security.SecurityIntegrityChecker { *; }
-keep class com.cargenome.app.domain.premium.** { *; }

