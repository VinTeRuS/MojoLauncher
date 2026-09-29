package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    private final ActivityResultLauncher<Object> mMrpackLauncher =
            registerForActivityResult(new net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension("mrpack"), (data) -> {
                if (data != null) net.kdt.pojavlaunch.lifecycle.ContextExecutor.execute(new net.kdt.pojavlaunch.tasks.LocalMrpackInstallTask(data));
            });

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.social_media_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mSettingsMenuButton = view.findViewById(R.id.setting_menu_button);
        Button mManageInstanceButton = view.findViewById(R.id.manage_instance_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mDiscordButton
                .setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        mCustomControlButton
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mSettingsMenuButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment.class, "setting_fragment", null));
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mManageInstanceButton.setOnClickListener(
                v -> Tools.swapFragment(requireActivity(), ModManagerFragment.class, ModManagerFragment.TAG, null));

        Button mInstallMrpackButton = view.findViewById(R.id.install_mrpack_button);
        if (mInstallMrpackButton != null) {
            mInstallMrpackButton.setOnClickListener(v -> {
                if (net.kdt.pojavlaunch.progresskeeper.ProgressKeeper.getTaskCount() == 0) {
                    mMrpackLauncher.launch(null);
                } else {
                    android.widget.Toast.makeText(requireContext(), R.string.tasks_ongoing, android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }

        Button mShareLogButton = view.findViewById(R.id.share_log_button);
        if (mShareLogButton != null) {
            mShareLogButton.setOnClickListener(v -> Tools.shareLog(requireContext()));
        }

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mNewsButton.setOnLongClickListener((v) -> {
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
        
        // Setup Drag & Drop for reorderable blocks
        View communityBlock = view.findViewById(R.id.menu_category_community_container);
        View quickActionsBlock = view.findViewById(R.id.menu_category_quick_actions_container);
        View manageInstanceBlock = view.findViewById(R.id.menu_category_manage_instance_container);
        
        if (communityBlock != null && quickActionsBlock != null && manageInstanceBlock != null) {
            setupDraggableBlock(communityBlock);
            setupDraggableBlock(quickActionsBlock);
            setupDraggableBlock(manageInstanceBlock);
            
            // Restore saved order
            android.view.ViewGroup parent = (android.view.ViewGroup) communityBlock.getParent();
            restoreLayoutOrder(parent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void setupDraggableBlock(View block) {
        block.setOnLongClickListener(v -> {
            android.content.ClipData data = android.content.ClipData.newPlainText("", "");
            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                v.startDragAndDrop(data, shadowBuilder, v, 0);
            } else {
                v.startDrag(data, shadowBuilder, v, 0);
            }
            return true;
        });
        
        block.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case android.view.DragEvent.ACTION_DROP:
                    View draggedView = (View) event.getLocalState();
                    if (draggedView == null || draggedView == v) return true;
                    
                    android.view.ViewGroup parent = (android.view.ViewGroup) draggedView.getParent();
                    if (parent != v.getParent()) return false;
                    
                    int targetIndex = parent.indexOfChild(v);
                    parent.removeView(draggedView);
                    parent.addView(draggedView, targetIndex);
                    
                    saveLayoutOrder(parent);
                    return true;
            }
            return true;
        });
    }

    private void saveLayoutOrder(android.view.ViewGroup parent) {
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getId() != View.NO_ID) {
                order.append(child.getId()).append(",");
            }
        }
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("pojav_launcher_prefs", android.content.Context.MODE_PRIVATE);
        prefs.edit().putString("main_menu_order", order.toString()).apply();
    }

    private void restoreLayoutOrder(android.view.ViewGroup parent) {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("pojav_launcher_prefs", android.content.Context.MODE_PRIVATE);
        String orderString = prefs.getString("main_menu_order", null);
        if (orderString == null) return;
        
        String[] ids = orderString.split(",");
        for (String idStr : ids) {
            if (idStr.isEmpty()) continue;
            try {
                int id = Integer.parseInt(idStr);
                View child = parent.findViewById(id);
                if (child != null && child.getParent() == parent) {
                    parent.removeView(child);
                    parent.addView(child);
                }
            } catch (NumberFormatException ignored) {}
        }
    }
}
