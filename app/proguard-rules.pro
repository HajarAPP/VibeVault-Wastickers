# VibeVault ProGuard Rules

# Keep Fresco classes (required for WebP sticker validation)
-keep class com.facebook.imagepipeline.** { *; }
-keep class com.facebook.animated.webp.** { *; }
-keep class com.facebook.drawee.** { *; }

# Keep Google Ads SDK
-keep class com.google.android.gms.ads.** { *; }

# Keep Google Billing
-keep class com.android.billingclient.** { *; }

# Keep model classes for Parcelable
-keep class com.vibevault.stickers.StickerPack { *; }
-keep class com.vibevault.stickers.Sticker { *; }

# General Android rules
-keepattributes Signature
-keepattributes *Annotation*

# R8 missing-class suppressions
# android.media.LoudnessCodecController is an API 35 class referenced by AdMob's
# internal ads SDK (com.google.android.gms.internal.ads.zzrz) but not present
# in compileSdk 34. This is safe to suppress — it's an optional audio feature.
-dontwarn android.media.LoudnessCodecController
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener
