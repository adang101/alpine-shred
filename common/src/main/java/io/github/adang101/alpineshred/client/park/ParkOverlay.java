package io.github.adang101.alpineshred.client.park;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.adang101.alpineshred.park.GrindMaterial;
import io.github.adang101.alpineshred.park.GrindPath;
import io.github.adang101.alpineshred.park.RailPath;
import io.github.adang101.alpineshred.park.V3;
import io.github.adang101.alpineshred.park.block.GrindPaths;
import io.github.adang101.alpineshred.park.block.ParkStates;
import io.github.adang101.alpineshred.park.block.RailBlock;
import io.github.adang101.alpineshred.park.item.ParkWrenchItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Section 10, "Placement and tools", on the client:
 * <ul>
 *   <li><b>Ghost preview.</b> While a rail item is held, the piece placement would choose for the
 *       targeted cell is drawn as a translucent model with its path, green when it connects to a
 *       neighbour and red when it does not. It asks the block's own placement code, which calls
 *       the same {@code RailPlacement.choose} the server does, so what you see is what you get.</li>
 *   <li><b>Wrench overlay.</b> While the wrench is held, every grind path within
 *       {@value #RADIUS} blocks is drawn as a line, colored by material, with its ends marked.</li>
 * </ul>
 */
public final class ParkOverlay {
    static final int RADIUS = 8;
    private static final int RESCAN_TICKS = 10;

    private static List<GrindPath> nearby = List.of();
    private static long scannedAt = Long.MIN_VALUE;
    private static BlockPos scannedFrom = BlockPos.ZERO;

    private ParkOverlay() {
    }

    public static void render(PoseStack pose, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;
        ItemStack held = player.getMainHandItem();
        boolean wrench = held.getItem() instanceof ParkWrenchItem;
        boolean rail = held.getItem() instanceof BlockItem item && item.getBlock() instanceof RailBlock;
        if (!wrench && !rail) return;

        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        if (wrench) paths(mc, player, pose, buffers);
        if (rail) ghost(mc, player, held, pose, buffers);
        pose.popPose();
        buffers.endBatch(RenderType.translucent());
        buffers.endBatch(RenderType.lines());
    }

    // ---- the wrench's overlay ------------------------------------------------------------------------

    private static void paths(Minecraft mc, LocalPlayer player, PoseStack pose, MultiBufferSource buffers) {
        long now = mc.level.getGameTime();
        BlockPos at = player.blockPosition();
        if (now - scannedAt >= RESCAN_TICKS || !at.closerThan(scannedFrom, 2)) {
            List<GrindPath> found = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-RADIUS, -RADIUS / 2, -RADIUS), at.offset(RADIUS, RADIUS / 2, RADIUS))) {
                GrindPath path = GrindPaths.at(mc.level, p.immutable());
                if (path != null) found.add(path);
            }
            nearby = found;
            scannedAt = now;
            scannedFrom = at;
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (GrindPath path : nearby) {
            float[] c = color(path.material());
            polyline(pose, lines, path.path(), c[0], c[1], c[2], 0.9f);
            for (V3 end : new V3[]{path.path().endA(), path.path().endB()}) {
                line(pose, lines, end, end.add(0, 0.25, 0), 1f, 0.9f, 0.2f, 1f);
            }
        }
    }

    private static float[] color(GrindMaterial m) {
        return switch (m) {
            case METAL -> new float[]{0.6f, 0.85f, 1f};
            case COPPER -> new float[]{1f, 0.6f, 0.3f};
            case WOOD -> new float[]{0.85f, 0.65f, 0.35f};
            case PLASTIC -> new float[]{1f, 0.4f, 0.9f};
            case STONE, CONCRETE -> new float[]{0.8f, 0.8f, 0.8f};
        };
    }

    // ---- the rail ghost --------------------------------------------------------------------------------

    private static void ghost(Minecraft mc, LocalPlayer player, ItemStack held, PoseStack pose, MultiBufferSource buffers) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, blockHit);
        if (!context.canPlace()) return;
        RailBlock block = (RailBlock) ((BlockItem) held.getItem()).getBlock();
        BlockState state = block.getStateForPlacement(context);
        if (state == null) return;
        BlockPos pos = context.getClickedPos();
        boolean connects = state.getValue(ParkStates.CONNECTED_A) || state.getValue(ParkStates.CONNECTED_B);
        float r = connects ? 0.35f : 1f, g = connects ? 1f : 0.3f, b = connects ? 0.4f : 0.3f;

        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        pose.pushPose();
        pose.translate(pos.getX(), pos.getY(), pos.getZ());
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), new Translucent(buffers.getBuffer(RenderType.translucent()), 0.5f),
                state, model, r, g, b, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        RailPath path = RailPath.at(RailBlock.shape(state), RailBlock.facing(state), pos.getX(), pos.getY(), pos.getZ());
        polyline(pose, buffers.getBuffer(RenderType.lines()), path, r, g, b, 1f);
    }

    // ---- lines ----------------------------------------------------------------------------------------

    private static void polyline(PoseStack pose, VertexConsumer lines, RailPath path, float r, float g, float b, float a) {
        for (int i = 0; i + 1 < path.pointCount(); i++) {
            line(pose, lines, path.point(i).add(0, 0.03, 0), path.point(i + 1).add(0, 0.03, 0), r, g, b, a);
        }
    }

    private static void line(PoseStack pose, VertexConsumer lines, V3 from, V3 to, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        V3 d = to.sub(from);
        double len = Math.max(1.0e-6, d.length());
        float nx = (float) (d.x() / len), ny = (float) (d.y() / len), nz = (float) (d.z() / len);
        lines.vertex(m, (float) from.x(), (float) from.y(), (float) from.z()).color(r, g, b, a).normal(n, nx, ny, nz).endVertex();
        lines.vertex(m, (float) to.x(), (float) to.y(), (float) to.z()).color(r, g, b, a).normal(n, nx, ny, nz).endVertex();
    }

    /** Passes vertices through with their alpha scaled: a block model, see-through. */
    private record Translucent(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color(r, g, b, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            delegate.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
