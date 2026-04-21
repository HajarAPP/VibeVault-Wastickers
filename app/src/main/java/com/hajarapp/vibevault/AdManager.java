/*
 * VibeVault — WhatsApp Stickers App
 * Copyright (c) 2026 VibeVault. All rights reserved.
 *
 * Centralized AdMob management for all three ad formats:
 *   - Banner Ads (main screen footer)
 *   - Interstitial Ads (shown on "Add to WhatsApp" action)
 *   - Rewarded Video Ads (shown to unlock premium packs)
 *
 * Package: com.hajarapp.vibevault
 */

package com.hajarapp.vibevault;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Manages the lifecycle of all AdMob ad units.
 *
 * Usage:
 *   1. Create an instance in Activity.onCreate()
 *   2. Call loadBannerAd(), loadInterstitialAd(), loadRewardedAd() to pre-load
 *   3. Call show methods when needed
 *   4. Call destroy() in Activity.onDestroy() to release resources
 *
 * All ad unit IDs below are PRODUCTION IDs for the VibeVault AdMob account.
 *
 * Rewarded Ad Configuration:
 *   Name:  "Unlock Sticker"
 *   Value: 1
 *   Logic: When the user finishes watching the rewarded video, the "Unlock Sticker"
 *          reward is triggered to grant access to the locked sticker pack.
 */
public class AdManager {
    private static final String TAG = "AdManager";

    // ═══════════════════════════════════════════════════════════════════
    //  PRODUCTION AD UNIT IDs — VibeVault (ca-app-pub-5531227541272550)
    //
    //  App ID:         ca-app-pub-5531227541272550~7567843911
    //  Banner:         ca-app-pub-5531227541272550/7734318697
    //  Interstitial:   ca-app-pub-5531227541272550/1002435562
    //  Rewarded:       ca-app-pub-5531227541272550/2481992017
    // ═══════════════════════════════════════════════════════════════════
    private static final String BANNER_AD_UNIT_ID =
            "ca-app-pub-5531227541272550/7734318697";
    private static final String INTERSTITIAL_AD_UNIT_ID =
            "ca-app-pub-5531227541272550/1002435562";
    private static final String REWARDED_AD_UNIT_ID =
            "ca-app-pub-5531227541272550/2481992017";

    // Maximum number of automatic retry attempts for failed ad loads
    private static final int MAX_RETRY_COUNT = 3;

    // Ad instances
    @Nullable private InterstitialAd mInterstitialAd;
    @Nullable private RewardedAd mRewardedAd;
    @Nullable private AdView mBannerAdView;

    // Retry counters
    private int interstitialRetryCount = 0;
    private int rewardedRetryCount = 0;

    // Track whether an ad is currently being shown (prevent double-show)
    private boolean isShowingInterstitial = false;
    private boolean isShowingRewarded = false;

    /**
     * Callback interface for when a reward is granted after watching a video ad.
     */
    public interface OnRewardGranted {
        void onReward();
    }

    // ─────────────────────────────────────────────────
    // Banner Ad
    // ─────────────────────────────────────────────────

    /**
     * Loads a banner ad into the provided AdView.
     * The AdView's ad unit ID should be set in the layout XML, but this method
     * also handles the case where it's set programmatically.
     *
     * @param adView The AdView widget from the layout
     */
    public void loadBannerAd(@NonNull AdView adView) {
        mBannerAdView = adView;

        adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                Log.d(TAG, "Banner ad loaded successfully.");
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                Log.e(TAG, "Banner ad failed to load: " + loadAdError.getMessage()
                        + " (code: " + loadAdError.getCode() + ")");
            }

            @Override
            public void onAdOpened() {
                Log.d(TAG, "Banner ad opened.");
            }

            @Override
            public void onAdClicked() {
                Log.d(TAG, "Banner ad clicked.");
            }

            @Override
            public void onAdClosed() {
                Log.d(TAG, "Banner ad closed.");
            }
        });

        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
        Log.d(TAG, "Banner ad load requested.");
    }

    // ─────────────────────────────────────────────────
    // Interstitial Ad
    // ─────────────────────────────────────────────────

    /**
     * Pre-loads an interstitial ad. Call this in onCreate() so the ad is ready
     * by the time the user clicks "Add to WhatsApp".
     *
     * @param context Application or Activity context
     */
    public void loadInterstitialAd(@NonNull Context context) {
        if (mInterstitialAd != null) {
            Log.d(TAG, "Interstitial already loaded, skipping.");
            return;
        }

        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        mInterstitialAd = interstitialAd;
                        interstitialRetryCount = 0;
                        Log.d(TAG, "Interstitial ad loaded successfully.");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mInterstitialAd = null;
                        Log.e(TAG, "Interstitial ad failed to load: "
                                + loadAdError.getMessage()
                                + " (code: " + loadAdError.getCode() + ")");

                        // Retry with exponential backoff (up to MAX_RETRY_COUNT)
                        if (interstitialRetryCount < MAX_RETRY_COUNT) {
                            interstitialRetryCount++;
                            Log.d(TAG, "Retrying interstitial load, attempt "
                                    + interstitialRetryCount);
                            loadInterstitialAd(context);
                        }
                    }
                });
    }

    /**
     * Shows the interstitial ad if loaded. Otherwise, falls through immediately.
     *
     * The WhatsApp sticker-adding action is performed in the onDismissed callback,
     * ensuring the user experience isn't blocked if the ad fails to load.
     *
     * @param activity    Host activity (required for ad presentation)
     * @param onDismissed Callback executed after the ad is closed, or immediately
     *                    if no ad is available
     */
    public void showInterstitialAd(@NonNull Activity activity, @NonNull Runnable onDismissed) {
        if (isShowingInterstitial) {
            Log.w(TAG, "Interstitial already showing, ignoring duplicate call.");
            return;
        }

        if (mInterstitialAd != null) {
            isShowingInterstitial = true;

            mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    mInterstitialAd = null;
                    isShowingInterstitial = false;
                    Log.d(TAG, "Interstitial ad dismissed by user.");
                    onDismissed.run();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    mInterstitialAd = null;
                    isShowingInterstitial = false;
                    Log.e(TAG, "Interstitial ad failed to show: " + adError.getMessage());
                    onDismissed.run();
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad shown to user.");
                }
            });

            mInterstitialAd.show(activity);
        } else {
            Log.d(TAG, "Interstitial ad not ready — proceeding without ad.");
            onDismissed.run();
        }
    }

    /**
     * @return true if an interstitial ad is loaded and ready to display.
     */
    public boolean isInterstitialReady() {
        return mInterstitialAd != null;
    }

    // ─────────────────────────────────────────────────
    // Rewarded Video Ad
    // ─────────────────────────────────────────────────

    /**
     * Pre-loads a rewarded video ad. Call this in onCreate() so the ad is ready
     * when the user taps the "Unlock Pack" button.
     *
     * @param context Application or Activity context
     */
    public void loadRewardedAd(@NonNull Context context) {
        if (mRewardedAd != null) {
            Log.d(TAG, "Rewarded ad already loaded, skipping.");
            return;
        }

        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(context, REWARDED_AD_UNIT_ID, adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        mRewardedAd = rewardedAd;
                        rewardedRetryCount = 0;
                        Log.d(TAG, "Rewarded ad loaded successfully.");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mRewardedAd = null;
                        Log.e(TAG, "Rewarded ad failed to load: "
                                + loadAdError.getMessage()
                                + " (code: " + loadAdError.getCode() + ")");

                        // Retry with exponential backoff (up to MAX_RETRY_COUNT)
                        if (rewardedRetryCount < MAX_RETRY_COUNT) {
                            rewardedRetryCount++;
                            Log.d(TAG, "Retrying rewarded ad load, attempt "
                                    + rewardedRetryCount);
                            loadRewardedAd(context);
                        }
                    }
                });
    }

    /**
     * Shows the rewarded video ad. The reward callback is ONLY fired when the
     * user finishes watching the entire video. If the user dismisses early,
     * no reward is granted.
     *
     * @param activity Host activity
     * @param callback Called when the user earns the reward (pack unlock)
     */
    public void showRewardedAd(@NonNull Activity activity,
                                @NonNull OnRewardGranted callback) {
        if (isShowingRewarded) {
            Log.w(TAG, "Rewarded ad already showing, ignoring duplicate call.");
            return;
        }

        if (mRewardedAd != null) {
            isShowingRewarded = true;

            mRewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    mRewardedAd = null;
                    isShowingRewarded = false;
                    Log.d(TAG, "Rewarded ad dismissed by user.");
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    mRewardedAd = null;
                    isShowingRewarded = false;
                    Log.e(TAG, "Rewarded ad failed to show: " + adError.getMessage());
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad shown to user.");
                }
            });

            mRewardedAd.show(activity, rewardItem -> {
                // "Unlock Sticker" reward granted — value: 1
                Log.d(TAG, "✅ 'Unlock Sticker' reward granted: " + rewardItem.getAmount()
                        + "x " + rewardItem.getType()
                        + " — unlocking sticker pack for user.");
                callback.onReward();
            });
        } else {
            Log.w(TAG, "Rewarded ad not ready. Cannot show.");
        }
    }

    /**
     * @return true if a rewarded ad is loaded and ready to display.
     */
    public boolean isRewardedReady() {
        return mRewardedAd != null;
    }

    // ─────────────────────────────────────────────────
    // Lifecycle management
    // ─────────────────────────────────────────────────

    /**
     * Call from Activity.onResume() to resume the banner ad.
     */
    public void resumeBannerAd() {
        if (mBannerAdView != null) {
            mBannerAdView.resume();
        }
    }

    /**
     * Call from Activity.onPause() to pause the banner ad.
     */
    public void pauseBannerAd() {
        if (mBannerAdView != null) {
            mBannerAdView.pause();
        }
    }

    /**
     * Call from Activity.onDestroy() to release all ad resources.
     * After calling this, the AdManager instance should not be reused.
     */
    public void destroy() {
        if (mBannerAdView != null) {
            mBannerAdView.destroy();
            mBannerAdView = null;
        }
        mInterstitialAd = null;
        mRewardedAd = null;
        isShowingInterstitial = false;
        isShowingRewarded = false;
        Log.d(TAG, "AdManager destroyed, all ad resources released.");
    }
}
