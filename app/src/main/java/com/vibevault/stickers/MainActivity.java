/*
 * VibeVault — WhatsApp Stickers App
 * Copyright (c) 2026 VibeVault. All rights reserved.
 *
 * MainActivity — The main screen of the VibeVault app.
 *
 * Responsibilities:
 *   - Displays all sticker packs in a RecyclerView
 *   - Shows Banner Ad at the bottom of the screen
 *   - Handles "Add to WhatsApp" with Interstitial Ad flow
 *   - Handles "Unlock Premium Pack" with Rewarded Video Ad flow
 *   - Navigates to StickerPackDetailsActivity on pack tap
 *   - Manages ad lifecycle (resume/pause/destroy)
 *
 * Package: com.vibevault.stickers
 */

package com.vibevault.stickers;

import com.hajmidapp.vibevault.R;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main entry point of the app. Extends AddStickerPackActivity to inherit
 * the WhatsApp intent-handling logic for adding sticker packs.
 */
public class MainActivity extends AddStickerPackActivity
        implements StickerPackListAdapter.OnPackInteractionListener,
                   PremiumManager.OnPackUnlockedListener {

    private static final String TAG = "MainActivity";

    // ════════════════════════════════════════════════════════════
    // Views
    // ════════════════════════════════════════════════════════════
    private RecyclerView recyclerView;
    private ProgressBar loadingIndicator;
    private AdView bannerAdView;

    // ════════════════════════════════════════════════════════════
    // Managers
    // ════════════════════════════════════════════════════════════
    private AdManager adManager;
    private PremiumManager premiumManager;

    // ════════════════════════════════════════════════════════════
    // Data
    // ════════════════════════════════════════════════════════════
    private StickerPackListAdapter adapter;
    private ArrayList<StickerPack> stickerPacks;

    // ════════════════════════════════════════════════════════════
    // Threading — replaces deprecated AsyncTask
    // ════════════════════════════════════════════════════════════
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ════════════════════════════════════════════════════════════
    // Activity Lifecycle
    // ════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ── Initialize views ──
        recyclerView = findViewById(R.id.sticker_pack_list);
        loadingIndicator = findViewById(R.id.loading_indicator);
        bannerAdView = findViewById(R.id.banner_ad_view);

        // ── Initialize managers ──
        premiumManager = new PremiumManager(this);
        premiumManager.setOnPackUnlockedListener(this);

        adManager = new AdManager();

        // ── Initialize AdMob SDK ──
        // This must be called once before loading any ads.
        // The callback fires when all ad networks in the mediation chain are ready.
        MobileAds.initialize(this, initializationStatus -> {
            Log.d(TAG, "AdMob SDK initialized successfully.");
            // Now that SDK is ready, load the banner
            adManager.loadBannerAd(bannerAdView);
        });

        // ── Pre-load full-screen ads ──
        // Load these early so they're ready when the user clicks
        adManager.loadInterstitialAd(this);
        adManager.loadRewardedAd(this);

        // ── Setup RecyclerView ──
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        // ── Load sticker packs ──
        loadStickerPacks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adManager.resumeBannerAd();

        // Refresh whitelist status in case user added packs via WhatsApp
        if (stickerPacks != null && adapter != null) {
            refreshWhitelistStatus();
        }
    }

    @Override
    protected void onPause() {
        adManager.pauseBannerAd();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        adManager.destroy();
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
        super.onDestroy();
    }

    // ════════════════════════════════════════════════════════════
    // Sticker Pack Loading
    // ════════════════════════════════════════════════════════════

    /**
     * Loads sticker packs from the ContentProvider on a background thread.
     * Shows a loading indicator while working.
     */
    private void loadStickerPacks() {
        loadingIndicator.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);

        executor.execute(() -> {
            ArrayList<StickerPack> packs = null;
            try {
                packs = StickerPackLoader.fetchStickerPacks(MainActivity.this);
            } catch (Exception e) {
                Log.e(TAG, "Error loading sticker packs", e);
            }

            final ArrayList<StickerPack> finalPacks = packs;
            mainHandler.post(() -> onStickerPacksLoaded(finalPacks));
        });
    }

    /**
     * Called on the main thread when sticker packs finish loading.
     * Sets up the RecyclerView adapter with the loaded data.
     */
    private void onStickerPacksLoaded(ArrayList<StickerPack> packs) {
        loadingIndicator.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);

        if (packs == null || packs.isEmpty()) {
            Toast.makeText(this, R.string.error_loading_packs, Toast.LENGTH_LONG).show();
            return;
        }

        this.stickerPacks = packs;
        adapter = new StickerPackListAdapter(this, stickerPacks, premiumManager, this);
        recyclerView.setAdapter(adapter);

        Log.d(TAG, "Loaded " + packs.size() + " sticker packs.");
    }

    /**
     * Refreshes the whitelist status (is pack already added to WhatsApp?)
     * on a background thread. Updates UI when complete.
     */
    private void refreshWhitelistStatus() {
        executor.execute(() -> {
            if (stickerPacks != null) {
                for (StickerPack pack : stickerPacks) {
                    pack.setIsWhitelisted(
                            WhitelistCheck.isWhitelisted(MainActivity.this, pack.identifier));
                }
            }

            mainHandler.post(() -> {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        });
    }

    // ════════════════════════════════════════════════════════════
    // OnPackInteractionListener — RecyclerView Callbacks
    // ════════════════════════════════════════════════════════════

    /**
     * Called when the user taps "Add to WhatsApp" on a free/unlocked pack.
     * Shows an interstitial ad first, then sends the WhatsApp intent.
     */
    @Override
    public void onAddToWhatsAppClicked(StickerPack pack) {
        adManager.showInterstitialAd(this, () -> {
            // This runs after the ad is dismissed (or immediately if no ad)
            addStickerPackToWhatsApp(pack.identifier, pack.name);

            // Pre-load the next interstitial for future clicks
            adManager.loadInterstitialAd(this);
        });
    }

    /**
     * Called when the user taps "Unlock" on a locked premium pack.
     * Shows the premium unlock dialog with Ad and Purchase options.
     */
    @Override
    public void onUnlockClicked(StickerPack pack) {
        showPremiumUnlockDialog(pack);
    }

    /**
     * Called when the user taps on a pack card (not a button).
     * Navigates to the detail view if the pack is accessible.
     */
    @Override
    public void onPackClicked(StickerPack pack) {
        Intent intent = new Intent(this, StickerPackDetailsActivity.class);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_ID, pack.identifier);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_NAME, pack.name);
        intent.putExtra(StickerPackDetailsActivity.EXTRA_STICKER_PACK_AUTHORITY,
                BuildConfig.CONTENT_PROVIDER_AUTHORITY);
        intent.putExtra("sticker_pack", pack);
        startActivity(intent);
    }

    // ════════════════════════════════════════════════════════════
    // PremiumManager.OnPackUnlockedListener
    // ════════════════════════════════════════════════════════════

    /**
     * Called by PremiumManager when a pack is unlocked.
     * Refreshes the adapter to update the UI state.
     */
    @Override
    public void onPackUnlocked(@NonNull String identifier) {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    // ════════════════════════════════════════════════════════════
    // Premium Unlock Dialog
    // ════════════════════════════════════════════════════════════

    /**
     * Shows a dialog offering two unlock options:
     *   1. Watch a Rewarded Video Ad (free unlock)
     *   2. Purchase via In-App Purchase ($0.99)
     *
     * The dialog uses the app's dark theme for visual consistency.
     */
    private void showPremiumUnlockDialog(@NonNull StickerPack pack) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.premium_dialog_title);
        builder.setMessage(getString(R.string.premium_dialog_message));

        // ── Option 1: Watch Rewarded Video Ad (Free) ──
        builder.setPositiveButton(R.string.unlock_with_ad, (dialog, which) -> {
            if (adManager.isRewardedReady()) {
                adManager.showRewardedAd(this, () -> {
                    // Reward granted — unlock the pack
                    premiumManager.unlockPack(pack.identifier);
                    Toast.makeText(this, R.string.pack_unlocked_toast,
                            Toast.LENGTH_SHORT).show();

                    // Pre-load next rewarded ad for future unlocks
                    adManager.loadRewardedAd(this);
                });
            } else {
                // Ad not loaded yet — ask user to try again
                Toast.makeText(this, R.string.rewarded_ad_not_ready,
                        Toast.LENGTH_SHORT).show();
                adManager.loadRewardedAd(this);
            }
        });

        // ── Option 2: Purchase via Google Play Billing ($0.99) ──
        builder.setNeutralButton(R.string.unlock_with_purchase, (dialog, which) -> {
            // TODO: Implement Google Play Billing flow
            // When ready, use BillingClient to launch the purchase flow:
            //   1. Connect to BillingClient
            //   2. Query SkuDetails for the pack's product ID
            //   3. Launch BillingFlowParams
            //   4. Handle PurchasesUpdatedListener callback
            //   5. Call premiumManager.unlockPack(pack.identifier) on success
            Toast.makeText(this, "In-App Purchase coming soon!",
                    Toast.LENGTH_SHORT).show();
        });

        // ── Cancel ──
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

        // Build and style the dialog
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.card_sticker_pack);
        }
        dialog.show();

        // Style the dialog button text colors to match our neon theme
        try {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(getResources().getColor(R.color.neon_green, getTheme()));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setTextColor(getResources().getColor(R.color.premium_gold, getTheme()));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
        } catch (Exception e) {
            // Non-critical — button colors will fall back to theme defaults
            Log.w(TAG, "Could not style dialog buttons", e);
        }
    }
}
