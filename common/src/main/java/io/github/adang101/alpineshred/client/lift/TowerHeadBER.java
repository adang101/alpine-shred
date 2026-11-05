package io.github.adang101.alpineshred.client.lift;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.adang101.alpineshred.lift.LiftPath;
import io.github.adang101.alpineshred.lift.LiftTowerHeadBlockEntity;
import io.github.adang101.alpineshred.park.V3;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Draws a tower head's share of a lift (design doc section 16): the cable from this head to its next
 * node, both sides, and the two sheaves the cable runs over, which turn with the line's travel. The
 * cable of the span between the drive and the first head belongs to the drive's renderer. The head
 * has no line id on the client, so the line is found by position in {@link ClientLiftLines}.
 *
 * <p>Cable spans reach far from their block, so the renderer draws off screen and from 256 blocks.
 */
public final class TowerHeadBER implements BlockEntityRenderer<LiftTowerHeadBlockEntity> {
    private static final float SHEAVE_RADIUS = 0.3f;

    public TowerHeadBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LiftTowerHeadBlockEntity head, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockPos pos = head.getBlockPos();
        ClientLine line = ClientLiftLines.lineAt(pos);
        if (line == null || head.getLevel() == null) return;
        int index = line.nodes().indexOf(pos);
        List<V3> nodes = line.path().nodes();
        if (index < 1 || index > nodes.size() - 2) return;
        double travel = line.travelAt(partialTick);

        long started = LiftRenderStats.begin();
        Cables.drawSpan(pose, buffers, head.getLevel(), pos, line.path(), index, travel);
        LiftRenderStats.end(LiftRenderStats.Part.CABLE, started);

        started = LiftRenderStats.begin();
        // The hardware hangs above the block, which may be dark inside.
        light = LevelRenderer.getLightColor(head.getLevel(), pos.above(3));
        V3 prev = nodes.get(index - 1), next = nodes.get(index + 1);
        double dx = next.x() - prev.x(), dz = next.z() - prev.z();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(Boxes.TEXTURE));
        // The loaded cable runs over the left sheave toward the drive, the return cable over the right one away from it.
        sheave(pose, vc, dx, dz, LiftPath.HEAD_OFFSET, travel / SHEAVE_RADIUS, light, overlay);
        sheave(pose, vc, dx, dz, -LiftPath.HEAD_OFFSET, -travel / SHEAVE_RADIUS, light, overlay);
        LiftRenderStats.end(LiftRenderStats.Part.WHEEL, started);
    }

    /**
     * One sheave, a hub and two crossed bars, under the cable {@code offset} blocks to the left of the head
     * (the line runs along (dx, dz)), turned {@code angle} radians about the axis across the line.
     */
    private static void sheave(PoseStack pose, VertexConsumer vc, double dx, double dz, double offset, double angle, int light, int overlay) {
        double len = Math.sqrt(dx * dx + dz * dz);
        double lx = -dz / len, lz = dx / len;
        float r = SHEAVE_RADIUS;
        pose.pushPose();
        pose.translate(0.5 + lx * offset, 0.5 + LiftPath.CABLE_HEIGHT - r - 0.02, 0.5 + lz * offset);
        // Turn the model's x axis onto "left", then spin about it.
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(-dx, -dz)));
        pose.mulPose(Axis.XP.rotation((float) (angle % (2 * Math.PI))));
        Boxes.box(pose, vc, Boxes.STEEL, -0.05f, -r, -0.03f, 0.05f, r, 0.03f, light, overlay);
        Boxes.box(pose, vc, Boxes.STEEL, -0.05f, -0.03f, -r, 0.05f, 0.03f, r, light, overlay);
        Boxes.box(pose, vc, Boxes.DARK, -0.08f, -0.07f, -0.07f, 0.08f, 0.07f, 0.07f, light, overlay);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(LiftTowerHeadBlockEntity head) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
