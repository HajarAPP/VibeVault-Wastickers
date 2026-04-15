/*
 * VibeVault — WhatsApp Stickers App
 * Copyright (c) 2026 VibeVault. All rights reserved.
 */

package com.vibevault.stickers;

import android.app.Application;

import com.facebook.drawee.backends.pipeline.Fresco;

/**
 * Application class that initializes Fresco for WebP image support.
 * Fresco is required by the WhatsApp Stickers SDK for WebP sticker validation.
 */
public class VibeVaultApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Fresco — required for WebP sticker decoding and validation
        Fresco.initialize(this);
    }
}
