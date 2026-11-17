# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Firestore maps documents onto the model classes by reflection (DocumentSnapshot.toObject), so
# their names, no-argument constructors and fields have to survive shrinking. Code shrinking is
# currently off (isMinifyEnabled = false in app/build.gradle.kts); these rules are here so that
# switching it on does not silently break every read.
-keepattributes Signature,*Annotation*
-keep class com.example.model.** { *; }
