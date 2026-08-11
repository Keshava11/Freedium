# R8 configuration for release builds.
#
# The release variant runs with minification, resource shrinking and obfuscation on.
# Anything reached only by reflection or JNI has to be kept explicitly - the rules below
# cover the three cases in this app: Room's generated code, SQLCipher's native bridge, and
# Kotlin/Compose metadata.
#
# To produce an installable release build you need a signing key. Create one yourself and
# point keystore.properties at it (that file is git-ignored and is never read by anyone
# but Gradle):
#
#   keytool -genkeypair -v -keystore freedium-release.jks -keyalg RSA -keysize 4096 \
#     -validity 10000 -alias freedium
#
#   # keystore.properties, in the project root
#   storeFile=freedium-release.jks
#   storePassword=...
#   keyAlias=freedium
#   keyPassword=...
#
# Without it `assembleRelease` still builds, but emits an unsigned APK.

# ---------------------------------------------------------------- stack traces
# Keep line numbers so a release crash is diagnosable, but hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------- Room
# Room generates an implementation of the @Database class by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------ SQLCipher
# Loaded over JNI, so R8 cannot see the references from Java.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**

# -------------------------------------------------------------------- Kotlin
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# Coroutines' internals are referenced reflectively by the debug agent.
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ------------------------------------------------------------------- Compose
-dontwarn androidx.compose.**

# --------------------------------------------------------------- WorkManager
# Workers are constructed reflectively from their class name, so R8 must not rename or
# remove them or their (Context, WorkerParameters) constructor.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --------------------------------------------------------------- our own code
# The listener service is instantiated by the system from the manifest name.
-keep class com.ravi.freedium.utils.notification.FreediumNotificationListener { *; }

# Strip the remaining verbose logging as a belt-and-braces measure. FreediumLog already
# compiles out under BuildConfig.DEBUG; this also removes any direct android.util.Log call
# that creeps back in, so notification content cannot reach logcat in a release build.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
}
