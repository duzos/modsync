package dev.duzo.share.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.duzo.share.ModSync;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.*;

/**
 * Persists the server address so we can auto-reconnect after restart.
 */
public class ReconnectData {

    private static final Gson GSON = new Gson();

    private static Path getDataFile() {
        return FMLPaths.GAMEDIR.get().resolve("modsync_reconnect.json");
    }

    public static void save(String host, int mcPort, int httpPort) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("host", host);
            obj.addProperty("mcPort", mcPort);
            obj.addProperty("httpPort", httpPort);
            obj.addProperty("timestamp", System.currentTimeMillis());

            Files.writeString(getDataFile(), GSON.toJson(obj));
            ModSync.LOGGER.info("ModSync: Saved reconnect data for {}:{}", host, mcPort);
        } catch (Exception e) {
            ModSync.LOGGER.error("Failed to save reconnect data", e);
        }
    }

    public static ReconnectInfo load() {
        Path file = getDataFile();
        if (!Files.exists(file)) return null;

        try {
            String json = Files.readString(file);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);

            long timestamp = obj.get("timestamp").getAsLong();
            // Only valid for 5 minutes after saving
            if (System.currentTimeMillis() - timestamp > 5 * 60 * 1000) {
                delete();
                return null;
            }

            return new ReconnectInfo(
                obj.get("host").getAsString(),
                obj.get("mcPort").getAsInt(),
                obj.get("httpPort").getAsInt()
            );
        } catch (Exception e) {
            ModSync.LOGGER.error("Failed to load reconnect data", e);
            return null;
        }
    }

    public static void delete() {
        try {
            Files.deleteIfExists(getDataFile());
        } catch (Exception ignored) {}
    }

    public record ReconnectInfo(String host, int mcPort, int httpPort) {
        public String address() {
            return host + ":" + mcPort;
        }
    }
}