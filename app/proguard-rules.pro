# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- Crash report readability -------------------------------------------------
# Keep line numbers so Play Console stack traces stay deobfuscatable via the
# uploaded mapping.txt, but still hide the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generic signatures + annotations are needed by the Firebase/Firestore SDK
# (generic Task<T>, collectionGroup queries) and by kotlinx.coroutines.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# --- Kotlin / coroutines ------------------------------------------------------
# Coroutines ships consumer rules; keep the volatile fields R8 can miss.
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- App code -----------------------------------------------------------------
# Firestore payloads are written/read as Map<String, Any?> (no POJO auto-
# mapping) and every enum wire value is mapped explicitly, so the model classes
# need no keep rules. Enum valueOf() is still kept by R8 by default.
#
# osmdroid, Stripe, ML Kit, Firebase, credentials/googleid, Markwon and ZXing
# all ship their own consumer ProGuard rules inside their AARs, which R8 applies
# automatically. No additional keeps are required for them.
