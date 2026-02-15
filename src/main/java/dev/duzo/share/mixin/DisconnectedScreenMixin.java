package dev.duzo.share.mixin;

import dev.duzo.share.client.LastServerCache;
import dev.duzo.share.client.screen.ModSyncScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.ModMismatchDisconnectedScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModMismatchDisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {

    @Shadow @Final private Component reason;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void modsync$addSyncButton(CallbackInfo ci) {
        // Check if this disconnect looks like a Forge mod mismatch
        String reasonStr = reason.getString().toLowerCase();
        boolean looksLikeModMismatch =
            reasonStr.contains("mod") ||
            reasonStr.contains("channel") ||
            reasonStr.contains("registry") ||
            reasonStr.contains("fml");

        // Always add the button, but make it more prominent for mod mismatches
        String buttonText = looksLikeModMismatch
            ? "§aSync Mods"
            : "Sync Mods";

        // Position: right side of the bottom row, next to the back button
        // Standard button width is 150, with 4px gap between buttons
        int buttonWidth = 98;
        int buttonY = height - 28; // Same Y as the back button
        int rightButtonX = width / 2 + 2; // Right side with small gap from center

        addRenderableWidget(Button.builder(
            Component.literal(buttonText),
            btn -> modsync$openModSync()
        ).bounds(rightButtonX, buttonY, buttonWidth, 20).build());
    }

    @Unique
    private void modsync$openModSync() {
        // Use cached server data since getCurrentServer() is null after disconnect
        if (!LastServerCache.hasCachedServer()) {
            // Fallback: we don't know which server we were connecting to
            return;
        }

        String host = LastServerCache.getLastHost();
        int mcPort = LastServerCache.getLastMcPort();
        int httpPort = LastServerCache.getLastHttpPort();

        Minecraft.getInstance().setScreen(new ModSyncScreen(this, host, mcPort, httpPort));
    }
}