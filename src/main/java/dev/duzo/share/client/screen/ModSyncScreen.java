package dev.duzo.share.client.screen;

import dev.duzo.share.ModSync;
import dev.duzo.share.client.ModDownloader;
import dev.duzo.share.client.ModDownloader.RemoteModInfo;
import dev.duzo.share.client.ReconnectData;
import dev.duzo.share.client.RestartManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ModSyncScreen extends Screen {

    private final String serverHost;
    private final int mcPort;
    private int httpPort; // Not final - may be discovered
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
    private Button continueButton;

    // Track download state
    private volatile boolean downloadError = false;
    private volatile int currentModIndex = 0;
    private volatile String failedModName = "";

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

        // Continue button — shown when a download fails, to skip and continue
        continueButton = addRenderableWidget(Button.builder(
            Component.literal("Skip & Continue"),
            btn -> continueDownloading()
        ).bounds(width / 2 + 2, height - 40, 100, 20).build());
        continueButton.visible = false;

        // Start fetching mod list in background (with port discovery)
        CompletableFuture.runAsync(this::discoverPortAndFetchMods);
    }

    private void discoverPortAndFetchMods() {
        ModSync.LOGGER.info("ModSync: Starting port discovery for {}:{}", serverHost, mcPort);

        // Try the initially provided port first
        ModSync.LOGGER.info("ModSync: Trying initial port {}", httpPort);
        if (tryFetchModList(httpPort)) {
            ModSync.LOGGER.info("ModSync: Successfully connected on port {}", httpPort);
            return;
        }

        // Scan a range of ports around the MC port (common configurations)
        // Try mcPort+1 through mcPort+100 to cover custom offsets
        ModSync.LOGGER.info("ModSync: Initial port failed, scanning range {}+1 to {}+100", mcPort, mcPort);
        for (int offset = 1; offset <= 100; offset++) {
            int port = mcPort + offset;
            if (port != httpPort && port <= 65535 && tryFetchModList(port)) {
                ModSync.LOGGER.info("ModSync: Found server on port {} (offset +{})", port, offset);
                this.httpPort = port;
                return;
            }
        }

        // Try some common standalone HTTP ports
        ModSync.LOGGER.info("ModSync: Range scan failed, trying common HTTP ports");
        int[] commonPorts = { 8080, 8443, 80, 443 };
        for (int port : commonPorts) {
            if (port != httpPort && tryFetchModList(port)) {
                ModSync.LOGGER.info("ModSync: Found server on common port {}", port);
                this.httpPort = port;
                return;
            }
        }

        // All ports failed
        ModSync.LOGGER.error("ModSync: Failed to find ModSync server on any port");
        errorMessage = "Failed to connect to ModSync server.\nEnsure the server has ModSync installed and the HTTP port is accessible.";
        state = State.ERROR;
    }

    private boolean tryFetchModList(int port) {
        return tryFetchModList(port, 500); // 500ms timeout for discovery
    }

    private boolean tryFetchModList(int port, int timeoutMs) {
        try {
            List<RemoteModInfo> serverMods = ModDownloader.fetchModList(serverHost, port, timeoutMs);
            ModSync.LOGGER.info("ModSync: Port {} responded with {} mods", port, serverMods.size());

            missingMods = ModDownloader.findMissingMods(serverMods);
            ModSync.LOGGER.info("ModSync: {} mods are missing locally", missingMods.size());

            if (missingMods.isEmpty()) {
                ModSync.LOGGER.info("ModSync: All mods already installed");
                errorMessage = "All server mods are already installed! Try reconnecting.";
                state = State.ERROR;
            } else {
                for (RemoteModInfo mod : missingMods) {
                    ModSync.LOGGER.info("ModSync: Missing mod: {} ({} bytes)", mod.fileName, mod.fileSize);
                }
                state = State.CONSENT;
            }
            return true;
        } catch (Exception e) {
            // This port didn't work, try next
            return false;
        }
    }

    private void startDownloading() {
        ModSync.LOGGER.info("ModSync: Starting download of {} mods", missingMods.size());
        state = State.DOWNLOADING;
        downloadButton.visible = false;
        cancelButton.visible = false;
        downloadError = false;
        currentModIndex = 0;

        totalMods.set(missingMods.size());
        modsDownloaded.set(0);

        CompletableFuture.runAsync(this::downloadNextMod);
    }

    private void downloadNextMod() {
        if (currentModIndex >= missingMods.size()) {
            // All done
            ModSync.LOGGER.info("ModSync: All downloads complete!");
            state = State.DONE;
            return;
        }

        RemoteModInfo mod = missingMods.get(currentModIndex);

        try {
            ModSync.LOGGER.info("ModSync: Downloading {} ({} bytes)", mod.fileName, mod.fileSize);
            currentFileName.set(mod.fileName);
            currentFileBytes.set(0);
            currentFileTotal.set(mod.fileSize);

            ModDownloader.downloadMod(serverHost, httpPort, mod, (downloaded, total) -> {
                currentFileBytes.set(downloaded);
                currentFileTotal.set(total);
            });

            modsDownloaded.incrementAndGet();
            ModSync.LOGGER.info("ModSync: Completed download of {}", mod.fileName);

            // Move to next mod
            currentModIndex++;
            downloadNextMod();

        } catch (Exception e) {
            ModSync.LOGGER.error("ModSync: Failed to download {}: {}", mod.fileName, e.getMessage());
            downloadError = true;
            failedModName = mod.fileName;
            errorMessage = "Failed to download: " + mod.fileName + "\n" + e.getMessage();
            state = State.ERROR;
            // Don't continue automatically - let user decide via Continue button
        }
    }

    private void continueDownloading() {
        ModSync.LOGGER.info("ModSync: User chose to skip {} and continue", failedModName);
        downloadError = false;
        currentModIndex++; // Skip the failed mod

        if (currentModIndex >= missingMods.size()) {
            // No more mods to try
            if (modsDownloaded.get() > 0) {
                ModSync.LOGGER.info("ModSync: Finished with {} mods downloaded (some skipped)", modsDownloaded.get());
                state = State.DONE;
            } else {
                errorMessage = "All downloads failed. Cannot continue.";
                state = State.ERROR;
            }
        } else {
            state = State.DOWNLOADING;
            CompletableFuture.runAsync(this::downloadNextMod);
        }
    }

    private void doRestart() {
        ModSync.LOGGER.info("ModSync: User requested restart");
        // Save reconnect info
        ReconnectData.save(serverHost, mcPort, httpPort);
        ModSync.LOGGER.info("ModSync: Saved reconnect data for {}:{}", serverHost, mcPort);

        // Attempt automatic restart
        if (!RestartManager.restart()) {
            ModSync.LOGGER.warn("ModSync: Auto-restart failed, user must restart manually");
            // If restart fails, show a friendlier message but keep the exit button visible
            errorMessage = "Auto-restart not available with this launcher.\n"
                + "Click 'Exit Game' and relaunch from your launcher.";
            state = State.ERROR;
        }
    }

    private void doExit() {
        ModSync.LOGGER.info("ModSync: User requested exit for manual restart");
        // Save reconnect info so we auto-reconnect on next launch
        ReconnectData.save(serverHost, mcPort, httpPort);
        ModSync.LOGGER.info("ModSync: Saved reconnect data for {}:{}", serverHost, mcPort);

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

        // Update button visibility and positioning
        downloadButton.visible = (state == State.CONSENT);
        restartButton.visible = (state == State.DONE);
        exitButton.visible = (state == State.DONE);

        // Continue button: shown only on download errors (not other errors)
        continueButton.visible = (state == State.ERROR && downloadError);

        // Cancel button: visible except when done or downloading
        // When in error state without continue option, make it wider and centered
        cancelButton.visible = (state != State.DONE && state != State.DOWNLOADING);
        if (state == State.ERROR && !downloadError) {
            // Non-download error: wide centered cancel button
            cancelButton.setX(width / 2 - 100);
            cancelButton.setWidth(200);
        } else if (state == State.ERROR && downloadError) {
            // Download error: cancel on left, continue on right
            cancelButton.setX(width / 2 - 102);
            cancelButton.setWidth(100);
        } else {
            cancelButton.setX(width / 2 - 100);
            cancelButton.setWidth(95);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderLoading(GuiGraphics g) {
        g.drawCenteredString(font, "Contacting server...", width / 2, height / 2, 0xAAAAAA);
    }

    private void renderConsent(GuiGraphics g) {
        int y = 40;
        g.drawCenteredString(font, "§eThe server requires " + missingMods.size() + " mod(s) you don't have:", width / 2, y, 0xFFFF55);
        y += 20;

        // Calculate how many mods we can show before hitting the buttons
        // Leave space for: mod list, "and X more", total size, warning, and buttons
        int buttonAreaY = height - 70; // Buttons at height-40, plus some padding
        int reservedHeight = 50; // Space for total size + warning text
        int availableHeight = buttonAreaY - y - reservedHeight;
        int lineHeight = 12;
        int maxModsToShow = Math.max(1, availableHeight / lineHeight);

        int modsToShow = Math.min(missingMods.size(), maxModsToShow);

        for (int i = 0; i < modsToShow; i++) {
            RemoteModInfo mod = missingMods.get(i);
            String sizeStr = formatBytes(mod.fileSize);
            g.drawCenteredString(font,
                "§f" + mod.fileName + " §7(" + sizeStr + ")",
                width / 2, y, 0xFFFFFF);
            y += lineHeight;
        }

        if (missingMods.size() > modsToShow) {
            g.drawCenteredString(font,
                "§7... and " + (missingMods.size() - modsToShow) + " more",
                width / 2, y, 0x777777);
            y += lineHeight;
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
        // Split error message into lines and wrap long lines
        int maxWidth = width - 40; // Leave 20px padding on each side
        int y = height / 2 - 30;
        
        for (String line : errorMessage.split("\n")) {
            // Wrap long lines
            List<String> wrappedLines = wrapText(line, maxWidth);
            for (String wrappedLine : wrappedLines) {
                g.drawCenteredString(font, "§c" + wrappedLine, width / 2, y, 0xFF5555);
                y += 12;
            }
        }
    }
    
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : text.split(" ")) {
            String testLine = currentLine.length() > 0 
                ? currentLine + " " + word 
                : word;
            
            if (font.width(testLine) > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            }
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}