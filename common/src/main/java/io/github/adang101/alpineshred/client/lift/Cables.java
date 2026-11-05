package io.github.adang101.alpineshred.client.lift;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.lift.LiftPath;
import io.github.adang101.alpineshred.park.V3;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The cable between two nodes of a line, drawn as textured cross-quads (design doc section 16): every
 * piece of {@link #SEGMENT} blocks is two quads, one across the other, {@link #WIDTH} wide, so at most
 * two quads a block. The loaded side and the return side are both drawn, along the exact spans that
 * {@link LiftPath} builds the loop from. The texture scrolls with the line's travel, so the cable looks
 * like it runs. Light is taken once per span, at its midpoint.
 */
final class Cables {
    static final ResourceLocation TEXTURE = AlpineShred.id("textures/lift/cable.png");
    static final double WIDTH = 0.12;
    static final double SEGMENT = 1.0;
    /** Texture repeats per block of cable. */
    private static final double REPEATS_PER_BLOCK = 0.5;

    private Cables() {
    }

    /**
     * Draws span {@code k}, between node {@code k} and node {@code k + 1}, both sides. The pose is the block
     * entity's (translated to {@code origin}); {@code travel} is the line's travel between ticks.
     */
    static void drawSpan(PoseStack pose, MultiBufferSource buffers, Level level, BlockPos origin, LiftPath path, int k, double travel) {
        List<V3> nodes = path.nodes();
        int last = nodes.size() - 1;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // Loaded cable runs from node k + 1 up to node k, the return cable the other way.
        side(pose, vc, level, origin, LiftPath.anchor(nodes, k + 1, k + 1 == last, true), LiftPath.anchor(nodes, k, k == 0, true), travel);
        side(pose, vc, level, origin, LiftPath.anchor(nodes, k, k == 0, false), LiftPath.anchor(nodes, k + 1, k + 1 == last, false), travel);
    }

    /** One side of one span, from {@code a} to {@code b} in the direction the cable moves. */
    private static void side(PoseStack pose, VertexConsumer vc, Level level, BlockPos origin, V3 a, V3 b, double travel) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int pieces = Math.max(1, (int) Math.ceil(length / SEGMENT));
        int light = LevelRenderer.getLightColor(level, BlockPos.containing((a.x() + b.x()) / 2, (a.y() + b.y()) / 2, (a.z() + b.z()) / 2));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        V3 from = a;
        for (int i = 1; i <= pieces; i++) {
            double t = i / (double) pieces;
            V3 to = LiftPath.spanPoint(a, b, t);
            // The pattern is fixed to the moving cable: it sits where it was, plus the travel.
            float v0 = (float) (((i - 1) / (double) pieces * length - travel) * REPEATS_PER_BLOCK);
            float v1 = (float) ((t * length - travel) * REPEATS_PER_BLOCK);
            piece(vc, m, n, origin, from, to, v0, v1, light);
            from = to;
        }
    }

    private static void piece(VertexConsumer vc, Matrix4f m, Matrix3f n, BlockPos origin, V3 p0, V3 p1, float v0, float v1, int light) {
        double dx = p1.x() - p0.x(), dy = p1.y() - p0.y(), dz = p1.z() - p0.z();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) return;
        dx /= len;
        dy /= len;
        dz /= len;
        // Across the cable, level; and the other way, perpendicular to both.
        double flat = Math.sqrt(dx * dx + dz * dz);
        double sx = flat < 1.0e-6 ? 1 : dz / flat, sz = flat < 1.0e-6 ? 0 : -dx / flat;
        double ux = dy * sz, uy = dz * sx - dx * sz, uz = -dy * sx;
        float h = (float) (WIDTH / 2);
        float ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        float ax = (float) p0.x() - ox, ay = (float) p0.y() - oy, az = (float) p0.z() - oz;
        float bx = (float) p1.x() - ox, by = (float) p1.y() - oy, bz = (float) p1.z() - oz;
        strip(vc, m, n, ax, ay, az, bx, by, bz, (float) sx * h, 0, (float) sz * h, (float) ux, (float) uy, (float) uz, v0, v1, light);
        strip(vc, m, n, ax, ay, az, bx, by, bz, (float) ux * h, (float) uy * h, (float) uz * h, (float) sx, 0, (float) sz, v0, v1, light);
    }

    /** One quad: the segment from (a) to (b), widened by +-(w) and facing along (nx, ny, nz). */
    private static void strip(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float bx, float by, float bz,
                              float wx, float wy, float wz, float nx, float ny, float nz, float v0, float v1, int light) {
        int overlay = OverlayTexture.NO_OVERLAY;
        Boxes.vertex(vc, m, n, nx, ny, nz, ax - wx, ay - wy, az - wz, 0, v0, light, overlay);
        Boxes.vertex(vc, m, n, nx, ny, nz, ax + wx, ay + wy, az + wz, 1, v0, light, overlay);
        Boxes.vertex(vc, m, n, nx, ny, nz, bx + wx, by + wy, bz + wz, 1, v1, light, overlay);
        Boxes.vertex(vc, m, n, nx, ny, nz, bx - wx, by - wy, bz - wz, 0, v1, light, overlay);
    }
}
