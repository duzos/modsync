package dev.duzo.share.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.duzo.share.ModSync;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiConsumer;

public class ModDownloader {

    public static class RemoteModInfo {
        public final String modId;
        public final String fileName;
        public final long fileSize;
        public final String sha256;

        public RemoteModInfo(String modId, String fileName, long fileSize, String sha256) {
            this.modId = modId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.sha256 = sha256;
        }
    }

    /**
     * Fetches the mod list from the server's HTTP endpoint.
     */
    public static List<RemoteModInfo> fetchModList(String serverHost, int httpPort) throws Exception {
        String url = "http://" + serverHost + ":" + httpPort + "/modsync/modlist";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        try (InputStream is = conn.getInputStream()) {
            String json = new String(is.readAllBytes());
            return parseModList(json);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Checks which server mods are missing locally.
     */
    public static List<RemoteModInfo> findMissingMods(List<RemoteModInfo> serverMods) {
        Path modsDir = FMLPaths.MODSDIR.get();
        Set<String> localHashes = new HashSet<>();

        // Hash all local mod JARs
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jar : stream) {
                try {
                    byte[] bytes = Files.readAllBytes(jar);
                    localHashes.add(sha256Hex(bytes));
                } catch (Exception e) {
                    ModSync.LOGGER.warn("Failed to hash local JAR: {}", jar.getFileName());
                }
            }
        } catch (Exception e) {
            ModSync.LOGGER.error("Failed to scan local mods", e);
        }

        // Also check by filename existence as a fallback
        List<RemoteModInfo> missing = new ArrayList<>();
        for (RemoteModInfo remote : serverMods) {
            Path localFile = modsDir.resolve(remote.fileName);
            boolean hashMatch = localHashes.contains(remote.sha256);
            boolean fileExists = Files.exists(localFile);

            // If we have the exact same hash somewhere, the mod is present
            if (!hashMatch && !fileExists) {
                missing.add(remote);
            }
        }

        return missing;
    }

    /**
     * Downloads a single mod JAR from the server.
     * @param progressCallback Called with (bytesDownloaded, totalBytes) for progress tracking
     */
    public static Path downloadMod(
        String serverHost, int httpPort,
        RemoteModInfo mod,
        BiConsumer<Long, Long> progressCallback
    ) throws Exception {
        String url = "http://" + serverHost + ":" + httpPort
            + "/modsync/download?file=" + mod.fileName;

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Server returned HTTP " + responseCode + " for " + mod.fileName);
        }

        // Download to a temp file first, then move to mods folder after verification
        Path modsDir = FMLPaths.MODSDIR.get();
        Path tempFile = modsDir.resolve(mod.fileName + ".modsync.tmp");
        Path targetFile = modsDir.resolve(mod.fileName);

        try (InputStream is = conn.getInputStream();
             OutputStream os = Files.newOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int bytesRead;

            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (progressCallback != null) {
                    progressCallback.accept(totalRead, mod.fileSize);
                }
            }
        } finally {
            conn.disconnect();
        }

        // Verify SHA-256
        byte[] downloadedBytes = Files.readAllBytes(tempFile);
        String actualHash = sha256Hex(downloadedBytes);

        if (!actualHash.equals(mod.sha256)) {
            Files.deleteIfExists(tempFile);
            throw new SecurityException(
                "SHA-256 mismatch for " + mod.fileName +
                "! Expected: " + mod.sha256 + ", Got: " + actualHash +
                ". File deleted for safety."
            );
        }

        // Move temp to final location
        Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        ModSync.LOGGER.info("ModSync: Downloaded and verified {}", mod.fileName);

        return targetFile;
    }

    private static List<RemoteModInfo> parseModList(String json) {
        List<RemoteModInfo> list = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray mods = root.getAsJsonArray("mods");

        for (JsonElement elem : mods) {
            JsonObject obj = elem.getAsJsonObject();
            list.add(new RemoteModInfo(
                obj.get("modId").getAsString(),
                obj.get("fileName").getAsString(),
                obj.get("fileSize").getAsLong(),
                obj.get("sha256").getAsString()
            ));
        }
        return list;
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