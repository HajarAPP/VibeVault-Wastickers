/*
 * VibeVault — WhatsApp Stickers App
 * Copyright (c) 2026 VibeVault. All rights reserved.
 */

package com.vibevault.stickers;

import com.vibevault.stickers.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class StickerPackListAdapter extends RecyclerView.Adapter<StickerPackListAdapter.ViewHolder> {

    public interface OnPackInteractionListener {
        void onAddToWhatsAppClicked(StickerPack pack);
        void onUnlockClicked(StickerPack pack);
        void onPackClicked(StickerPack pack);
    }

    private final List<StickerPack> stickerPacks;
    private final PremiumManager premiumManager;
    private final OnPackInteractionListener listener;
    private final Context context;

    public StickerPackListAdapter(@NonNull Context context,
                                   @NonNull List<StickerPack> stickerPacks,
                                   @NonNull PremiumManager premiumManager,
                                   @NonNull OnPackInteractionListener listener) {
        this.context = context;
        this.stickerPacks = stickerPacks;
        this.premiumManager = premiumManager;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sticker_pack, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StickerPack pack = stickerPacks.get(position);
        boolean isUnlocked = premiumManager.isPackUnlocked(pack.identifier);
        boolean isPremium = premiumManager.isPremiumPack(pack.identifier);
        boolean isWhitelisted = pack.getIsWhitelisted();

        // Pack info
        holder.packName.setText(pack.name);
        holder.packPublisher.setText(String.format("by %s", pack.publisher));
        holder.stickerCount.setText(String.format("%d stickers", pack.getStickers().size()));

        // Load tray icon from assets
        loadTrayIcon(holder.trayIcon, pack.identifier, pack.trayImageFile);

        // Badge
        if (isPremium && !isUnlocked) {
            holder.packBadge.setText(R.string.premium_badge);
            holder.packBadge.setBackgroundResource(R.drawable.badge_premium);
            holder.packBadge.setTextColor(context.getResources().getColor(R.color.cyber_bg_primary, null));
            holder.itemView.setBackgroundResource(R.drawable.card_sticker_pack_premium);
        } else if (isPremium) {
            holder.packBadge.setText("✓ UNLOCKED");
            holder.packBadge.setBackgroundResource(R.drawable.badge_free);
            holder.packBadge.setTextColor(context.getResources().getColor(R.color.cyber_bg_primary, null));
            holder.itemView.setBackgroundResource(R.drawable.card_sticker_pack);
        } else {
            holder.packBadge.setText(R.string.free_badge);
            holder.packBadge.setBackgroundResource(R.drawable.badge_free);
            holder.packBadge.setTextColor(context.getResources().getColor(R.color.cyber_bg_primary, null));
            holder.itemView.setBackgroundResource(R.drawable.card_sticker_pack);
        }

        // Buttons visibility
        if (isWhitelisted) {
            // Already added to WhatsApp
            holder.btnAddWhatsApp.setVisibility(View.GONE);
            holder.btnUnlock.setVisibility(View.GONE);
            holder.addedIndicator.setVisibility(View.VISIBLE);
            holder.lockIcon.setVisibility(View.GONE);
        } else if (isPremium && !isUnlocked) {
            // Locked premium pack
            holder.btnAddWhatsApp.setVisibility(View.GONE);
            holder.btnUnlock.setVisibility(View.VISIBLE);
            holder.addedIndicator.setVisibility(View.GONE);
            holder.lockIcon.setVisibility(View.VISIBLE);
        } else {
            // Free or unlocked pack
            holder.btnAddWhatsApp.setVisibility(View.VISIBLE);
            holder.btnUnlock.setVisibility(View.GONE);
            holder.addedIndicator.setVisibility(View.GONE);
            holder.lockIcon.setVisibility(View.GONE);
        }

        // Click listeners
        holder.btnAddWhatsApp.setOnClickListener(v -> listener.onAddToWhatsAppClicked(pack));
        holder.btnUnlock.setOnClickListener(v -> listener.onUnlockClicked(pack));
        holder.itemView.setOnClickListener(v -> {
            if (isUnlocked || !isPremium) {
                listener.onPackClicked(pack);
            } else {
                listener.onUnlockClicked(pack);
            }
        });
    }

    @Override
    public int getItemCount() {
        return stickerPacks.size();
    }

    private void loadTrayIcon(ImageView imageView, String identifier, String trayImageFile) {
        try {
            InputStream inputStream = context.getAssets().open(identifier + "/" + trayImageFile);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            imageView.setImageBitmap(bitmap);
            inputStream.close();
        } catch (IOException e) {
            // Use a placeholder if tray icon can't be loaded
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView trayIcon;
        TextView packName;
        TextView packPublisher;
        TextView stickerCount;
        TextView packBadge;
        ImageView lockIcon;
        TextView addedIndicator;
        Button btnAddWhatsApp;
        Button btnUnlock;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            trayIcon = itemView.findViewById(R.id.tray_icon);
            packName = itemView.findViewById(R.id.pack_name);
            packPublisher = itemView.findViewById(R.id.pack_publisher);
            stickerCount = itemView.findViewById(R.id.sticker_count);
            packBadge = itemView.findViewById(R.id.pack_badge);
            lockIcon = itemView.findViewById(R.id.lock_icon);
            addedIndicator = itemView.findViewById(R.id.added_indicator);
            btnAddWhatsApp = itemView.findViewById(R.id.btn_add_whatsapp);
            btnUnlock = itemView.findViewById(R.id.btn_unlock);
        }
    }
}
