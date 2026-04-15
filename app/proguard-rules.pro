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
