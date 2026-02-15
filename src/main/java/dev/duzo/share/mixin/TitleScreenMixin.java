package dev.duzo.share.mixin;

import dev.duzo.share.ModSync;
import dev.duzo.share.client.ReconnectData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique
    private static boolean modsync$checkedReconnect = false;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void modsync$autoReconnect(CallbackInfo ci) {
        // Only try once per game session
        if (modsync$checkedReconnect) return;
        modsync$checkedReconnect = true;

        ReconnectData.ReconnectInfo info = ReconnectData.load();
        if (info == null) return;

        // Clean up the reconnect file
        ReconnectData.delete();

        ModSync.LOGGER.info("ModSync: Auto-reconnecting to {}:{}", info.host(), info.mcPort());

        // Delay the connection slightly to let the title screen fully initialize
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            ServerAddress serverAddress = new ServerAddress(info.host(), info.mcPort());
            ServerData serverData = new ServerData(
                "ModSync Reconnect",
                info.address(),
                false
            );

            ConnectScreen.startConnecting(
                (Screen)(Object) this,
                mc,
                serverAddress,
                serverData,
                false // not a quickplay
            );
        });
    }
}