/*
 * VibeVault — WhatsApp Stickers App
 * Copyright (c) 2026 VibeVault. All rights reserved.
 *
 * StickerPackDetailsActivity — Shows a full grid preview of all stickers
 * in a selected pack, with the option to add it to WhatsApp.
 *
 * Features:
 *   - Pack header: tray icon, name, publisher, sticker count + total size
 *   - 4-column sticker grid preview loaded from assets
 *   - "Add to WhatsApp" button with Interstitial Ad flow
 *   - Navigation back via toolbar
 *
 * Package: com.vibevault.stickers
 */

package com.vibevault.stickers;
import com.hajmidapp.vibevault.R;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;
import java.io.InputStream;

public class StickerPackDetailsActivity extends AddStickerPackActivity {

    private static final String TAG = "PackDetailsActivity";

    public static final String EXTRA_STICKER_PACK_ID = "sticker_pack_id";
    public static final String EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority";
    public static final String EXTRA_STICKER_PACK_NAME = "sticker_pack_name";

    private StickerPack stickerPack;
    private AdManager adManager;

    // ════════════════════════════════════════════════════════════
    // Activity Lifecycle
    // ════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_details);

        // ── Initialize AdManager ──
        adManager = new AdManager();
        adManager.loadInterstitialAd(this);

        // ── Get sticker pack from intent ──
        stickerPack = getIntent().getParcelableExtra("sticker_pack");
        if (stickerPack == null) {
            Log.e(TAG, "No sticker pack passed via intent — finishing activity.");
            finish();
            return;
        }

        // ── Setup Toolbar ──
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(stickerPack.name);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        // ── Populate Header ──
        ImageView trayIcon = findViewById(R.id.detail_tray_icon);
        TextView packName = findViewById(R.id.detail_pack_name);
        TextView publisher = findViewById(R.id.detail_publisher);
        TextView stickerCount = findViewById(R.id.detail_sticker_count);

        packName.setText(stickerPack.name);
        publisher.setText(getString(R.string.publisher_label, stickerPack.publisher));
        stickerCount.setText(String.format("%d stickers • %s",
                stickerPack.getStickers().size(),
                formatFileSize(stickerPack.getTotalSize())));

        // Load tray icon from assets
        loadTrayIcon(trayIcon, stickerPack.identifier, stickerPack.trayImageFile);

        // ── Setup Sticker Grid Preview ──
        RecyclerView stickerGrid = findViewById(R.id.sticker_grid);
        stickerGrid.setLayoutManager(new GridLayoutManager(this, 4));
        stickerGrid.setHasFixedSize(true);
        StickerPreviewAdapter previewAdapter = new StickerPreviewAdapter(
                this, stickerPack.identifier, stickerPack.getStickers());
        stickerGrid.setAdapter(previewAdapter);

        // ── Add to WhatsApp Button ──
        Button btnAddWhatsApp = findViewById(R.id.btn_detail_add_whatsapp);

        // Update button text if already whitelisted
        if (stickerPack.getIsWhitelisted()) {
            btnAddWhatsApp.setText(R.string.added_to_whatsapp);
            btnAddWhatsApp.setEnabled(false);
            btnAddWhatsApp.setAlpha(0.5f);
        }

        btnAddWhatsApp.setOnClickListener(v -> {
            // Show interstitial ad, then send the WhatsApp intent
            adManager.showInterstitialAd(this, () -> {
                addStickerPackToWhatsApp(stickerPack.identifier, stickerPack.name);
                // Pre-load next interstitial
                adManager.loadInterstitialAd(this);
            });
        });
    }

    @Override
    protected void onDestroy() {
        if (adManager != null) {
            adManager.destroy();
        }
        super.onDestroy();
    }

    // ════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════

    /**
     * Loads the tray icon from the assets directory.
     * Falls back to a system placeholder if the file can't be read.
     */
    private void loadTrayIcon(ImageView imageView, String identifier, String trayImageFile) {
        try {
            InputStream inputStream = getAssets().open(identifier + "/" + trayImageFile);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            imageView.setImageBitmap(bitmap);
            inputStream.close();
        } catch (IOException e) {
            Log.w(TAG, "Could not load tray icon: " + identifier + "/" + trayImageFile, e);
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    /**
     * Formats a byte count into a human-readable size string.
     */
    private String formatFileSize(long sizeInBytes) {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeInBytes / 1024.0);
        } else {
            return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0));
        }
    }
}
