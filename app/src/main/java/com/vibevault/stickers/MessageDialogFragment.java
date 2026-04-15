/*
 * VibeVault — WhatsApp Stickers App
 * Copyright (c) 2026 VibeVault. All rights reserved.
 */

package com.vibevault.stickers;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class MessageDialogFragment extends DialogFragment {
    private static final String ARG_TITLE_ID = "title_id";
    private static final String ARG_MESSAGE = "message";

    public static MessageDialogFragment newInstance(@StringRes int titleId, String message) {
        MessageDialogFragment fragment = new MessageDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE_ID, titleId);
        args.putString(ARG_MESSAGE, message);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @StringRes int titleId = getArguments().getInt(ARG_TITLE_ID);
        String message = getArguments().getString(ARG_MESSAGE);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity())
                .setTitle(titleId)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dismiss());

        return dialogBuilder.create();
    }
}
