package dev.duzo.share.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class ModSyncConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // Server config
    public static final ForgeConfigSpec.IntValue HTTP_PORT;
    public static final ForgeConfigSpec.ConfigValue<String> SERVER_SECRET;

    // These would be in a client config in practice, but keeping it simple
    public static final ForgeConfigSpec.BooleanValue AUTO_DOWNLOAD;
    public static final ForgeConfigSpec.BooleanValue AUTO_RESTART;

    static {
        BUILDER.push("server");
        HTTP_PORT = BUILDER
            .comment("HTTP port for serving mod files. Default: Minecraft port + 1")
            .defineInRange("httpPort", 25566, 1024, 65535);
        SERVER_SECRET = BUILDER
            .comment("Shared secret for mod downloads (basic auth). Leave empty to disable.")
            .define("serverSecret", "");
        BUILDER.pop();

        BUILDER.push("client");
        AUTO_DOWNLOAD = BUILDER
            .comment("Automatically start downloading when mods are missing (still shows consent)")
            .define("autoDownload", false);
        AUTO_RESTART = BUILDER
            .comment("Automatically restart after all mods are downloaded")
            .define("autoRestart", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}