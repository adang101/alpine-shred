package io.github.adang101.alpineshred.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.gear.GearAccess;
import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearProfile;
import io.github.adang101.alpineshred.movement.RideSnapshot;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.registry.ModItems;
import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
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
 * <p>Boards and skis are drawn by the procedural {@code DesignedGearRenderer} (section 22) through
 * their built-in item renderer, so the item, the rider and the bench preview look alike; the pair of
 * skis is drawn once, centred between the feet.
 *
 * <p>Ski poles are a pair: one pole in each hand, shaft down and a little back, drawn here from
 * boxes because the item's flat inventory sprite reads as a sword in the hand. The vanilla held
 * item is hidden for them ({@code ItemInHandLayerMixin}, through {@link #drawsPoles}).
 */
public class SnowGearLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float PX = 1f / 16f;
    /** Degrees the pole tips back from the line of the arm. */
    private static final float POLE_TILT = 45f;
    private static final ResourceLocation POLE_TEXTURE = AlpineShred.id("textures/entity/ski_poles.png");
    private static final ResourceLocation CARBON_POLE_TEXTURE = AlpineShred.id("textures/entity/carbon_ski_poles.png");

    private final ItemRenderer itemRenderer;
    private final ModelPart pole = buildPole();

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
        renderPoles(pose, buffers, light, player);
    }

    /** True when this layer draws {@code entity}'s poles, so the vanilla held item must not. */
    public static boolean drawsPoles(LivingEntity entity, ItemStack stack) {
        if (!(entity instanceof AbstractClientPlayer player) || !stack.is(ModTags.Items.POLES)) return false;
        if (player.isInvisible() || player.isSpectator()) return false;
        Entity camera = Minecraft.getInstance().getCameraEntity();
        int range = AlpineConfig.CLIENT_GEAR_RENDER_DISTANCE.value();
        return camera == null || camera == player || camera.distanceToSqr(player) <= (double) range * range;
    }

    /** The pole for {@code arm}: what that hand holds, or the other hand's poles when it is empty. */
    private static ItemStack poleFor(AbstractClientPlayer player, HumanoidArm arm) {
        boolean main = arm == player.getMainArm();
        ItemStack held = main ? player.getMainHandItem() : player.getOffhandItem();
        ItemStack other = main ? player.getOffhandItem() : player.getMainHandItem();
        if (held.is(ModTags.Items.POLES)) return held;
        if (held.isEmpty() && other.is(ModTags.Items.POLES)) return other;
        return ItemStack.EMPTY;
    }

    private void renderPoles(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        boolean slim = "slim".equals(player.getModelName());
        for (HumanoidArm arm : HumanoidArm.values()) {
            ItemStack stack = poleFor(player, arm);
            if (stack.isEmpty()) continue;
            ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            float centreX = (arm == HumanoidArm.RIGHT ? -1 : 1) * (slim ? 0.5f : 1f) * PX;
            ResourceLocation texture = stack.is(ModItems.CARBON_SKI_POLES.get()) ? CARBON_POLE_TEXTURE : POLE_TEXTURE;
            pose.pushPose();
            part.translateAndRotate(pose);
            pose.translate(centreX, 9.5f * PX, 0);
            // Arm space: +y runs down the arm to the fist, +z is behind the rider.
            pose.mulPose(Axis.XP.rotationDegrees(POLE_TILT));
            pole.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    /** One pole, grip at the origin (the fist) and the shaft running 14 pixels down +y. */
    private static ModelPart buildPole() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("pole", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-0.25f, 0.5f, -0.25f, 0.5f, 12.5f, 0.5f)      // shaft
                        .texOffs(4, 0).addBox(-0.5f, -2.5f, -0.5f, 1f, 3f, 1f)              // grip, through the fist
                        .texOffs(12, 0).addBox(-1.25f, 10.75f, -1.25f, 2.5f, 0.25f, 2.5f)   // basket
                        .texOffs(4, 8).addBox(-0.15f, 13f, -0.15f, 0.3f, 1f, 0.3f),         // tip
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16).bakeRoot().getChild("pole");
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
            // Under the feet, following crouch offsets of the legs: the item models' half-pixel deck
            // is centred on the item origin, so a quarter pixel down puts its top at the soles (the
            // lean layer lifts the rider by LeanRig.STAND to stand on it).
            ModelPart right = model.rightLeg;
            ModelPart left = model.leftLeg;
            pose.translate((right.x + left.x) / 2f * PX, ((right.y + left.y) / 2f + 12f + 0.25f) * PX, (right.z + left.z) / 2f * PX);
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
        // Section 22: client.showCustomDesigns hides other players' designs (never your own).
        io.github.adang101.alpineshred.client.design.DesignedGearRenderer.drawingOthers(player != Minecraft.getInstance().player);
        try {
            draw(pose, buffers, light, player, stack, 3);
        } finally {
            io.github.adang101.alpineshred.client.design.DesignedGearRenderer.drawingOthers(false);
        }
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
