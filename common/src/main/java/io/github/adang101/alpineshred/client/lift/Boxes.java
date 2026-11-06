package io.github.adang101.alpineshred.client.lift;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Placeholder cuboids for the lift hardware (chair, sheaves, bullwheels). Every cuboid samples one
 * flat 8x8 swatch of {@link #TEXTURE}, a 64x64 sheet whose top row holds eight materials, so a part
 * picks its colour by index and no UV layout is needed.
 */
final class Boxes {
    static final ResourceLocation TEXTURE = AlpineShred.id("textures/lift/hardware.png");
    static final int STEEL = 0, DARK = 1, SEAT = 2, CUSHION = 3, BAR = 4, RUBBER = 5, ACCENT = 6, FRAME = 7;

    private static final float SHEET = 64f;
    private static final float SWATCH = 8f;

    private Boxes() {
    }

    /** A cuboid from (x0, y0, z0) to (x1, y1, z1) in the pose's space, in the material {@code swatch}. */
    static void box(PoseStack pose, VertexConsumer vc, int swatch, float x0, float y0, float z0, float x1, float y1, float z1,
                    int light, int overlay) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        // One pixel inside the swatch, so filtering never reaches a neighbour.
        float u0 = (swatch * SWATCH + 1) / SHEET, u1 = (swatch * SWATCH + SWATCH - 1) / SHEET;
        float v0 = 1 / SHEET, v1 = (SWATCH - 1) / SHEET;
        // -y, +y
        quad(vc, m, n, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v0, u1, v1, light, overlay);
        quad(vc, m, n, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v0, u1, v1, light, overlay);
        // -z, +z
        quad(vc, m, n, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u0, v0, u1, v1, light, overlay);
        quad(vc, m, n, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0, v0, u1, v1, light, overlay);
        // -x, +x
        quad(vc, m, n, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u0, v0, u1, v1, light, overlay);
        quad(vc, m, n, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u0, v0, u1, v1, light, overlay);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, int light, int overlay) {
        vertex(vc, m, n, nx, ny, nz, ax, ay, az, u0, v1, light, overlay);
        vertex(vc, m, n, nx, ny, nz, bx, by, bz, u1, v1, light, overlay);
        vertex(vc, m, n, nx, ny, nz, cx, cy, cz, u1, v0, light, overlay);
        vertex(vc, m, n, nx, ny, nz, dx, dy, dz, u0, v0, light, overlay);
    }

    static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz, float x, float y, float z,
                       float u, float v, int light, int overlay) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
