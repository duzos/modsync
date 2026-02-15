package dev.duzo.share;

import dev.duzo.share.config.ModSyncConfig;
import dev.duzo.share.server.ModHttpServer;
import dev.duzo.share.server.ServerModScanner;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ModSync.MOD_ID)
public class ModSync {

    public static final String MOD_ID = "share";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ModHttpServer httpServer;

    public ModSync() {
        // Mark this mod as compatible with any side — critical so the bootstrap
        // client mod doesn't itself cause a handshake rejection when connecting
        // to servers that don't have it, and vice versa.
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remote, isServer) -> true
                )
        );

        ModSyncConfig.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (event.getServer().isDedicatedServer()) {
            LOGGER.info("ModSync: Scanning server mods...");
            ServerModScanner.scan(FMLPaths.MODSDIR.get());

            int httpPort = ModSyncConfig.HTTP_PORT.get();
            httpServer = new ModHttpServer(httpPort);
            httpServer.start();
            LOGGER.info("ModSync: HTTP server started on port {}", httpPort);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (httpServer != null) {
            httpServer.stop();
            LOGGER.info("ModSync: HTTP server stopped");
        }
    }
}
