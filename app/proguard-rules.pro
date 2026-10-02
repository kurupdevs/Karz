# Karz ProGuard rules. minifyEnabled is off for now; R8 full mode
# lands in the polish phase (SPEC section 10, phase 4).
# Rules below are ready for that release build.

# Keep the screens data contracts; repositories map Firestore documents to
# these models manually, but keeping them avoids any reflective surprises.
-keep class com.kurupdevs.karz.ui.screens.data.** { *; }

# Data-layer repositories and models referenced across modules.
-keep class com.kurupdevs.karz.data.** { *; }
-keep class com.kurupdevs.karz.math.** { *; }

# Firebase / Play services: keep annotations and generic signatures used by
# the SDKs' internal (de)serialization.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firestore uses protobuf-lite internally.
-dontwarn com.google.protobuf.**

# kotlinx.serialization: keep generated serializers.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembernames class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Coroutines / OkHttp (Coil networking).
-dontwarn kotlinx.coroutines.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil image loader.
-keep class coil3.** { *; }
-dontwarn coil3.**

# AndroidX startup providers referenced from the manifest.
-keep class androidx.startup.** { *; }
