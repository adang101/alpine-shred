package io.github.adang101.alpineshred.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.gear.GearAccess;
import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearProfile;
import io.github.adang101.alpineshred.movement.RideSnapshot;
import io.github.adang101.alpineshred.movement.Riders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws goggles, mitts and the board or skis on every player by reading {@link GearAccess}
 * (design doc sections 6 and 16). One layer for both loaders; no per-library renderer is
 * registered. Each piece is the item's own model drawn with the {@code FIXED} display context, so
 * placement is tuned in each model's {@code display.fixed} rather than in code.
 *
 * <h2>Coordinates</h2>
 * Inside a render layer the pose is model space: 1 unit = 1 block = 16 model pixels, y points
 * down, the face looks toward -z. Item models are authored y-up in a 16-pixel cube, so each
 * piece is flipped 180 degrees about x after positioning.
 *
 * <p>Phase 1 draws the pair-of-skis model once, centred between the feet; per-leg skis that follow
 * the leg animation come with the procedural {@code DesignedGearRenderer} (section 22).
 */
public class SnowGearLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float PX = 1f / 16f;

    private final ItemRenderer itemRenderer;

    public SnowGearLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                         EntityRendererProvider.Context context) {
        super(parent);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        Entity camera = Minecraft.getInstance().getCameraEntity();
        int range = AlpineConfig.CLIENT_GEAR_RENDER_DISTANCE.value();
        if (camera != null && camera != player && camera.distanceToSqr(player) > (double) range * range) return;

        GearAccess gear = GearAccess.current();
        gear.goggles(player).ifPresent(stack -> renderGoggles(pose, buffers, light, player, stack));
        gear.mitts(player).ifPresent(stack -> renderMitts(pose, buffers, light, player, stack));
        gear.board(player).ifPresent(stack -> renderBoardOrSkis(pose, buffers, light, player, stack));
    }

    private void renderGoggles(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ItemStack stack) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        pose.pushPose();
        model.head.translateAndRotate(pose);
        pose.translate(0, -4 * PX, 0); // head centre
        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            // Sit over the helmet brim: up and forward one pixel, and outside the armor inflation.
            pose.translate(0, -1 * PX, -1 * PX);
            pose.scale(1.15f, 1.15f, 1.15f);
        }
        flipUp(pose);
        draw(pose, buffers, light, player, stack, 0);
        pose.popPose();
    }

    private void renderMitts(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ItemStack stack) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        boolean slim = "slim".equals(player.getModelName());
        for (HumanoidArm arm : HumanoidArm.values()) {
            ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            float centreX = (arm == HumanoidArm.RIGHT ? -1 : 1) * (slim ? 0.5f : 1f) * PX;
            pose.pushPose();
            part.translateAndRotate(pose);
            pose.translate(centreX, 8.5f * PX, 0);
            if (slim) pose.scale(0.85f, 1f, 1f);
            flipUp(pose);
            draw(pose, buffers, light, player, stack, arm.ordinal() + 1);
            pose.popPose();
        }
    }

    private void renderBoardOrSkis(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ItemStack stack) {
        GearKind kind = GearProfile.kindOf(stack);
        if (kind == null) return;
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        RideSnapshot ride = Riders.get(player);
        pose.pushPose();
        if (ride.state().isStrapped()) {
            // Under the feet, following crouch offsets of the legs.
            ModelPart right = model.rightLeg;
            ModelPart left = model.leftLeg;
            pose.translate((right.x + left.x) / 2f * PX, ((right.y + left.y) / 2f + 12f + 0.3f) * PX, (right.z + left.z) / 2f * PX);
            flipUp(pose);
            if (kind == GearKind.BOARD) {
                // A board rides across the body; stance picks which way the nose points.
                pose.mulPose(Axis.YP.rotationDegrees(ride.goofy() ? -90 : 90));
            }
        } else {
            // On the back, base facing out: long axis vertical, nose down. Low enough and far enough
            // out that the top end clears the back of the head (skis are longer than boards).
            model.body.translateAndRotate(pose);
            if (kind == GearKind.BOARD) {
                pose.translate(0, 9.5f * PX, 4.3f * PX);
            } else {
                pose.translate(0, 8.5f * PX, 4.7f * PX);
            }
            pose.mulPose(Axis.XP.rotationDegrees(-90));
        }
        draw(pose, buffers, light, player, stack, 3);
        pose.popPose();
    }

    /** Model space is y-down; turn item models right way up after positioning. */
    private static void flipUp(PoseStack pose) {
        pose.mulPose(Axis.XP.rotationDegrees(180));
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ItemStack stack, int seedOffset) {
        itemRenderer.renderStatic(player, stack, ItemDisplayContext.FIXED, false, pose, buffers, player.level(),
                light, OverlayTexture.NO_OVERLAY, player.getId() + seedOffset);
    }
}
