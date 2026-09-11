-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn androidx.media3.decoder.ffmpeg.**

-keep class io.coil.** { *; }

-keepclassmembers class uz.oktv.player.** {
    *;
}
