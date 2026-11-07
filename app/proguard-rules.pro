# Keep Xposed entry points and their members.
-keep class com.spotlight.app.xposed.** { *; }
-keep class de.robv.android.xposed.** { *; }

# Keep data classes used by Compose/runtime reflection.
-keepclassmembers class ** {
    synthetic <init>(...);
}
