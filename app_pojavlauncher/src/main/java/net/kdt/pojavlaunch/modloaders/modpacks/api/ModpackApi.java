package net.kdt.pojavlaunch.modloaders.modpacks.api;


import android.content.Context;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;

import java.io.File;
import java.io.IOException;

/**
 *
 */
public interface ModpackApi {

    /**
     * @param searchFilters Filters
     * @param previousPageResult The result from the previous page
     * @return the list of mod items from specified offset
     */
    SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult);

    /**
     * @param searchFilters Filters
     * @return A list of mod items
     */
    default SearchResult searchMod(SearchFilters searchFilters) {
        return searchMod(searchFilters, null);
    }

    /**
     * Fetch the mod details
     * @param item The moditem that was selected
     * @return Detailed data about a mod(pack)
     */
    ModDetail getModDetails(ModItem item);

    /**
     * Download and install the modpack
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    default void handleModpackInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        // Doing this here since when starting installation, the progress does not start immediately
        // which may lead to two concurrent installations (very bad)
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                installModpack(modDetail, selectedVersion);
            }catch (IOException e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            }
        });
    }

    /**
     * Install the mod(pack).
     * May require the download of additional files.
     * May requires launching the installation of a modloader
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    ModLoader installModpack(ModDetail modDetail, int selectedVersion) throws IOException;

    /**
     * Handle single mod installation on a separated thread
     */
    default void handleModInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                installMod(modDetail, selectedVersion);
            }catch (IOException e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            }
        });
    }

    /**
     * Default implementation to directly stream a .jar into the active instance's mods folder
     */
    default void installMod(ModDetail modDetail, int selectedVersion) throws IOException {
        String versionUrl = modDetail.versionUrls[selectedVersion];
        
        net.kdt.pojavlaunch.instances.Instance instance = net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
        if(instance == null) throw new IOException("No instance selected");
        
        File modsDir = new File(instance.getGameDirectory(), "mods");
        if(!modsDir.exists()) modsDir.mkdirs();
        
        String fileName = net.kdt.pojavlaunch.utils.FileUtils.getFileName(versionUrl);
        if(fileName == null || fileName.isEmpty()) {
             fileName = modDetail.title.replaceAll("[^a-zA-Z0-9.-]", "_") + "-" + modDetail.versionNames[selectedVersion] + ".jar";
        }
        fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
        
        File modFile = new File(modsDir, fileName);
        
        byte[] downloadBuffer = new byte[8192];
        net.kdt.pojavlaunch.utils.DownloadUtils.downloadFileMonitored(versionUrl, modFile, downloadBuffer,
                new net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                        ProgressLayout.INSTALL_MODPACK));
                        
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
    }
}
