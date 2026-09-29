package net.kdt.pojavlaunch.fragments;

import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import git.artdeell.mojo.R;

public class InstalledModAdapter extends RecyclerView.Adapter<InstalledModAdapter.ViewHolder> {

    private final List<InstalledModInfo> mMods;
    private final OnModInteractionListener mListener;
    private final ExecutorService mIconLoader = Executors.newFixedThreadPool(3);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public interface OnModInteractionListener {
        void onModToggle(InstalledModInfo mod, boolean isEnabled);
        void onModDelete(InstalledModInfo mod);
    }

    public InstalledModAdapter(List<InstalledModInfo> mods, OnModInteractionListener listener) {
        this.mMods = mods;
        this.mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mod_installed, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InstalledModInfo mod = mMods.get(position);

        holder.name.setText(mod.name);
        holder.version.setText(mod.version.isEmpty() ? "Unknown Version" : "Version " + mod.version);
        holder.description.setText(mod.description);

        // Reset icon to placeholder
        holder.icon.setImageResource(R.drawable.ic_add);
        holder.icon.setTag(mod.file.getAbsolutePath());

        // Load icon asynchronously from jar
        if (mod.iconEntryPath != null && !mod.iconEntryPath.isEmpty()) {
            final String jarPath = mod.file.getAbsolutePath();
            final String iconPath = mod.iconEntryPath;
            mIconLoader.execute(() -> {
                try (ZipFile zip = new ZipFile(mod.file)) {
                    ZipEntry iconEntry = zip.getEntry(iconPath);
                    if (iconEntry != null) {
                        try (InputStream is = zip.getInputStream(iconEntry)) {
                            android.graphics.Bitmap bmp = BitmapFactory.decodeStream(is);
                            if (bmp != null) {
                                mMainHandler.post(() -> {
                                    if (jarPath.equals(holder.icon.getTag())) {
                                        holder.icon.setImageBitmap(bmp);
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception ignored) {}
            });
        }

        // Remove listener to prevent triggering on layout pass
        holder.toggleSwitch.setOnCheckedChangeListener(null);
        holder.toggleSwitch.setChecked(mod.isEnabled);

        holder.toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mListener.onModToggle(mod, isChecked);
        });

        holder.deleteButton.setOnClickListener(v -> mListener.onModDelete(mod));
    }

    @Override
    public int getItemCount() {
        return mMods.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final ImageView icon;
        public final TextView name;
        public final TextView version;
        public final TextView description;
        public final Switch toggleSwitch;
        public final ImageButton deleteButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.mod_item_icon);
            name = itemView.findViewById(R.id.mod_item_name);
            version = itemView.findViewById(R.id.mod_item_version);
            description = itemView.findViewById(R.id.mod_item_description);
            toggleSwitch = itemView.findViewById(R.id.mod_item_switch);
            deleteButton = itemView.findViewById(R.id.mod_item_delete);
        }
    }
}
