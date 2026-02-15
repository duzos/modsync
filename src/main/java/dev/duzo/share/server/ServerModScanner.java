package dev.duzo.share.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.duzo.share.ModSync;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ServerModScanner {

    public static class ModInfo {
        public final String modId;
        public final String fileName;
        public final long fileSize;
        public final String sha256;
        public final Path filePath;

        public ModInfo(String modId, String fileName, long fileSize, String sha256, Path filePath) {
            this.modId = modId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.sha256 = sha256;
            this.filePath = filePath;
        }
    }

    // Key is fileName (not modId) to ensure each JAR file is tracked exactly once
    private static final Map<String, ModInfo> MODS = new LinkedHashMap<>();
    private static String cachedModListJson = "{}";

    public static Map<String, ModInfo> getMods() {
        return Collections.unmodifiableMap(MODS);
    }

    public static String getModListJson() {
        return cachedModListJson;
    }

    public static ModInfo getModByFileName(String fileName) {
        return MODS.get(fileName);
    }

    public static void scan(Path modsDir) {
        MODS.clear();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jarPath : stream) {
                try {
                    scanJar(jarPath);
                } catch (Exception e) {
                    ModSync.LOGGER.warn("Failed to scan JAR: {}", jarPath.getFileName(), e);
                }
            }
        } catch (Exception e) {
            ModSync.LOGGER.error("Failed to scan mods directory", e);
        }

        // Build cached JSON
        cachedModListJson = buildModListJson();
        ModSync.LOGGER.info("ModSync: Found {} syncable mods", MODS.size());

        // Log all registered filenames for debugging
        for (ModInfo mod : MODS.values()) {
            ModSync.LOGGER.info("ModSync: Registered file '{}' (mod: {})", mod.fileName, mod.modId);
        }
    }

    private static void scanJar(Path jarPath) throws Exception {
        String fileName = jarPath.getFileName().toString();

        // Skip ourselves
        if (fileName.toLowerCase().contains("modsync")) return;

        // Skip if already registered (shouldn't happen, but just in case)
        if (MODS.containsKey(fileName)) return;

        // Compute SHA-256
        byte[] fileBytes = Files.readAllBytes(jarPath);
        String sha256 = sha256Hex(fileBytes);
        long fileSize = fileBytes.length;

        // Extract first mod ID from mods.toml for display purposes
        List<String> modIds = extractModIds(jarPath);
        String primaryModId = modIds.isEmpty() ? fileName : modIds.get(0);

        // Register by filename (not modId) to ensure each JAR is tracked exactly once
        MODS.put(fileName, new ModInfo(primaryModId, fileName, fileSize, sha256, jarPath));
        ModSync.LOGGER.debug("ModSync: Registered '{}' (primary mod: {})", fileName, primaryModId);
    }

    private static List<String> extractModIds(Path jarPath) throws Exception {
        List<String> ids = new ArrayList<>();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry("META-INF/mods.toml");
            if (entry == null) return ids;

            try (InputStream is = jar.getInputStream(entry)) {
                String content = new String(is.readAllBytes());
                // Simple TOML parsing — extract modId values
                // In production you'd use a proper TOML parser
                for (String line : content.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("modId")) {
                        int eqIdx = line.indexOf('=');
                        if (eqIdx != -1) {
                            String value = line.substring(eqIdx + 1).trim();
                            // Remove quotes
                            value = value.replace("\"", "").replace("'", "").trim();
                            if (!value.isEmpty()) {
                                ids.add(value);
                            }
                        }
                    }
                }
            }
        }

        return ids;
    }

    private static String buildModListJson() {
        JsonObject root = new JsonObject();
        JsonArray modsArray = new JsonArray();

        // MODS is already keyed by filename, so no duplicates
        for (ModInfo mod : MODS.values()) {

            JsonObject obj = new JsonObject();
            obj.addProperty("modId", mod.modId);
            obj.addProperty("fileName", mod.fileName);
            obj.addProperty("fileSize", mod.fileSize);
            obj.addProperty("sha256", mod.sha256);
            modsArray.add(obj);
        }

        root.add("mods", modsArray);
        return root.toString();
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}