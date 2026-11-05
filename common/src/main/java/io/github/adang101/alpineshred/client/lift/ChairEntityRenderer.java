package io.github.adang101.alpineshred.client.lift;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.lift.ChairEntity;
import io.github.adang101.alpineshred.lift.LiftPath;
import io.github.adang101.alpineshred.lift.PassengerRule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a lift chair (design doc section 16): a carriage on the cable, the hanger down to the chair,
 * the seat, the backrest, the footrest and the safety bar, which eases down or up over
 * {@value ChairEntity#BAR_EASE_TICKS} ticks. It is ten placeholder cuboids in {@link Boxes}' materials.
 * The model's origin is the top of the seat plank, where {@link ChairEntity} seats its riders, and it
 * faces along the cable. The dispatcher lights the chair where it hangs.
 */
public final class ChairEntityRenderer extends EntityRenderer<ChairEntity> {
    /** Half the chair's width at the default of two seats; it grows with the seat count. */
    private static final float MARGIN = 0.15f;
    /** The safety bar's hinge, on the backrest. */
    private static final float PIVOT_Y = 0.55f, PIVOT_Z = -0.35f;

    public ChairEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(ChairEntity chair) {
        return Boxes.TEXTURE;
    }

    @Override
    public void render(ChairEntity chair, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        long started = LiftRenderStats.begin();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(Boxes.TEXTURE));
        int overlay = OverlayTexture.NO_OVERLAY;
        float half = (float) (AlpineConfig.LIFT_SEATS.get() * PassengerRule.SEAT_SPACING / 2 + MARGIN);
        float hang = (float) LiftPath.CHAIR_HANG;

        pose.pushPose();
        // The model faces +z; a heading of yaw faces (-sin yaw, cos yaw).
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, chair.yRotO, chair.getYRot())));
        // Carriage on the cable and the hanger down to the backrest.
        Boxes.box(pose, vc, Boxes.DARK, -0.18f, hang - 0.1f, -0.22f, 0.18f, hang + 0.12f, 0.22f, light, overlay);
        Boxes.box(pose, vc, Boxes.STEEL, -0.05f, 0.75f, -0.5f, 0.05f, hang - 0.1f, -0.4f, light, overlay);
        // Seat, backrest and the rails the bar hinges on.
        Boxes.box(pose, vc, Boxes.SEAT, -half, -0.1f, -0.4f, half, 0f, 0.45f, light, overlay);
        Boxes.box(pose, vc, Boxes.CUSHION, -half, 0f, -0.5f, half, 0.75f, -0.4f, light, overlay);
        Boxes.box(pose, vc, Boxes.DARK, -half - 0.06f, -0.1f, -0.5f, -half, 0.6f, -0.34f, light, overlay);
        Boxes.box(pose, vc, Boxes.DARK, half, -0.1f, -0.5f, half + 0.06f, 0.6f, -0.34f, light, overlay);
        // Footrest.
        Boxes.box(pose, vc, Boxes.STEEL, -half + 0.03f, -0.62f, 0.48f, -half + 0.09f, -0.1f, 0.52f, light, overlay);
        Boxes.box(pose, vc, Boxes.STEEL, half - 0.09f, -0.62f, 0.48f, half - 0.03f, -0.1f, 0.52f, light, overlay);
        Boxes.box(pose, vc, Boxes.RUBBER, -half, -0.7f, 0.44f, half, -0.62f, 0.6f, light, overlay);

        // The bar swings about the hinge from raised (a quarter turn up) to lowered (level, across the lap).
        float closed = Mth.clamp(chair.barProgress(partialTick), 0f, 1f);
        float eased = closed * closed * (3 - 2 * closed);
        pose.pushPose();
        pose.translate(0, PIVOT_Y, PIVOT_Z);
        pose.mulPose(Axis.XP.rotationDegrees(-90f * (1f - eased)));
        Boxes.box(pose, vc, Boxes.BAR, -half - 0.06f, -0.03f, 0f, -half + 0.02f, 0.03f, 0.95f, light, overlay);
        Boxes.box(pose, vc, Boxes.BAR, half - 0.02f, -0.03f, 0f, half + 0.06f, 0.03f, 0.95f, light, overlay);
        Boxes.box(pose, vc, Boxes.ACCENT, -half - 0.06f, -0.04f, 0.9f, half + 0.06f, 0.04f, 0.98f, light, overlay);
        pose.popPose();
        pose.popPose();
        LiftRenderStats.end(LiftRenderStats.Part.CHAIR, started);
    }
}
