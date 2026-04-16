/*
 * VibeVault — WhatsApp Stickers App
 * Copyright (c) 2026 VibeVault. All rights reserved.
 */

package com.vibevault.stickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class StickerPreviewAdapter extends RecyclerView.Adapter<StickerPreviewAdapter.ViewHolder> {

    private final List<Sticker> stickers;
    private final String packIdentifier;
    private final Context context;

    public StickerPreviewAdapter(@NonNull Context context,
                                  @NonNull String packIdentifier,
                                  @NonNull List<Sticker> stickers) {
        this.context = context;
        this.packIdentifier = packIdentifier;
        this.stickers = stickers;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sticker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Sticker sticker = stickers.get(position);
        loadStickerImage(holder.stickerImage, packIdentifier, sticker.imageFileName);
    }

    @Override
    public int getItemCount() {
        return stickers.size();
    }

    private void loadStickerImage(ImageView imageView, String identifier, String fileName) {
        try {
            InputStream inputStream = context.getAssets().open(identifier + "/" + fileName);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            imageView.setImageBitmap(bitmap);
            inputStream.close();
        } catch (IOException e) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView stickerImage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            stickerImage = itemView.findViewById(R.id.sticker_image);
        }
    }
}
