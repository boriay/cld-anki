# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# signingConfig { ... } fields in the build.gradle.kts file.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data classes
-keep class com.catalanflashcard.data.entity.** { *; }

# Keep Room database
-keep class androidx.room.** { *; }
-keepclassmembers class androidx.room.** { *; }

# Keep Kotlin metadata
-keepclassmembers class com.catalanflashcard.** {
    public <methods>;
    public <fields>;
}

# Keep Compose runtime
-keep class androidx.compose.** { *; }

# Keep ViewModel
-keep class androidx.lifecycle.** { *; }

# If you keep line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
