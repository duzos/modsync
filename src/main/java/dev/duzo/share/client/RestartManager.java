package dev.duzo.share.client;

import dev.duzo.share.ModSync;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles restarting the Minecraft client process.
 * This is inherently launcher-dependent and may not work on all setups.
 */
public class RestartManager {

    /**
     * Attempts to restart the game.
     * Due to modern launcher complexity (Modrinth, Prism, CurseForge, etc.),
     * auto-restart rarely works. We'll try our best but the user may need to restart manually.
     *
     * Returns false if restart could not be initiated (user should restart manually).
     */
    public static boolean restart() {
        ModSync.LOGGER.info("ModSync: Initiating restart...");

        // First, try the standard restart approach
        List<String> command = buildRestartCommand();

        if (command != null && tryLaunch(command)) {
            ModSync.LOGGER.info("ModSync: Restart process launched successfully");
            exitGame();
            return true;
        }

        // If standard approach failed, just exit and tell user to restart manually
        // The reconnect data is already saved, so they'll auto-rejoin
        ModSync.LOGGER.warn("ModSync: Auto-restart not available for this launcher");
        return false;
    }

    /**
     * Simply exits the game. Used when auto-restart isn't possible.
     * The user will need to relaunch from their launcher.
     */
    public static void exitForManualRestart() {
        ModSync.LOGGER.info("ModSync: Exiting for manual restart");
        exitGame();
    }

    private static boolean tryLaunch(List<String> command) {
        try {
            ModSync.LOGGER.info("ModSync: Attempting restart with command: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            pb.directory(new File(System.getProperty("user.dir")));

            Process process = pb.start();

            // Give it a moment to see if it immediately fails
            Thread.sleep(500);

            // Check if process is still alive (good sign)
            if (process.isAlive()) {
                return true;
            }

            // Process exited immediately - probably failed
            int exitCode = process.exitValue();
            ModSync.LOGGER.warn("ModSync: Restart process exited immediately with code {}", exitCode);
            return false;

        } catch (Exception e) {
            ModSync.LOGGER.error("ModSync: Failed to launch restart process", e);
            return false;
        }
    }

    private static void exitGame() {
        // Give logs time to flush
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        System.exit(0);
    }

    private static List<String> buildRestartCommand() {
        try {
            // Get the Java executable
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                javaBin += ".exe";
            }

            File javaBinFile = new File(javaBin);
            if (!javaBinFile.exists()) {
                ModSync.LOGGER.error("Java binary not found at: {}", javaBin);
                return null;
            }

            // Get JVM arguments (memory, GC settings, etc.)
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            List<String> jvmArgs = new ArrayList<>(runtimeBean.getInputArguments());

            // Filter out problematic JVM args that might cause issues on restart
            jvmArgs.removeIf(arg ->
                arg.startsWith("-agentlib:") ||  // Debugger
                arg.startsWith("-javaagent:") || // Agents that might not exist
                arg.contains("attach") ||
                arg.startsWith("-Xrunjdwp")      // Remote debugging
            );

            // Get the classpath
            String classpath = System.getProperty("java.class.path");
            if (classpath == null || classpath.isEmpty()) {
                ModSync.LOGGER.error("Classpath is empty");
                return null;
            }

            // Get the main class and its arguments
            String sunCommand = System.getProperty("sun.java.command");
            if (sunCommand == null || sunCommand.isEmpty()) {
                ModSync.LOGGER.error("Cannot determine main class (sun.java.command is null)");
                return null;
            }

            // Parse sun.java.command carefully - the main class is the first token
            // but program arguments might contain spaces (quoted)
            String mainClass;
            List<String> programArgs = new ArrayList<>();

            int firstSpace = sunCommand.indexOf(' ');
            if (firstSpace == -1) {
                mainClass = sunCommand;
            } else {
                mainClass = sunCommand.substring(0, firstSpace);
                String argsStr = sunCommand.substring(firstSpace + 1).trim();
                if (!argsStr.isEmpty()) {
                    // Simple split - this may not handle all quoting correctly
                    // but should work for most Minecraft launches
                    for (String arg : argsStr.split(" ")) {
                        if (!arg.isEmpty()) {
                            programArgs.add(arg);
                        }
                    }
                }
            }

            // Build the full command
            List<String> command = new ArrayList<>();
            command.add(javaBin);
            command.addAll(jvmArgs);
            command.add("-cp");
            command.add(classpath);
            command.add(mainClass);
            command.addAll(programArgs);

            return command;

        } catch (Exception e) {
            ModSync.LOGGER.error("Failed to build restart command", e);
            return null;
        }
    }
}