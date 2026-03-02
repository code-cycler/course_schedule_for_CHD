# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line number information for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*

# ============================================================
# SnakeYAML (java.beans not fully supported on Android)
# ============================================================
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn org.yaml.snakeyaml.**

# ============================================================
# GeckoView (Firefox WebView)
# ============================================================
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keep interface org.mozilla.geckoview.** { *; }

# ============================================================
# Retrofit
# ============================================================
# Retrofit does not support Java 8's Optional by default
-dontwarn retrofit2.Platform$Java8

# Keep Retrofit interfaces and their methods
-keep interface ** extends retrofit2.Call { *; }
-keepclassmembers,allowshrinking,allowobfuscation class * {
    @retrofit2.http.* <methods>;
}

# Keep response classes
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ============================================================
# OkHttp
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ============================================================
# Jsoup (HTML Parser)
# ============================================================
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }

# ============================================================
# Room Database
# ============================================================
# Keep Room entities
-keep class com.example.course_schedule_for_chd_v002.data.local.database.entity.** { *; }

# Keep Room DAOs
-keep class com.example.course_schedule_for_chd_v002.data.local.database.CourseDao { *; }

# Room runtime
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ============================================================
# Koin Dependency Injection
# ============================================================
-keep class org.koin.** { *; }
-keep interface org.koin.** { *; }

# Keep Koin modules
-keep class com.example.course_schedule_for_chd_v002.di.** { *; }

# ============================================================
# Kotlin & Coroutines
# ============================================================
# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================================
# DataStore
# ============================================================
-keep class androidx.datastore.** { *; }

# ============================================================
# Compose
# ============================================================
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ============================================================
# Domain & Data Models (keep for JSON/Database serialization)
# ============================================================
-keep class com.example.course_schedule_for_chd_v002.domain.model.** { *; }
-keep class com.example.course_schedule_for_chd_v002.data.remote.dto.** { *; }

# Keep all data classes
-keep class **$$serializer { *; }
-keepclassmembers class **$$serializer {
    <fields>;
}

# ============================================================
# General Android
# ============================================================
# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(***);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Serializable implementations
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
