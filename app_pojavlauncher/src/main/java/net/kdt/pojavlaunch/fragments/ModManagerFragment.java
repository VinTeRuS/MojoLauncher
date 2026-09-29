package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;

public class ModManagerFragment extends Fragment implements InstalledModAdapter.OnModInteractionListener {
    public static final String TAG = "ModManagerFragment";

    private RecyclerView mRecyclerView;
    private TextView mEmptyText;
    private InstalledModAdapter mAdapter;
    private final List<InstalledModInfo> mModList = new ArrayList<>();
    
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    private final ActivityResultLauncher<Object> mModpackInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("mrpack"), (data)->{
                if(data != null) net.kdt.pojavlaunch.lifecycle.ContextExecutor.execute(new net.kdt.pojavlaunch.tasks.LocalMrpackInstallTask(data));
            });

    public ModManagerFragment() {
        super(R.layout.fragment_mod_manager);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton backButton = view.findViewById(R.id.manager_back_button);
        TextView title = view.findViewById(R.id.manager_title);
        
        ImageButton addModBtn = view.findViewById(R.id.manager_add_mod);
        ImageButton moreOptionsBtn = view.findViewById(R.id.manager_more_options);

        mRecyclerView = view.findViewById(R.id.installed_mods_list);
        mEmptyText = view.findViewById(R.id.empty_mods_text);

        mAdapter = new InstalledModAdapter(mModList, this);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setAdapter(mAdapter);

        Instance currentInstance = Instances.loadSelectedInstance();
        if (currentInstance != null) {
            title.setText(currentInstance.name + " - Mods");
        }

        backButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null));
        
        addModBtn.setOnClickListener(v -> {
            if (!isModdedInstance(currentInstance)) {
                Toast.makeText(requireContext(), "This instance does not support mods. Install Fabric/Forge first.", Toast.LENGTH_LONG).show();
                return;
            }
            Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null);
        });
        
        moreOptionsBtn.setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), v);
            popup.getMenu().add(0, 1, 0, getString(R.string.main_install_jar_file));
            popup.getMenu().add(0, 2, 0, getString(R.string.mcl_button_open_directory));
            popup.getMenu().add(0, 3, 0, getString(R.string.main_install_modpack));

            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        if (!isModdedInstance(currentInstance)) {
                            Toast.makeText(requireContext(), "This instance does not support mods.", Toast.LENGTH_LONG).show();
                            return true;
                        }
                        runInstallerWithConfirmation();
                        return true;
                    case 2:
                        openGameDirectory(requireContext());
                        return true;
                    case 3:
                        runModpackInstallerWithConfirmation();
                        return true;
                }
                return false;
            });
            popup.show();
        });

        loadMods();
    }

    private void loadMods() {
        mModList.clear();
        mAdapter.notifyDataSetChanged();
        mEmptyText.setVisibility(View.GONE);
        mEmptyText.setText("Loading mods...");
        mEmptyText.setVisibility(View.VISIBLE);

        mExecutor.execute(() -> {
            Instance instance = Instances.loadSelectedInstance();
            if (instance == null) return;
            
            File modsDir = new File(instance.getGameDirectory(), "mods");
            if (modsDir.exists() && modsDir.isDirectory()) {
                File[] files = modsDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName();
                        if (name.endsWith(".jar") || name.endsWith(".disabled")) {
                            InstalledModInfo info = new InstalledModInfo(file);
                            info.loadMetadata();
                            mModList.add(info);
                        }
                    }
                }
            }

            // Sort alphabetically by name
            mModList.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

            requireActivity().runOnUiThread(() -> {
                mAdapter.notifyDataSetChanged();
                if (mModList.isEmpty()) {
                    mEmptyText.setText("No mods installed");
                    mEmptyText.setVisibility(View.VISIBLE);
                } else {
                    mEmptyText.setVisibility(View.GONE);
                }
            });
        });
    }

    @Override
    public void onModToggle(InstalledModInfo mod, boolean isEnabled) {
        String name = mod.file.getName();
        File newFile;
        if (isEnabled && name.endsWith(".disabled")) {
            newFile = new File(mod.file.getParent(), name.substring(0, name.length() - ".disabled".length()));
        } else if (!isEnabled && name.endsWith(".jar")) {
            newFile = new File(mod.file.getParent(), name + ".disabled");
        } else {
            return;
        }

        if (mod.file.renameTo(newFile)) {
            mod.file = newFile;
            mod.isEnabled = isEnabled;
        } else {
            Toast.makeText(requireContext(), "Failed to toggle mod", Toast.LENGTH_SHORT).show();
            // Revert UI toggle state by notifying adapter
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onModDelete(InstalledModInfo mod) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Mod")
                .setMessage("Are you sure you want to delete " + mod.name + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (mod.file.delete()) {
                        mModList.remove(mod);
                        mAdapter.notifyDataSetChanged();
                        if (mModList.isEmpty()) mEmptyText.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete mod", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }

    private void runModpackInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModpackInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            Tools.openPath(context, gameDirectory, false);
        } else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isModdedInstance(Instance instance) {
        if (instance == null || instance.versionId == null) return false;
        String vId = instance.versionId.toLowerCase(java.util.Locale.ROOT);
        return vId.contains("fabric") || vId.contains("forge") || vId.contains("quilt") || 
               vId.contains("neoforge") || vId.contains("optifine") || vId.contains("liteloader");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }
}
