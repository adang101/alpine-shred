package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;

/**
 * Particle types (design doc section 17). Registered on both sides; the providers that draw them
 * are client-only ({@code client.particle}). The server never sends these: every client spawns
 * them from the synced rider state.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(AlpineShred.MOD_ID, Registries.PARTICLE_TYPE);

    /** Carving and braking: snow thrown off the edge. */
    public static final RegistrySupplier<SimpleParticleType> SNOW_SPRAY = simple("snow_spray");
    /** Landing: a burst at the feet. */
    public static final RegistrySupplier<SimpleParticleType> SNOW_POOF = simple("snow_poof");
    /** Carve trails: flat marks on the snow along the edge, 40 ticks. */
    public static final RegistrySupplier<SimpleParticleType> SNOW_TRAIL = simple("snow_trail");

    private ModParticles() {
    }

    /** The constructor is protected in 1.20.1; an empty subclass reaches it on both loaders. */
    private static RegistrySupplier<SimpleParticleType> simple(String id) {
        return PARTICLES.register(id, () -> new SimpleParticleType(false) {
        });
    }

    public static void register() {
        PARTICLES.register();
    }
}
