package io.github.adang101.alpineshred.client.feel;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.feel.ParticleRules;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.SlideModel;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import io.github.adang101.alpineshred.park.GrindMaterial;
import io.github.adang101.alpineshred.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Client-side particles from the synced rider state (design doc section 17): snow spray off the
 * edge when carving or braking (block particles on non-snow ground), a burst on landing, and flat
 * carve trails ({@code client.carveTrails}), rail sparks while grinding metal (a burst on the catch)
 * and block dust off a box. The server never sends any.
 */
final class RiderParticles {
    private RiderParticles() {
    }

    static void update(RiderView view) {
        Level level = view.player.level();
        RandomSource random = view.player.getRandom();
        boolean snowy = view.surface != null && view.surface.category() != SurfaceProfile.Category.SCRAPE
                && view.surface.category() != SurfaceProfile.Category.BOX;
        double x = view.player.getX(), y = view.player.getY(), z = view.player.getZ();
        double travel = view.speed > 0.01 ? travelYaw(view) : view.heading;
        double fx = SlideModel.dirX(travel), fz = SlideModel.dirZ(travel);
        double rx = SlideModel.dirX(travel + 90), rz = SlideModel.dirZ(travel + 90);

        if (view.state == RideState.GRIND) {
            grindSparks(view, level, random, x, y, z, fx, fz);
        } else if (view.state == RideState.SLIDE && view.blockUnder != null && !view.blockUnder.isAir()) {
            int count = view.spray.take(ParticleRules.boxDustPerTick(view.speed));
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, view.blockUnder);
            for (int i = 0; i < count; i++) {
                FeelStats.particlesSpawned++;
                level.addParticle(dust, x - fx * 0.3 + (random.nextDouble() - 0.5) * 0.3, y + 0.05,
                        z - fz * 0.3 + (random.nextDouble() - 0.5) * 0.3, -fx * 0.05, 0.05, -fz * 0.05);
            }
        } else if (view.grounded && view.state.isGroundRiding()) {
            int count = view.spray.take(ParticleRules.sprayPerTick(view.state, view.speed, view.lean));
            if (count > 0) {
                ParticleOptions particle = snowy ? ModParticles.SNOW_SPRAY.get()
                        : view.blockUnder != null && !view.blockUnder.isAir() ? new BlockParticleOption(ParticleTypes.BLOCK, view.blockUnder) : null;
                // Carving throws snow to the outside of the turn; braking throws it ahead.
                double side = view.state == RideState.BRAKE ? 0 : view.lean > 0 ? -1 : 1;
                double ox = view.state == RideState.BRAKE ? fx : rx * side;
                double oz = view.state == RideState.BRAKE ? fz : rz * side;
                for (int i = 0; particle != null && i < count; i++) {
                    double spread = (random.nextDouble() - 0.5) * 0.6;
                    double px = x + ox * 0.35 + fx * spread + (random.nextDouble() - 0.5) * 0.2;
                    double pz = z + oz * 0.35 + fz * spread + (random.nextDouble() - 0.5) * 0.2;
                    double out = 0.08 + random.nextDouble() * 0.12 + view.speed * 0.1;
                    FeelStats.particlesSpawned++;
                    level.addParticle(particle, px, y + 0.1, pz,
                            ox * out + fx * view.speed * 0.25, 0.08 + random.nextDouble() * 0.14, oz * out + fz * view.speed * 0.25);
                }
            }
            if (snowy && AlpineConfig.CLIENT_CARVE_TRAILS.on() && ParticleRules.leavesTrail(view.state, view.speed, view.lean)) {
                double side = view.lean > 0 ? 1 : -1; // the inside edge cuts the trail
                // Direction rides in the velocity arguments; the trail particle does not move.
                FeelStats.particlesSpawned++;
                level.addParticle(ModParticles.SNOW_TRAIL.get(), x + rx * side * 0.15, y + 0.02, z + rz * side * 0.15, fx, 0, fz);
            }
        }
        if (!Double.isNaN(view.landedImpact) && snowy) {
            int count = ParticleRules.poofCount(view.landedImpact);
            for (int i = 0; i < count; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 0.05 + random.nextDouble() * 0.15;
                FeelStats.particlesSpawned++;
                level.addParticle(ModParticles.SNOW_POOF.get(), x + Math.cos(a) * 0.3, y + 0.1, z + Math.sin(a) * 0.3,
                        Math.cos(a) * r, 0.05 + random.nextDouble() * 0.1, Math.sin(a) * r);
            }
        }
    }

    /** Sparks off the contact point, thrown back along the rail; a burst on the catch. */
    private static void grindSparks(RiderView view, Level level, RandomSource random, double x, double y, double z, double fx, double fz) {
        boolean metal = view.material == GrindMaterial.METAL || view.material == GrindMaterial.COPPER;
        int count = view.spray.take(ParticleRules.sparksPerTick(metal, view.speed, view.pressing));
        if (view.caught && metal) count += ParticleRules.CATCH_SPARKS;
        boolean copper = view.material == GrindMaterial.COPPER;
        for (int i = 0; i < count; i++) {
            double back = 0.1 + random.nextDouble() * 0.15 + view.speed * 0.2;
            FeelStats.particlesSpawned++;
            // Made through the engine so the spark can be tinted: green for copper (section 17).
            Particle spark = Minecraft.getInstance().particleEngine.createParticle(ModParticles.RAIL_SPARK.get(),
                    x + (random.nextDouble() - 0.5) * 0.2, y + 0.02, z + (random.nextDouble() - 0.5) * 0.2,
                    -fx * back + (random.nextDouble() - 0.5) * 0.12, 0.06 + random.nextDouble() * 0.12,
                    -fz * back + (random.nextDouble() - 0.5) * 0.12);
            if (spark != null && copper) spark.setColor(0.55f, 1.0f, 0.7f);
        }
    }

    private static double travelYaw(RiderView view) {
        double h = view.heading;
        return view.switchStance ? h + 180 : h;
    }
}
