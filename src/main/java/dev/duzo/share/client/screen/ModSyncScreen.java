package dev.duzo.share.client.screen;

import dev.duzo.share.client.ModDownloader;
import dev.duzo.share.client.ModDownloader.RemoteModInfo;
import dev.duzo.share.client.ReconnectData;
import dev.duzo.share.client.RestartManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ModSyncScreen extends Screen {

    private final String serverHost;
    private final int mcPort;
    private final int httpPort;
    private final Screen parentScreen;

    // State machine
    private enum State { LOADING, CONSENT, DOWNLOADING, DONE, ERROR }
    private volatile State state = State.LOADING;

    private List<RemoteModInfo> missingMods;
    private String errorMessage;

    // Download progress
    private final AtomicInteger modsDownloaded = new AtomicInteger(0);
    private final AtomicInteger totalMods = new AtomicInteger(0);
    private final AtomicLong currentFileBytes = new AtomicLong(0);
    private final AtomicLong currentFileTotal = new AtomicLong(0);
    private final AtomicReference<String> currentFileName = new AtomicReference<>("");

    private Button downloadButton;
    private Button cancelButton;
    private Button restartButton;
    private Button exitButton;

    public ModSyncScreen(Screen parent, String serverHost, int mcPort, int httpPort) {
        super(Component.literal("ModSync - Mod Synchronization"));
        this.parentScreen = parent;
        this.serverHost = serverHost;
        this.mcPort = mcPort;
        this.httpPort = httpPort;
    }

    @Override
    protected void init() {
        // Cancel button — always present
        cancelButton = addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            btn -> minecraft.setScreen(parentScreen)
        ).bounds(width / 2 - 100, height - 40, 95, 20).build());

        // Download button — shown during consent
        downloadButton = addRenderableWidget(Button.builder(
            Component.literal("Download & Install"),
            btn -> startDownloading()
        ).bounds(width / 2 + 5, height - 40, 95, 20).build());
        downloadButton.visible = false;

        // Restart button — shown when done
        restartButton = addRenderableWidget(Button.builder(
            Component.literal("Restart Now"),
            btn -> doRestart()
        ).bounds(width / 2 - 102, height - 40, 100, 20).build());
        restartButton.visible = false;

        // Exit button — shown when done (fallback if auto-restart fails)
        exitButton = addRenderableWidget(Button.builder(
            Component.literal("Exit Game"),
            btn -> doExit()
        ).bounds(width / 2 + 2, height - 40, 100, 20).build());
        exitButton.visible = false;

        // Start fetching mod list in background
        CompletableFuture.runAsync(this::fetchMissingMods);
    }

    private void fetchMissingMods() {
        try {
            List<RemoteModInfo> serverMods = ModDownloader.fetchModList(serverHost, httpPort);
            missingMods = ModDownloader.findMissingMods(serverMods);

            if (missingMods.isEmpty()) {
                errorMessage = "All server mods are already installed! Try reconnecting.";
                state = State.ERROR;
            } else {
                state = State.CONSENT;
            }
        } catch (Exception e) {
            errorMessage = "Failed to fetch mod list: " + e.getMessage();
            state = State.ERROR;
        }
    }

    private void startDownloading() {
        state = State.DOWNLOADING;
        downloadButton.visible = false;
        cancelButton.visible = false;

        totalMods.set(missingMods.size());
        modsDownloaded.set(0);

        CompletableFuture.runAsync(() -> {
            try {
                for (RemoteModInfo mod : missingMods) {
                    currentFileName.set(mod.fileName);
                    currentFileBytes.set(0);
                    currentFileTotal.set(mod.fileSize);

                    ModDownloader.downloadMod(serverHost, httpPort, mod, (downloaded, total) -> {
                        currentFileBytes.set(downloaded);
                        currentFileTotal.set(total);
                    });

                    modsDownloaded.incrementAndGet();
                }
                state = State.DONE;
            } catch (Exception e) {
                errorMessage = "Download failed: " + e.getMessage();
                state = State.ERROR;
            }
        });
    }

    private void doRestart() {
        // Save reconnect info
        ReconnectData.save(serverHost, mcPort, httpPort);

        // Attempt automatic restart
        if (!RestartManager.restart()) {
            // If restart fails, show a friendlier message but keep the exit button visible
            errorMessage = "Auto-restart not available with this launcher.\n"
                + "Click 'Exit Game' and relaunch from your launcher.";
            state = State.ERROR;
        }
    }

    private void doExit() {
        // Save reconnect info so we auto-reconnect on next launch
        ReconnectData.save(serverHost, mcPort, httpPort);

        // Just exit - user will restart from their launcher
        RestartManager.exitForManualRestart();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        // Title
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);

        switch (state) {
            case LOADING -> renderLoading(graphics);
            case CONSENT -> renderConsent(graphics);
            case DOWNLOADING -> renderDownloading(graphics);
            case DONE -> renderDone(graphics);
            case ERROR -> renderError(graphics);
        }

        // Update button visibility
        downloadButton.visible = (state == State.CONSENT);
        cancelButton.visible = (state != State.DONE && state != State.DOWNLOADING);
        restartButton.visible = (state == State.DONE);
        exitButton.visible = (state == State.DONE);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderLoading(GuiGraphics g) {
        g.drawCenteredString(font, "Contacting server...", width / 2, height / 2, 0xAAAAAA);
    }

    private void renderConsent(GuiGraphics g) {
        int y = 40;
        g.drawCenteredString(font, "§eThe server requires " + missingMods.size() + " mod(s) you don't have:", width / 2, y, 0xFFFF55);
        y += 20;

        for (int i = 0; i < Math.min(missingMods.size(), 10); i++) {
            RemoteModInfo mod = missingMods.get(i);
            String sizeStr = formatBytes(mod.fileSize);
            g.drawCenteredString(font,
                "§f" + mod.fileName + " §7(" + sizeStr + ")",
                width / 2, y, 0xFFFFFF);
            y += 12;
        }

        if (missingMods.size() > 10) {
            g.drawCenteredString(font,
                "§7... and " + (missingMods.size() - 10) + " more",
                width / 2, y, 0x777777);
            y += 12;
        }

        y += 10;
        long totalSize = missingMods.stream().mapToLong(m -> m.fileSize).sum();
        g.drawCenteredString(font,
            "§fTotal download: §a" + formatBytes(totalSize),
            width / 2, y, 0xFFFFFF);

        y += 20;
        g.drawCenteredString(font,
            "§c⚠ Only download mods from servers you trust! ⚠",
            width / 2, y, 0xFF5555);
    }

    private void renderDownloading(GuiGraphics g) {
        int done = modsDownloaded.get();
        int total = totalMods.get();
        String file = currentFileName.get();
        long fileBytes = currentFileBytes.get();
        long fileTotal = currentFileTotal.get();

        g.drawCenteredString(font,
            "Downloading mod " + (done + 1) + " / " + total,
            width / 2, height / 2 - 30, 0xFFFFFF);
        g.drawCenteredString(font,
            "§7" + file,
            width / 2, height / 2 - 15, 0xAAAAAA);

        // Progress bar
        int barWidth = 200;
        int barHeight = 10;
        int barX = width / 2 - barWidth / 2;
        int barY = height / 2 + 5;

        g.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

        if (fileTotal > 0) {
            float progress = (float) fileBytes / fileTotal;
            int fillWidth = (int) (barWidth * progress);
            g.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF55FF55);
        }

        g.drawCenteredString(font,
            formatBytes(fileBytes) + " / " + formatBytes(fileTotal),
            width / 2, barY + barHeight + 5, 0xAAAAAA);
    }

    private void renderDone(GuiGraphics g) {
        g.drawCenteredString(font,
            "§a✓ All mods downloaded and verified!",
            width / 2, height / 2 - 30, 0x55FF55);
        g.drawCenteredString(font,
            "§fMinecraft needs to restart to load the new mods.",
            width / 2, height / 2 - 10, 0xFFFFFF);
        g.drawCenteredString(font,
            "§7You will auto-reconnect after restart.",
            width / 2, height / 2 + 5, 0xAAAAAA);
        g.drawCenteredString(font,
            "§8(Use 'Exit Game' if 'Restart Now' doesn't work)",
            width / 2, height / 2 + 20, 0x666666);
    }

    private void renderError(GuiGraphics g) {
        g.drawCenteredString(font, "§c" + errorMessage, width / 2, height / 2, 0xFF5555);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}