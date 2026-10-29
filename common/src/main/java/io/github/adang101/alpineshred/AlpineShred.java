package io.github.adang101.alpineshred;

import dev.architectury.platform.Platform;
import io.github.adang101.alpineshred.command.AlpineCommands;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.data.AlpineDataLoaders;
import io.github.adang101.alpineshred.gear.GearProfiles;
import io.github.adang101.alpineshred.movement.RideServer;
import io.github.adang101.alpineshred.movement.StrapController;
import io.github.adang101.alpineshred.network.ModNetwork;
import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModItems;
import io.github.adang101.alpineshred.registry.ModParticles;
import io.github.adang101.alpineshred.registry.ModRecipes;
import io.github.adang101.alpineshred.registry.ModSounds;
import io.github.adang101.alpineshred.registry.ModTabs;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared initialization, called once from each loader's entry point (design doc section 4).
 * Everything here is safe on a dedicated server; client setup lives in
 * {@link io.github.adang101.alpineshred.client.AlpineShredClient}.
 */
public final class AlpineShred {
    public static final String MOD_ID = "alpineshred";
    public static final Logger LOGGER = LoggerFactory.getLogger("Alpine Shred");

    private AlpineShred() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static void init() {
        AlpineConfig.loadCommon(Platform.getConfigFolder(), LOGGER::warn);

        ModBlocks.register();
        ModItems.register();
        ModTabs.register();
        ModRecipes.register();
        ModSounds.register();
        ModParticles.register();

        AlpineDataLoaders.register();
        ModNetwork.registerCommon();
        GearProfiles.register();
        StrapController.register();
        RideServer.register();
        AlpineCommands.register();
        io.github.adang101.alpineshred.smoke.ServerSmoke.register();

        LOGGER.info("Alpine Shred initialized on {}", Platform.isForge() ? "Forge" : "Fabric");
    }
}
