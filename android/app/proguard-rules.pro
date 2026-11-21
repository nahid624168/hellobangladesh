# kotlinx.serialization (DTOs in :core:network)
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **.*$Companion { kotlinx.serialization.KSerializer serializer(...); }
-if @kotlinx.serialization.Serializable class ** { static **$* *; }
-keepclassmembers class <2>$<3> { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.exambase.core.network.**$$serializer { *; }
-keepclassmembers class com.exambase.core.network.** { *** Companion; }
-keepclasseswithmembers class com.exambase.core.network.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor + OkHttp
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room entities / DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# WorkManager workers are created by reflection
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
