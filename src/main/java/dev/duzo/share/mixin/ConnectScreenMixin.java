package dev.duzo.share.mixin;

import dev.duzo.share.client.LastServerCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to capture the server address before a connection attempt.
 * This ensures we have the server data available even after disconnect.
 */
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {

    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void modsync$cacheServerOnConnect(Screen screen, Minecraft mc, ServerAddress address, ServerData serverData, boolean quickPlay, CallbackInfo ci) {
        // Cache from ServerData if available (has the original user-entered address)
        if (serverData != null) {
            LastServerCache.cacheServer(serverData);
        }
        // Fallback to resolved address
        else if (address != null) {
            String host = address.getHost();
            int port = address.getPort();

            // Wrap IPv6 addresses in brackets if not already
            if (host.contains(":") && !host.startsWith("[")) {
                host = "[" + host + "]";
            }

            LastServerCache.cacheServer(host + ":" + port);
        }
    }
}

