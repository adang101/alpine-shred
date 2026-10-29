package io.github.adang101.alpineshred.client.feel;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.feel.ParticleRules;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.SlideModel;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import io.github.adang101.alpineshred.registry.ModParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Client-side particles from the synced rider state (design doc section 17): snow spray off the
 * edge when carving or braking (block particles on non-snow ground), a burst on landing, and flat
 * carve trails ({@code client.carveTrails}). The server never sends any.
 */
final class RiderParticles {
    private RiderParticles() {
    }

    static void update(RiderView view) {
        Level level = view.player.level();
        RandomSource random = view.player.getRandom();
        boolean snowy = view.surface != null && view.surface.category() != SurfaceProfile.Category.SCRAPE;
        double x = view.player.getX(), y = view.player.getY(), z = view.player.getZ();
        double travel = view.speed > 0.01 ? travelYaw(view) : view.heading;
        double fx = SlideModel.dirX(travel), fz = SlideModel.dirZ(travel);
        double rx = SlideModel.dirX(travel + 90), rz = SlideModel.dirZ(travel + 90);

        if (view.grounded && view.state.isGroundRiding()) {
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
                    level.addParticle(particle, px, y + 0.1, pz,
                            ox * out + fx * view.speed * 0.25, 0.08 + random.nextDouble() * 0.14, oz * out + fz * view.speed * 0.25);
                }
            }
            if (snowy && AlpineConfig.CLIENT_CARVE_TRAILS.on() && ParticleRules.leavesTrail(view.state, view.speed, view.lean)) {
                double side = view.lean > 0 ? 1 : -1; // the inside edge cuts the trail
                // Direction rides in the velocity arguments; the trail particle does not move.
                level.addParticle(ModParticles.SNOW_TRAIL.get(), x + rx * side * 0.15, y + 0.02, z + rz * side * 0.15, fx, 0, fz);
            }
        }
        if (!Double.isNaN(view.landedImpact) && snowy) {
            int count = ParticleRules.poofCount(view.landedImpact);
            for (int i = 0; i < count; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = 0.05 + random.nextDouble() * 0.15;
                level.addParticle(ModParticles.SNOW_POOF.get(), x + Math.cos(a) * 0.3, y + 0.1, z + Math.sin(a) * 0.3,
                        Math.cos(a) * r, 0.05 + random.nextDouble() * 0.1, Math.sin(a) * r);
            }
        }
    }

    private static double travelYaw(RiderView view) {
        double h = view.heading;
        return view.switchStance ? h + 180 : h;
    }
}
