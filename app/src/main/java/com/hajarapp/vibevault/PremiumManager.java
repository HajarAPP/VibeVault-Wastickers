/*
 * VibeVault — WhatsApp Stickers App
 * Copyright (c) 2026 VibeVault. All rights reserved.
 *
 * PremiumManager — Manages the freemium unlock state for sticker packs.
 *
 * Architecture:
 *   - Uses SharedPreferences for persistent, lightweight unlock tracking.
 *   - Free packs are defined in a static set (always accessible).
 *   - Premium packs start locked and are unlocked via rewarded ad or IAP.
 *   - Supports a listener pattern so UI can react to unlock events.
 *
 * Package: com.hajarapp.vibevault
 */

package com.hajarapp.vibevault;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PremiumManager {

    private static final String TAG = "PremiumManager";
    private static final String PREFS_NAME = "vibevault_premium";
    private static final String KEY_PREFIX = "unlocked_pack_";
    private static final String KEY_ALL_UNLOCKED = "all_packs_unlocked";

    /**
     * Identifiers of sticker packs that are FREE by default.
     * These packs are always accessible without watching an ad or purchasing.
     *
     * All other pack identifiers (from contents.json) are considered PREMIUM.
     */
    private static final Set<String> FREE_PACKS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "brainrot_legends",
                    "cyber_y2k"
            ))
    );

    private final SharedPreferences prefs;

    @Nullable
    private OnPackUnlockedListener unlockedListener;

    /**
     * Listener interface for UI updates when a pack is unlocked.
     */
    public interface OnPackUnlockedListener {
        /**
         * Called after a premium pack has been successfully unlocked.
         * @param identifier The pack identifier that was unlocked
         */
        void onPackUnlocked(@NonNull String identifier);
    }

    // ════════════════════════════════════════════════════════════
    // Constructor
    // ════════════════════════════════════════════════════════════

    public PremiumManager(@NonNull Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ════════════════════════════════════════════════════════════
    // Listener
    // ════════════════════════════════════════════════════════════

    /**
     * Set a listener to be notified when a pack is unlocked.
     * Useful for refreshing the RecyclerView adapter.
     */
    public void setOnPackUnlockedListener(@Nullable OnPackUnlockedListener listener) {
        this.unlockedListener = listener;
    }

    // ════════════════════════════════════════════════════════════
    // Pack Status Queries
    // ════════════════════════════════════════════════════════════

    /**
     * Returns true if this pack is included in the FREE_PACKS set.
     * Free packs are always accessible — no ad or purchase needed.
     *
     * @param identifier The pack identifier from contents.json
     */
    public boolean isFreePack(@NonNull String identifier) {
        return FREE_PACKS.contains(identifier);
    }

    /**
     * Returns true if this pack is a premium pack (not in FREE_PACKS).
     * Premium packs require a rewarded ad watch or IAP to unlock.
     *
     * @param identifier The pack identifier from contents.json
     */
    public boolean isPremiumPack(@NonNull String identifier) {
        return !FREE_PACKS.contains(identifier);
    }

    /**
     * Returns true if the user can access this pack's stickers.
     * A pack is unlocked if it's either:
     *   - A free pack (always unlocked), OR
     *   - A premium pack that has been explicitly unlocked, OR
     *   - The global "all packs unlocked" flag is set
     *
     * @param identifier The pack identifier from contents.json
     */
    public boolean isPackUnlocked(@NonNull String identifier) {
        // Free packs are always unlocked
        if (isFreePack(identifier)) {
            return true;
        }
        // Check global unlock (e.g., from a "Pro" IAP)
        if (prefs.getBoolean(KEY_ALL_UNLOCKED, false)) {
            return true;
        }
        // Check individual pack unlock
        return prefs.getBoolean(KEY_PREFIX + identifier, false);
    }

    // ════════════════════════════════════════════════════════════
    // Unlock Operations
    // ════════════════════════════════════════════════════════════

    /**
     * Marks a single premium pack as unlocked.
     * Call this after the user successfully watches a rewarded ad
     * or completes an in-app purchase for this specific pack.
     *
     * @param identifier The pack identifier to unlock
     */
    public void unlockPack(@NonNull String identifier) {
        prefs.edit()
                .putBoolean(KEY_PREFIX + identifier, true)
                .apply();

        // Notify listener
        if (unlockedListener != null) {
            unlockedListener.onPackUnlocked(identifier);
        }
    }

    /**
     * Unlocks ALL premium packs at once.
     * Call this after the user purchases a "Pro" or "Unlock All" IAP.
     * Uses a global flag so future packs are also auto-unlocked.
     */
    public void unlockAllPacks() {
        prefs.edit()
                .putBoolean(KEY_ALL_UNLOCKED, true)
                .apply();
    }

    /**
     * Locks a pack again. Primarily for testing and debugging.
     * In production, packs should generally not be re-locked after purchase.
     *
     * @param identifier The pack identifier to lock
     */
    public void lockPack(@NonNull String identifier) {
        prefs.edit()
                .putBoolean(KEY_PREFIX + identifier, false)
                .apply();
    }

    /**
     * Resets all unlock state. For testing/debugging only.
     * This clears both individual unlocks and the global unlock flag.
     */
    public void resetAllUnlocks() {
        prefs.edit().clear().apply();
    }

    // ════════════════════════════════════════════════════════════
    // Bulk Queries
    // ════════════════════════════════════════════════════════════

    /**
     * Returns true if ALL packs in the list have been unlocked.
     * Useful for hiding the "unlock" UI when everything is already available.
     *
     * @param allPacks The complete list of sticker packs from the loader
     */
    public boolean areAllPacksUnlocked(@NonNull List<StickerPack> allPacks) {
        // Global unlock means everything is unlocked
        if (prefs.getBoolean(KEY_ALL_UNLOCKED, false)) {
            return true;
        }
        for (StickerPack pack : allPacks) {
            if (!isPackUnlocked(pack.identifier)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the number of premium packs that are currently locked.
     *
     * @param allPacks The complete list of sticker packs from the loader
     */
    public int getLockedPackCount(@NonNull List<StickerPack> allPacks) {
        int count = 0;
        for (StickerPack pack : allPacks) {
            if (isPremiumPack(pack.identifier) && !isPackUnlocked(pack.identifier)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the set of pack identifiers that are free.
     * Useful for analytics or UI differentiation.
     */
    @NonNull
    public static Set<String> getFreePacks() {
        return FREE_PACKS;
    }
}
