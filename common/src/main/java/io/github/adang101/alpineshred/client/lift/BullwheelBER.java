package io.github.adang101.alpineshred.client.lift;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.adang101.alpineshred.lift.LiftNodeBlockEntity;
import io.github.adang101.alpineshred.lift.LiftPath;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;

/**
 * The bullwheel on both stations (design doc section 16): a flat wheel of {@link LiftPath#WHEEL_RADIUS}
 * at cable height that turns by {@code travel / WHEEL_RADIUS} radians, so its rim keeps pace with the
 * cable. The drive station also draws the cable of the span between itself and the first head (or the
 * return station, on a line with no heads).
 */
public final class BullwheelBER implements BlockEntityRenderer<LiftNodeBlockEntity> {
    private static final int RIM_PIECES = 16;
    private static final float RIM = (float) LiftPath.WHEEL_RADIUS - 0.15f;

    public BullwheelBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LiftNodeBlockEntity station, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockPos pos = station.getBlockPos();
        ClientLine line = ClientLiftLines.lineAt(pos);
        if (line == null || station.getLevel() == null) return;
        int index = line.nodes().indexOf(pos);
        if (index != 0 && index != line.nodes().size() - 1) return;
        double travel = line.travelAt(partialTick);

        if (index == 0) {
            long started = LiftRenderStats.begin();
            Cables.drawSpan(pose, buffers, station.getLevel(), pos, line.path(), 0, travel);
            LiftRenderStats.end(LiftRenderStats.Part.CABLE, started);
        }

        long started = LiftRenderStats.begin();
        // The wheel turns above the block, which may be dark inside.
        light = LevelRenderer.getLightColor(station.getLevel(), pos.above(3));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(Boxes.TEXTURE));
        pose.pushPose();
        // The cable arcs round the node at cable height; the rim sits just under it. Both wheels turn the same way.
        pose.translate(0.5, 0.5 + LiftPath.CABLE_HEIGHT - 0.15, 0.5);
        pose.mulPose(Axis.YP.rotation((float) (-(travel / LiftPath.WHEEL_RADIUS) % (2 * Math.PI))));
        for (int i = 0; i < RIM_PIECES; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation((float) (2 * Math.PI * i / RIM_PIECES)));
            Boxes.box(pose, vc, Boxes.DARK, RIM - 0.15f, -0.125f, -0.4f, RIM + 0.15f, 0.125f, 0.4f, light, overlay);
            pose.popPose();
        }
        Boxes.box(pose, vc, Boxes.STEEL, -RIM, -0.06f, -0.06f, RIM, 0.06f, 0.06f, light, overlay);
        Boxes.box(pose, vc, Boxes.STEEL, -0.06f, -0.06f, -RIM, 0.06f, 0.06f, RIM, light, overlay);
        Boxes.box(pose, vc, Boxes.ACCENT, -0.2f, -0.2f, -0.2f, 0.2f, 0.2f, 0.2f, light, overlay);
        // The shaft down to the station block.
        Boxes.box(pose, vc, Boxes.STEEL, -0.1f, -1.85f, -0.1f, 0.1f, 0f, 0.1f, light, overlay);
        pose.popPose();
        LiftRenderStats.end(LiftRenderStats.Part.WHEEL, started);
    }

    @Override
    public boolean shouldRenderOffScreen(LiftNodeBlockEntity station) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
