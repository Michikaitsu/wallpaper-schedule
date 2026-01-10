# Wallpaper Scheduler ProGuard Rules

# Keep application class
-keep class com.wallpaperscheduler.** { *; }

# Keep data classes
-keepclassmembers class com.wallpaperscheduler.DaySchedule {
    <fields>;
    <init>(...);
}

# Material Design
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Kotlin
-keep class kotlin.** { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Keep generic signatures
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
