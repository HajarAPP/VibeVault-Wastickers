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
import com.hajmidapp.vibevault.R;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

class WhitelistCheck {
    static final String CONSUMER_WHATSAPP_PACKAGE_NAME = "com.whatsapp";
    static final String SMB_WHATSAPP_PACKAGE_NAME = "com.whatsapp.w4b";
    private static final String CONTENT_PROVIDER = ".provider.sticker_whitelist_check";
    private static final String QUERY_PATH = "is_whitelisted";
    private static final String AUTHORITY_QUERY_PARAM = "authority";
    private static final String IDENTIFIER_QUERY_PARAM = "identifier";
    private static final String QUERY_RESULT_COLUMN_NAME = "result";

    static boolean isWhitelisted(@NonNull Context context, @NonNull String identifier) {
        try {
            boolean isWhitelistedInConsumerApp = isWhitelistedFromProvider(context, identifier, CONSUMER_WHATSAPP_PACKAGE_NAME);
            boolean isWhitelistedInSmbApp = isWhitelistedFromProvider(context, identifier, SMB_WHATSAPP_PACKAGE_NAME);
            return isWhitelistedInConsumerApp || isWhitelistedInSmbApp;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isStickerPackWhitelistedInWhatsAppConsumer(@NonNull Context context, @NonNull String identifier) {
        return isWhitelistedFromProvider(context, identifier, CONSUMER_WHATSAPP_PACKAGE_NAME);
    }

    static boolean isStickerPackWhitelistedInWhatsAppSmb(@NonNull Context context, @NonNull String identifier) {
        return isWhitelistedFromProvider(context, identifier, SMB_WHATSAPP_PACKAGE_NAME);
    }

    private static boolean isWhitelistedFromProvider(@NonNull Context context, @NonNull String identifier, @NonNull String whatsappPackageName) {
        final PackageManager packageManager = context.getPackageManager();
        if (isPackageInstalled(whatsappPackageName, packageManager)) {
            final String whatsappProviderAuthority = whatsappPackageName + CONTENT_PROVIDER;
            final ProviderInfo providerInfo = packageManager.resolveContentProvider(whatsappProviderAuthority, PackageManager.GET_META_DATA);
            if (providerInfo == null) {
                return false;
            }
            final Uri queryUri = new Uri.Builder()
                    .scheme("content")
                    .authority(whatsappProviderAuthority)
                    .appendPath(QUERY_PATH)
                    .appendQueryParameter(AUTHORITY_QUERY_PARAM, BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                    .appendQueryParameter(IDENTIFIER_QUERY_PARAM, identifier)
                    .build();
            try (final Cursor cursor = context.getContentResolver().query(queryUri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int whiteListResult = cursor.getInt(cursor.getColumnIndexOrThrow(QUERY_RESULT_COLUMN_NAME));
                    return whiteListResult == 1;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    static boolean isPackageInstalled(@NonNull String packageName, @NonNull PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean isWhatsAppConsumerAppInstalled(@NonNull PackageManager packageManager) {
        return isPackageInstalled(CONSUMER_WHATSAPP_PACKAGE_NAME, packageManager);
    }

    static boolean isWhatsAppSmbAppInstalled(@NonNull PackageManager packageManager) {
        return isPackageInstalled(SMB_WHATSAPP_PACKAGE_NAME, packageManager);
    }
}
