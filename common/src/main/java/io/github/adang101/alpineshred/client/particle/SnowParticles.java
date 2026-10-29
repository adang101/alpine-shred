package io.github.adang101.alpineshred.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.architectury.registry.client.particle.ParticleProviderRegistry;
import io.github.adang101.alpineshred.registry.ModParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Particle providers and classes for {@link ModParticles} (design doc section 17). Client only. */
public final class SnowParticles {
    private SnowParticles() {
    }

    public static void register() {
        ParticleProviderRegistry.register(ModParticles.SNOW_SPRAY, sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new Spray(level, x, y, z, xd, yd, zd, sprites));
        ParticleProviderRegistry.register(ModParticles.SNOW_POOF, sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new Poof(level, x, y, z, xd, yd, zd, sprites));
        ParticleProviderRegistry.register(ModParticles.SNOW_TRAIL, sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new Trail(level, x, y, z, xd, zd, sprites));
    }

    /** Snow thrown off an edge: small, falls fast, shrinks through four frames. */
    static final class Spray extends TextureSheetParticle {
        private final SpriteSet sprites;

        Spray(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
            super(level, x, y, z);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.sprites = sprites;
            this.gravity = 0.7f;
            this.friction = 0.94f;
            this.lifetime = 10 + random.nextInt(8);
            this.quadSize = 0.06f + random.nextFloat() * 0.06f;
            this.hasPhysics = true;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** A landing puff: drifts out and up, grows and fades. */
    static final class Poof extends TextureSheetParticle {
        private final SpriteSet sprites;

        Poof(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
            super(level, x, y, z);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.sprites = sprites;
            this.gravity = 0.05f;
            this.friction = 0.85f;
            this.lifetime = 14 + random.nextInt(10);
            this.quadSize = 0.12f + random.nextFloat() * 0.1f;
            this.hasPhysics = true;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            alpha = 1.0f - (float) age / lifetime;
            quadSize *= 1.03f;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /**
     * A carve mark lying flat on the snow along the direction of travel for 40 ticks. Drawn as a
     * horizontal quad (both faces) instead of a camera-facing one.
     */
    static final class Trail extends TextureSheetParticle {
        private static final float HALF_LENGTH = 0.35f;
        private static final float HALF_WIDTH = 0.07f;
        private final float dirX;
        private final float dirZ;

        Trail(ClientLevel level, double x, double y, double z, double dirX, double dirZ, SpriteSet sprites) {
            super(level, x, y, z);
            double len = Math.hypot(dirX, dirZ);
            this.dirX = len > 1.0e-6 ? (float) (dirX / len) : 0f;
            this.dirZ = len > 1.0e-6 ? (float) (dirZ / len) : 1f;
            this.xd = this.yd = this.zd = 0;
            this.gravity = 0;
            this.hasPhysics = false;
            this.lifetime = 40;
            this.alpha = 0.8f;
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            alpha = 0.8f * (1.0f - (float) age / lifetime);
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            Vec3 cam = camera.getPosition();
            float cx = (float) (Mth.lerp(partialTick, xo, x) - cam.x());
            float cy = (float) (Mth.lerp(partialTick, yo, y) - cam.y());
            float cz = (float) (Mth.lerp(partialTick, zo, z) - cam.z());
            float lx = dirX * HALF_LENGTH, lz = dirZ * HALF_LENGTH;
            float wx = -dirZ * HALF_WIDTH, wz = dirX * HALF_WIDTH;
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            int light = getLightColor(partialTick);
            float[][] corners = {
                    {cx - lx - wx, cz - lz - wz, u0, v1},
                    {cx - lx + wx, cz - lz + wz, u1, v1},
                    {cx + lx + wx, cz + lz + wz, u1, v0},
                    {cx + lx - wx, cz + lz - wz, u0, v0}};
            for (int i = 0; i < 4; i++) vertex(buffer, corners[i], cy, light);
            for (int i = 3; i >= 0; i--) vertex(buffer, corners[i], cy, light);
        }

        private void vertex(VertexConsumer buffer, float[] c, float y, int light) {
            buffer.vertex(c[0], y, c[1]).uv(c[2], c[3]).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
