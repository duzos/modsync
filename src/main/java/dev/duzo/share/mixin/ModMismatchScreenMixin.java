package dev.duzo.share.mixin;

import dev.duzo.share.client.LastServerCache;
import dev.duzo.share.client.screen.ModSyncScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
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

/**
 * Mixin for Forge's ModMismatchDisconnectedScreen.
 * This screen is shown when Forge detects mod/channel mismatches.
 * Adds a "Sync Mods from Server" button next to the existing back button.
 */
@Mixin(ModMismatchDisconnectedScreen.class)
public abstract class ModMismatchScreenMixin extends Screen {

    @Shadow @Final private Component reason;

    protected ModMismatchScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void modsync$addSyncButton(CallbackInfo ci) {
        // Find and reposition the existing "Back to Server List" button to the left
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof Button button) {
                // Check if this is the back button (at the bottom of the screen)
                if (button.getY() >= height - 40) {
                    // Move it to the left side
                    button.setX(width / 2 - 154);
                    button.setWidth(150);
                }
            }
        }

        // Check if this disconnect looks like a Forge mod mismatch
        String reasonStr = reason.getString().toLowerCase();
        boolean looksLikeModMismatch =
            reasonStr.contains("mod") ||
            reasonStr.contains("channel") ||
            reasonStr.contains("registry") ||
            reasonStr.contains("fml");

        // Always add the button, but make it more prominent for mod mismatches
        String buttonText = looksLikeModMismatch
            ? "§aSync Mods from Server"
            : "Sync Mods from Server";

        // Position: right side, next to the back button
        int buttonWidth = 150;
        int buttonY = height - 28;
        int rightButtonX = width / 2 + 4;

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