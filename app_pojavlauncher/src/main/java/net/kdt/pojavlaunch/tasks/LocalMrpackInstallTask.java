package net.kdt.pojavlaunch.tasks;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceInstaller;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.lifecycle.ContextExecutorTask;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import org.apache.commons.io.IOUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

public class LocalMrpackInstallTask implements ContextExecutorTask {
    private final Uri mUri;

    public LocalMrpackInstallTask(Uri uri) {
        this.mUri = uri;
    }

    @Override
    public void executeWithApplication(android.content.Context application) {
        executeWithContext(application);
    }

    @Override
    public void executeWithActivity(Activity activity) {
        executeWithContext(activity);
    }

    private void executeWithContext(android.content.Context context) {
        new Thread(() -> {
            File tempMrpack = null;
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_downloading_metadata, 0, 100);
            try {
                // 1. Copy Uri to temporary file
                tempMrpack = new File(Tools.DIR_CACHE, "temp_import.mrpack");
                try (InputStream is = context.getContentResolver().openInputStream(mUri);
                     FileOutputStream fos = new FileOutputStream(tempMrpack)) {
                    if (is == null) throw new IOException("Failed to open file from uri");
                    IOUtils.copy(is, fos);
                }

                // 2. Read modrinth.index.json to get modpack info
                ModrinthIndex modrinthIndex;
                try (ZipFile modpackZipFile = new ZipFile(tempMrpack)) {
                    modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                            Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                            ModrinthIndex.class);
                }

                String modpackName = (modrinthIndex.name + " " + modrinthIndex.versionId).trim().replaceAll("[\\\\/:*?\"<>| \\t\\n]", "_");
                if (modpackName.length() > 255) {
                    modpackName = modpackName.substring(0, 255);
                }

                // 3. Create Instance
                Instance instance = Instances.createInstance(i -> {
                    i.name = modrinthIndex.name;
                }, modpackName.substring(0, Math.min(16, modpackName.length())));

                try {
                    // 4. Install mrpack
                    ModrinthApi api = new ModrinthApi();
                    ModLoader modLoaderInfo = api.installMrpack(tempMrpack, instance.getGameDirectory());

                    if (modLoaderInfo == null) throw new IOException("Unknown modpack mod loader information");

                    if (modLoaderInfo.requiresGuiInstallation()) {
                        InstanceInstaller instanceInstaller = modLoaderInfo.createInstaller();
                        if (instanceInstaller == null) throw new IOException("Failed to prepare data for instance installation");
                        instance.installer = instanceInstaller;
                    } else {
                        String versionId = modLoaderInfo.installHeadlessly();
                        if (versionId == null) throw new IOException("Unknown mod loader version");
                        instance.versionId = versionId;
                    }
                    instance.write();

                    Instances.setSelectedInstance(instance);
                    if (modLoaderInfo.requiresGuiInstallation()) {
                        instance.installer.start();
                    }
                } catch (IOException e) {
                    Instances.removeInstance(instance);
                    throw e;
                }

            } catch (Exception e) {
                Log.e("LocalMrpackInstallTask", "Failed to install modpack", e);
                Tools.showErrorRemote(e);
            } finally {
                if (tempMrpack != null && tempMrpack.exists()) {
                    tempMrpack.delete();
                }
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
        }).start();
    }
}
