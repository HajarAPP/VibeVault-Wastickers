/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * Modified for VibeVault by VibeVault Team.
 * Package: com.vibevault.stickers
 */

package com.vibevault.stickers;
import com.hajmidapp.vibevault.R;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * StickerContentProvider — The core bridge between this app and WhatsApp.
 *
 * WhatsApp queries this ContentProvider to:
 *   1. Discover all available sticker packs (METADATA)
 *   2. Get sticker list for a specific pack (STICKERS)
 *   3. Fetch individual sticker/tray image files (STICKERS_ASSET)
 *
 * CRITICAL: Do NOT modify the UriMatcher codes (1–5) or the column name constants.
 * These follow WhatsApp's hardcoded protocol. Changing them will break integration.
 */
public class StickerContentProvider extends ContentProvider {

    private static final String TAG = "StickerContentProvider";

    // ════════════════════════════════════════════════════════════
    // Column names for WhatsApp queries — DO NOT CHANGE
    // ════════════════════════════════════════════════════════════

    public static final String STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier";
    public static final String STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name";
    public static final String STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher";
    public static final String STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon";
    public static final String ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link";
    public static final String IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link";
    public static final String PUBLISHER_EMAIL = "sticker_pack_publisher_email";
    public static final String PUBLISHER_WEBSITE = "sticker_pack_publisher_website";
    public static final String PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website";
    public static final String LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website";
    public static final String IMAGE_DATA_VERSION = "image_data_version";
    public static final String AVOID_CACHE = "whatsapp_will_not_cache_stickers";
    public static final String ANIMATED_STICKER_PACK = "animated_sticker_pack";

    public static final String STICKER_FILE_NAME_IN_QUERY = "sticker_file_name";
    public static final String STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji";
    public static final String STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY = "sticker_accessibility_text";

    private static final String CONTENT_FILE_NAME = "contents.json";

    // ════════════════════════════════════════════════════════════
    // URI structure — DO NOT CHANGE match codes
    // ════════════════════════════════════════════════════════════

    static final String METADATA = "metadata";
    private static final int METADATA_CODE = 1;
    private static final int METADATA_CODE_FOR_SINGLE_PACK = 2;

    static final String STICKERS = "stickers";
    private static final int STICKERS_CODE = 3;

    static final String STICKERS_ASSET = "stickers_asset";
    private static final int STICKERS_ASSET_CODE = 4;
    private static final int STICKER_PACK_TRAY_ICON_CODE = 5;

    /**
     * Authority URI used by StickerPackLoader to query for all pack metadata.
     */
    public static final Uri AUTHORITY_URI = new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(BuildConfig.CONTENT_PROVIDER_AUTHORITY)
            .appendPath(METADATA)
            .build();

    private UriMatcher matcher;
    private List<StickerPack> stickerPackList;
    private boolean isInitialized = false;

    // ════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════

    @Override
    public boolean onCreate() {
        final String authority = BuildConfig.CONTENT_PROVIDER_AUTHORITY;
        if (!authority.startsWith(Objects.requireNonNull(getContext()).getPackageName())) {
            throw new IllegalStateException(
                    "Your authority (" + authority + ") for the content provider should start "
                            + "with your package name: " + getContext().getPackageName());
        }

        initializeUriMatcher(authority);
        return true;
    }

    /**
     * Builds the UriMatcher with all valid URI patterns.
     * Uses an instance-level matcher to avoid duplicate URI entries if the
     * provider is recreated.
     */
    private synchronized void initializeUriMatcher(@NonNull String authority) {
        if (isInitialized) {
            return;
        }

        matcher = new UriMatcher(UriMatcher.NO_MATCH);

        // Code 1: Get metadata for ALL sticker packs
        matcher.addURI(authority, METADATA, METADATA_CODE);

        // Code 2: Get metadata for a SINGLE sticker pack by identifier
        matcher.addURI(authority, METADATA + "/*", METADATA_CODE_FOR_SINGLE_PACK);

        // Code 3: Get the list of stickers for a specific pack
        matcher.addURI(authority, STICKERS + "/*", STICKERS_CODE);

        // Codes 4 & 5: Register each sticker file and tray icon as a valid asset URI
        for (StickerPack stickerPack : getStickerPackList()) {
            // Code 5: Tray icon
            matcher.addURI(authority,
                    STICKERS_ASSET + "/" + stickerPack.identifier + "/" + stickerPack.trayImageFile,
                    STICKER_PACK_TRAY_ICON_CODE);

            // Code 4: Individual sticker images
            for (Sticker sticker : stickerPack.getStickers()) {
                matcher.addURI(authority,
                        STICKERS_ASSET + "/" + stickerPack.identifier + "/" + sticker.imageFileName,
                        STICKERS_ASSET_CODE);
            }
        }

        isInitialized = true;
    }

    // ════════════════════════════════════════════════════════════
    // Query handling
    // ════════════════════════════════════════════════════════════

    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        final int code = matcher.match(uri);
        switch (code) {
            case METADATA_CODE:
                return getPackForAllStickerPacks(uri);
            case METADATA_CODE_FOR_SINGLE_PACK:
                return getCursorForSingleStickerPack(uri);
            case STICKERS_CODE:
                return getStickersForAStickerPack(uri);
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) {
        final int matchCode = matcher.match(uri);
        if (matchCode == STICKERS_ASSET_CODE || matchCode == STICKER_PACK_TRAY_ICON_CODE) {
            return getImageAsset(uri);
        }
        return null;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        final int matchCode = matcher.match(uri);
        switch (matchCode) {
            case METADATA_CODE:
                return "vnd.android.cursor.dir/vnd."
                        + BuildConfig.CONTENT_PROVIDER_AUTHORITY + "." + METADATA;
            case METADATA_CODE_FOR_SINGLE_PACK:
                return "vnd.android.cursor.item/vnd."
                        + BuildConfig.CONTENT_PROVIDER_AUTHORITY + "." + METADATA;
            case STICKERS_CODE:
                return "vnd.android.cursor.dir/vnd."
                        + BuildConfig.CONTENT_PROVIDER_AUTHORITY + "." + STICKERS;
            case STICKERS_ASSET_CODE:
                return "image/webp";
            case STICKER_PACK_TRAY_ICON_CODE:
                return "image/webp";
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    // ════════════════════════════════════════════════════════════
    // Data loading
    // ════════════════════════════════════════════════════════════

    /**
     * Reads and parses contents.json from the assets directory.
     * Synchronized to prevent concurrent reads during initialization.
     */
    private synchronized void readContentFile(@NonNull Context context) {
        try (java.io.InputStream contentsInputStream = context.getAssets().open(CONTENT_FILE_NAME)) {
            stickerPackList = ContentFileParser.parseStickerPacks(contentsInputStream);
        } catch (IOException | IllegalStateException e) {
            throw new RuntimeException(
                    CONTENT_FILE_NAME + " file has issues: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the cached sticker pack list, reading from assets if not yet loaded.
     */
    List<StickerPack> getStickerPackList() {
        if (stickerPackList == null) {
            readContentFile(Objects.requireNonNull(getContext()));
        }
        return stickerPackList;
    }

    // ════════════════════════════════════════════════════════════
    // Cursor builders (metadata responses to WhatsApp)
    // ════════════════════════════════════════════════════════════

    private Cursor getPackForAllStickerPacks(@NonNull Uri uri) {
        return getStickerPackInfo(uri, getStickerPackList());
    }

    private Cursor getCursorForSingleStickerPack(@NonNull Uri uri) {
        final String identifier = uri.getLastPathSegment();
        for (StickerPack stickerPack : getStickerPackList()) {
            if (identifier != null && identifier.equals(stickerPack.identifier)) {
                return getStickerPackInfo(uri, Collections.singletonList(stickerPack));
            }
        }
        return getStickerPackInfo(uri, new ArrayList<>());
    }

    /**
     * Builds a MatrixCursor containing sticker pack metadata.
     * Column order must match WhatsApp's expected schema exactly.
     */
    @NonNull
    private Cursor getStickerPackInfo(@NonNull Uri uri,
                                      @NonNull List<StickerPack> stickerPackList) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                STICKER_PACK_IDENTIFIER_IN_QUERY,
                STICKER_PACK_NAME_IN_QUERY,
                STICKER_PACK_PUBLISHER_IN_QUERY,
                STICKER_PACK_ICON_IN_QUERY,
                ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
                IOS_APP_DOWNLOAD_LINK_IN_QUERY,
                PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE,
                PRIVACY_POLICY_WEBSITE,
                LICENSE_AGREEMENT_WEBSITE,
                IMAGE_DATA_VERSION,
                AVOID_CACHE,
                ANIMATED_STICKER_PACK,
        });

        for (StickerPack stickerPack : stickerPackList) {
            MatrixCursor.RowBuilder builder = cursor.newRow();
            builder.add(stickerPack.identifier);
            builder.add(stickerPack.name);
            builder.add(stickerPack.publisher);
            builder.add(stickerPack.trayImageFile);
            builder.add(stickerPack.androidPlayStoreLink);
            builder.add(stickerPack.iosAppStoreLink);
            builder.add(stickerPack.publisherEmail);
            builder.add(stickerPack.publisherWebsite);
            builder.add(stickerPack.privacyPolicyWebsite);
            builder.add(stickerPack.licenseAgreementWebsite);
            builder.add(stickerPack.imageDataVersion);
            builder.add(stickerPack.avoidCache ? 1 : 0);
            builder.add(stickerPack.animatedStickerPack ? 1 : 0);
        }

        cursor.setNotificationUri(
                Objects.requireNonNull(getContext()).getContentResolver(), uri);
        return cursor;
    }

    /**
     * Returns cursor with sticker file names and emojis for a specific pack.
     */
    @NonNull
    private Cursor getStickersForAStickerPack(@NonNull Uri uri) {
        final String identifier = uri.getLastPathSegment();
        MatrixCursor cursor = new MatrixCursor(new String[]{
                STICKER_FILE_NAME_IN_QUERY,
                STICKER_FILE_EMOJI_IN_QUERY,
                STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY
        });

        for (StickerPack stickerPack : getStickerPackList()) {
            if (identifier != null && identifier.equals(stickerPack.identifier)) {
                for (Sticker sticker : stickerPack.getStickers()) {
                    cursor.addRow(new Object[]{
                            sticker.imageFileName,
                            TextUtils.join(",", sticker.emojis),
                            sticker.accessibilityText
                    });
                }
            }
        }

        cursor.setNotificationUri(
                Objects.requireNonNull(getContext()).getContentResolver(), uri);
        return cursor;
    }

    // ════════════════════════════════════════════════════════════
    // Asset file serving
    // ════════════════════════════════════════════════════════════

    /**
     * Returns an AssetFileDescriptor for the requested sticker or tray image.
     * Validates that the requested file actually belongs to a known sticker pack.
     */
    private AssetFileDescriptor getImageAsset(Uri uri) throws IllegalArgumentException {
        AssetManager am = Objects.requireNonNull(getContext()).getAssets();
        final List<String> pathSegments = uri.getPathSegments();

        if (pathSegments.size() != 3) {
            throw new IllegalArgumentException(
                    "Path segments should be 3, uri is: " + uri);
        }

        String fileName = pathSegments.get(pathSegments.size() - 1);
        final String identifier = pathSegments.get(pathSegments.size() - 2);

        if (TextUtils.isEmpty(identifier)) {
            throw new IllegalArgumentException("Identifier is empty, uri: " + uri);
        }
        if (TextUtils.isEmpty(fileName)) {
            throw new IllegalArgumentException("File name is empty, uri: " + uri);
        }

        // Security: Only serve files that are registered in contents.json
        for (StickerPack stickerPack : getStickerPackList()) {
            if (identifier.equals(stickerPack.identifier)) {
                if (fileName.equals(stickerPack.trayImageFile)) {
                    return fetchFile(uri, am, fileName, identifier);
                } else {
                    for (Sticker sticker : stickerPack.getStickers()) {
                        if (fileName.equals(sticker.imageFileName)) {
                            return fetchFile(uri, am, fileName, identifier);
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Opens an asset file from the assets/{identifier}/{fileName} path.
     */
    private AssetFileDescriptor fetchFile(@NonNull Uri uri, @NonNull AssetManager am,
                                          @NonNull String fileName,
                                          @NonNull String identifier) {
        try {
            return am.openFd(identifier + "/" + fileName);
        } catch (IOException e) {
            Log.e(TAG, "IOException when getting asset file, uri:" + uri, e);
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════
    // Unsupported operations (read-only provider)
    // ════════════════════════════════════════════════════════════

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported — read-only provider");
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Not supported — read-only provider");
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported — read-only provider");
    }
}
