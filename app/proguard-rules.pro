# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class xyz.blockthem.mars.manager.**$$serializer { *; }
-keepclassmembers class xyz.blockthem.mars.manager.** { *** Companion; }
-keepclasseswithmembers class xyz.blockthem.mars.manager.** { kotlinx.serialization.KSerializer serializer(...); }

# Credential Manager / Google ID
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
