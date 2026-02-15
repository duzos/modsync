package dev.duzo.share.client;

import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory cache of the last server we attempted to connect to.
 * This is needed because by the time the disconnect screen shows,
 * Minecraft has already cleared the current server data.
 */
public class LastServerCache {

    @Nullable
    private static String lastHost;
    private static int lastMcPort = 25565;

    /**
     * Cache the server address before a connection attempt.
     */
    public static void cacheServer(ServerData serverData) {
        if (serverData == null || serverData.ip == null) return;
        cacheServer(serverData.ip);
    }

    /**
     * Cache the server address before a connection attempt.
     */
    public static void cacheServer(String address) {
        if (address == null || address.isEmpty()) return;

        // Handle IPv6 addresses: [ipv6]:port or [ipv6]
        if (address.startsWith("[")) {
            int closeBracket = address.indexOf(']');
            if (closeBracket == -1) {
                // Malformed, but store what we have
                lastHost = address;
                lastMcPort = 25565;
                return;
            }

            lastHost = address.substring(0, closeBracket + 1); // Include brackets for IPv6

            // Check for port after the bracket
            if (closeBracket + 1 < address.length() && address.charAt(closeBracket + 1) == ':') {
                try {
                    lastMcPort = Integer.parseInt(address.substring(closeBracket + 2));
                } catch (NumberFormatException e) {
                    lastMcPort = 25565;
                }
            } else {
                lastMcPort = 25565;
            }
        }
        // Handle IPv4 or hostname: host:port or host
        else if (address.contains(":")) {
            int lastColon = address.lastIndexOf(':');
            lastHost = address.substring(0, lastColon);
            try {
                lastMcPort = Integer.parseInt(address.substring(lastColon + 1));
            } catch (NumberFormatException e) {
                lastMcPort = 25565;
            }
        } else {
            lastHost = address;
            lastMcPort = 25565;
        }
    }

    /**
     * @return The last known server host, or null if none cached.
     */
    @Nullable
    public static String getLastHost() {
        return lastHost;
    }

    /**
     * @return The last known server Minecraft port.
     */
    public static int getLastMcPort() {
        return lastMcPort;
    }

    /**
     * @return The HTTP port (Minecraft port + 1 by convention).
     */
    public static int getLastHttpPort() {
        return lastMcPort + 1;
    }

    /**
     * @return True if we have cached server data.
     */
    public static boolean hasCachedServer() {
        return lastHost != null && !lastHost.isEmpty();
    }

    /**
     * Clear the cached server data.
     */
    public static void clear() {
        lastHost = null;
        lastMcPort = 25565;
    }
}

