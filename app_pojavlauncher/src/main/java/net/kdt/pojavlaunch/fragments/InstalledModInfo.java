package net.kdt.pojavlaunch.fragments;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InstalledModInfo {
    public File file;
    public String name;
    public String version;
    public String description;
    public boolean isEnabled;
    public String iconEntryPath; // path inside zip, e.g. "assets/mymod/icon.png"

    public InstalledModInfo(File file) {
        this.file = file;
        this.name = file.getName();
        this.version = "";
        this.description = "No description available.";
        this.isEnabled = !file.getName().endsWith(".disabled");
    }

    public void loadMetadata() {
        try (ZipFile zipFile = new ZipFile(file)) {
            // Check for Fabric
            ZipEntry fabricEntry = zipFile.getEntry("fabric.mod.json");
            if (fabricEntry != null) {
                String content = readZipEntry(zipFile, fabricEntry);
                JSONObject json = new JSONObject(content);
                this.name = json.optString("name", this.name);
                this.version = json.optString("version", "");
                this.description = json.optString("description", this.description);
                this.iconEntryPath = json.optString("icon", null);
                return;
            }

            // Check for Quilt
            ZipEntry quiltEntry = zipFile.getEntry("quilt.mod.json");
            if (quiltEntry != null) {
                String content = readZipEntry(zipFile, quiltEntry);
                JSONObject root = new JSONObject(content);
                if (root.has("quilt_loader")) {
                    JSONObject metadata = root.getJSONObject("quilt_loader").getJSONObject("metadata");
                    this.name = metadata.optString("name", this.name);
                    this.version = metadata.optString("version", "");
                    this.description = metadata.optString("description", this.description);
                    if (root.getJSONObject("quilt_loader").has("metadata") &&
                        root.getJSONObject("quilt_loader").getJSONObject("metadata").has("icon")) {
                        this.iconEntryPath = root.getJSONObject("quilt_loader").getJSONObject("metadata").optString("icon", null);
                    }
                }
                return;
            }

            // Check for Forge (1.13+)
            ZipEntry tomlEntry = zipFile.getEntry("META-INF/mods.toml");
            if (tomlEntry != null) {
                parseToml(zipFile, tomlEntry);
                return;
            }

            // Check for Forge (1.12-)
            ZipEntry mcmodEntry = zipFile.getEntry("mcmod.info");
            if (mcmodEntry != null) {
                String content = readZipEntry(zipFile, mcmodEntry);
                if (content.trim().startsWith("[")) {
                    JSONArray arr = new JSONArray(content);
                    if (arr.length() > 0) {
                        JSONObject mod = arr.getJSONObject(0);
                        this.name = mod.optString("name", this.name);
                        this.version = mod.optString("version", "");
                        this.description = mod.optString("description", this.description);
                    }
                } else if (content.trim().startsWith("{")) {
                    JSONObject root = new JSONObject(content);
                    if (root.has("modList")) {
                        JSONArray arr = root.getJSONArray("modList");
                        if (arr.length() > 0) {
                            JSONObject mod = arr.getJSONObject(0);
                            this.name = mod.optString("name", this.name);
                            this.version = mod.optString("version", "");
                            this.description = mod.optString("description", this.description);
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e("InstalledModInfo", "Failed to load metadata for " + file.getName(), e);
        }
    }

    private void parseJson(ZipFile zipFile, ZipEntry entry, String nameKey, String verKey, String descKey) throws Exception {
        String content = readZipEntry(zipFile, entry);
        JSONObject json = new JSONObject(content);
        this.name = json.optString(nameKey, this.name);
        this.version = json.optString(verKey, "");
        this.description = json.optString(descKey, this.description);
    }

    private void parseToml(ZipFile zipFile, ZipEntry entry) throws Exception {
        String content = readZipEntry(zipFile, entry);
        // Extremely basic TOML parsing for simple key="value"
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("displayName=\"") || line.startsWith("displayName = \"")) {
                this.name = extractTomlValue(line);
            } else if (line.startsWith("version=\"") || line.startsWith("version = \"")) {
                this.version = extractTomlValue(line);
                if(this.version.equals("${file.jarVersion}")) this.version = "From Jar";
            } else if (line.startsWith("description=\"") || line.startsWith("description = \"")) {
                this.description = extractTomlValue(line);
            } else if (line.startsWith("logoFile=\"") || line.startsWith("logoFile = \"")) {
                this.iconEntryPath = extractTomlValue(line);
            }
        }
    }

    private String extractTomlValue(String line) {
        int firstQuote = line.indexOf('"');
        int lastQuote = line.lastIndexOf('"');
        if (firstQuote != -1 && lastQuote != -1 && firstQuote < lastQuote) {
            return line.substring(firstQuote + 1, lastQuote);
        }
        return line;
    }

    private String readZipEntry(ZipFile zipFile, ZipEntry entry) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = zipFile.getInputStream(entry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
