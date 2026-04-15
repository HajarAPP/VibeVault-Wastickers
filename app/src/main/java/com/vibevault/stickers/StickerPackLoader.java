/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * Modified for VibeVault by VibeVault Team.
 */

package com.vibevault.stickers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class StickerPackLoader {

    /**
     * Fetches a sticker asset as a byte array via the ContentProvider.
     */
    @NonNull
    static byte[] fetchStickerAsset(@NonNull final String identifier,
                                    @NonNull final String name,
                                    @NonNull final ContentResolver contentResolver) throws IOException {
        try (final InputStream inputStream = contentResolver.openInputStream(getStickerAssetUri(identifier, name));
             final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new IOException("Cannot open sticker asset: " + identifier + "/" + name);
            }
            int bytesRead;
            byte[] data = new byte[16384];
            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }

    @NonNull
    static Uri getStickerAssetUri(@NonNull String identifier, @NonNull String stickerName) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                .appendPath(StickerContentProvider.STICKERS_ASSET)
                .appendPath(identifier)
                .appendPath(stickerName)
                .build();
    }

    /**
     * Loads all sticker packs from the ContentProvider with their stickers.
     */
    @NonNull
    static ArrayList<StickerPack> fetchStickerPacks(@NonNull Context context) throws IllegalStateException {
        final ContentResolver contentResolver = context.getContentResolver();
        final Cursor cursor = contentResolver.query(StickerContentProvider.AUTHORITY_URI, null, null, null, null);
        ArrayList<StickerPack> stickerPackList = new ArrayList<>();
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                final String identifier = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.STICKER_PACK_IDENTIFIER_IN_QUERY));
                final String name = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.STICKER_PACK_NAME_IN_QUERY));
                final String publisher = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.STICKER_PACK_PUBLISHER_IN_QUERY));
                final String trayImageFile = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.STICKER_PACK_ICON_IN_QUERY));
                final String androidPlayStoreLink = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.ANDROID_APP_DOWNLOAD_LINK_IN_QUERY));
                final String iosAppStoreLink = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.IOS_APP_DOWNLOAD_LINK_IN_QUERY));
                final String publisherEmail = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.PUBLISHER_EMAIL));
                final String publisherWebsite = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.PUBLISHER_WEBSITE));
                final String privacyPolicyWebsite = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.PRIVACY_POLICY_WEBSITE));
                final String licenseAgreementWebsite = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.LICENSE_AGREEMENT_WEBSITE));
                final String imageDataVersion = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.IMAGE_DATA_VERSION));
                final boolean avoidCache = cursor.getShort(cursor.getColumnIndexOrThrow(StickerContentProvider.AVOID_CACHE)) > 0;
                final boolean animatedStickerPack = cursor.getShort(cursor.getColumnIndexOrThrow(StickerContentProvider.ANIMATED_STICKER_PACK)) > 0;

                final StickerPack stickerPack = new StickerPack(
                        identifier, name, publisher, trayImageFile,
                        publisherEmail, publisherWebsite, privacyPolicyWebsite,
                        licenseAgreementWebsite, imageDataVersion, avoidCache, animatedStickerPack);
                stickerPack.setAndroidPlayStoreLink(androidPlayStoreLink);
                stickerPack.setIosAppStoreLink(iosAppStoreLink);
                stickerPackList.add(stickerPack);
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }

        // Now load stickers for each pack
        for (StickerPack stickerPack : stickerPackList) {
            List<Sticker> stickers = fetchStickersForPack(context, stickerPack);
            stickerPack.setStickers(stickers);
        }

        // Check whitelist status
        for (StickerPack stickerPack : stickerPackList) {
            stickerPack.setIsWhitelisted(WhitelistCheck.isWhitelisted(context, stickerPack.identifier));
        }

        return stickerPackList;
    }

    @NonNull
    private static List<Sticker> fetchStickersForPack(@NonNull Context context, @NonNull StickerPack stickerPack) {
        List<Sticker> stickers = new ArrayList<>();
        final Uri uri = getStickerListUri(stickerPack.identifier);
        final ContentResolver contentResolver = context.getContentResolver();
        try (final Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                do {
                    final String name = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.STICKER_FILE_NAME_IN_QUERY));
                    final String emojisConcatenated = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.STICKER_FILE_EMOJI_IN_QUERY));
                    final String accessibilityText = cursor.getString(cursor.getColumnIndexOrThrow(StickerContentProvider.STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY));
                    List<String> emojis = new ArrayList<>();
                    if (emojisConcatenated != null) {
                        emojis = new ArrayList<>(Arrays.asList(emojisConcatenated.split(",")));
                    }
                    final Sticker sticker = new Sticker(name, emojis, accessibilityText);

                    // Get file size
                    try {
                        byte[] bytes = fetchStickerAsset(stickerPack.identifier, name, contentResolver);
                        sticker.setSize(bytes.length);
                    } catch (IOException e) {
                        sticker.setSize(0);
                    }

                    stickers.add(sticker);
                } while (cursor.moveToNext());
            }
        }
        return stickers;
    }

    @NonNull
    private static Uri getStickerListUri(@NonNull String identifier) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                .appendPath(StickerContentProvider.STICKERS)
                .appendPath(identifier)
                .build();
    }
}
