package dev.duzo.share.mixin;

import dev.duzo.share.client.LastServerCache;
import dev.duzo.share.client.screen.ModSyncScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for Vanilla's DisconnectedScreen.
 * This screen is shown for registry/datapack mismatches that don't trigger
 * Forge's ModMismatchDisconnectedScreen.
 * Adds a "Sync Mods from Server" button centered below the existing buttons.
 */
@Mixin(DisconnectedScreen.class)
public abstract class VanillaDisconnectedScreenMixin extends Screen {

    @Shadow @Final private Component reason;

    protected VanillaDisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void modsync$addSyncButton(CallbackInfo ci) {
        // Only add the button if we have a cached server
        if (!LastServerCache.hasCachedServer()) {
            return;
        }

        // Check if this disconnect looks like it could be mod/registry related
        String reasonStr = reason.getString().toLowerCase();
        boolean looksLikeModIssue =
            reasonStr.contains("mod") ||
            reasonStr.contains("channel") ||
            reasonStr.contains("registry") ||
            reasonStr.contains("datapack") ||
            reasonStr.contains("fml") ||
            reasonStr.contains("missing") ||
            reasonStr.contains("mismatch");

        // Only add button if it looks like a mod issue
        if (!looksLikeModIssue) {
            return;
        }

        // Find the lowest (highest Y value) button to position our button below it
        Button lowestButton = null;
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof Button button) {
                if (lowestButton == null || button.getY() > lowestButton.getY()) {
                    lowestButton = button;
                }
            }
        }

        // Position our button centered below the lowest button, matching its width
        int buttonWidth = lowestButton != null ? lowestButton.getWidth() : 200;
        int buttonX = lowestButton != null ? lowestButton.getX() : (width / 2 - 100);
        int buttonY = lowestButton != null ? lowestButton.getY() + lowestButton.getHeight() + 4 : (height - 28);

        addRenderableWidget(Button.builder(
            Component.literal("§aSync Mods from Server"),
            btn -> modsync$openModSync()
        ).bounds(buttonX, buttonY, buttonWidth, 20).build());
    }

    @Unique
    private void modsync$openModSync() {
        String host = LastServerCache.getLastHost();
        int mcPort = LastServerCache.getLastMcPort();
        int httpPort = LastServerCache.getLastHttpPort();

        Minecraft.getInstance().setScreen(new ModSyncScreen(this, host, mcPort, httpPort));
    }
}

