# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# signingConfig { ... } fields in the build.gradle.kts file.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Data entities: Room uses reflection to instantiate these and read field names.
-keep class com.catalanflashcard.data.entity.** { *; }

# Keep public API of app classes for debugging convenience.
# Room, Compose, and Lifecycle bundle their own consumer ProGuard rules.
-keepclassmembers class com.catalanflashcard.** {
    public <methods>;
    public <fields>;
}

# If you keep line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
