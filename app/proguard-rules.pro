# Linphone SDK Proguard Rules
-keep class org.linphone.** { *; }
-keep interface org.linphone.** { *; }
-dontwarn org.linphone.**

# Android Architecture & Coroutines
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
