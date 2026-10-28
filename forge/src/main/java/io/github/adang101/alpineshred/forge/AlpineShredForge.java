package io.github.adang101.alpineshred.forge;

import dev.architectury.platform.forge.EventBuses;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.client.AlpineShredClient;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Forge (and NeoForge 1.20.1) entry point. Holds no logic beyond wiring (section 3, rule 1). */
@Mod(AlpineShred.MOD_ID)
public final class AlpineShredForge {
    public AlpineShredForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Architectury's DeferredRegister and client registries hook into this bus.
        EventBuses.registerModEventBus(AlpineShred.MOD_ID, modBus);
        AlpineShred.init();
        EnvExecutor.runInEnv(Env.CLIENT, () -> AlpineShredClient::init);
    }
}
