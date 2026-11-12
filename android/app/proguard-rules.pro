# Scam Shield ProGuard / R8 Rules
-keepclassmembers class com.scamshield.android.data.remote.dto.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn kotlinx.serialization.**

