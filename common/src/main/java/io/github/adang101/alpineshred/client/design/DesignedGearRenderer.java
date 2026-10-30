package io.github.adang101.alpineshred.client.design;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.design.Design;
import io.github.adang101.alpineshred.design.DesignKind;
import io.github.adang101.alpineshred.design.DesignNbt;
import io.github.adang101.alpineshred.design.GearMesh;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * {@code DesignedGearRenderer} (design doc section 22, "Rendering"): draws boards and skis from
 * {@link GearMesh} with the stack's atlas ({@link DesignTextureCache}) in one
 * {@code entityCutoutNoCull} render type, so designs need no baked models. It draws in item model
 * space, so the item renderer (held, dropped, inventory), the player layer (riding and carried on
 * the back) and the bench preview all place it the same way.
 */
public final class DesignedGearRenderer {
    private static final float PX = 1f / 16f;
    /** Whose gear is being drawn: the player layer marks other players', so their designs can be hidden. */
    private static final ThreadLocal<Boolean> OTHERS = ThreadLocal.withInitial(() -> false);

    private DesignedGearRenderer() {
    }

    /** Set around drawing another player's gear (and cleared after); {@code client.showCustomDesigns} applies to it. */
    public static void drawingOthers(boolean others) {
        OTHERS.set(others);
    }

    /** Draws a board or skis stack in item model space. */
    public static void render(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        DesignKind kind = DesignNbt.kindOf(stack);
        if (kind == null) return;
        boolean custom = !OTHERS.get() || AlpineConfig.CLIENT_SHOW_CUSTOM_DESIGNS.on();
        ResourceLocation atlas = DesignTextureCache.texture(stack, custom);
        boolean mirror = false;
        if (kind == DesignKind.SKI && custom) {
            CompoundTag tag = DesignNbt.tag(stack);
            mirror = tag != null && Design.Pair.byId(tag.getString("pair")) == Design.Pair.MIRROR;
        }
        draw(kind, atlas, sheet(stack), mirror, pose, buffers, light, overlay);
    }

    /** The item's model sheet, which the bindings are textured from. */
    public static ResourceLocation sheet(ItemStack stack) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new ResourceLocation(item.getNamespace(), "textures/item/gear/" + item.getPath() + ".png");
    }

    /** Draws gear of {@code kind} with a given atlas (the bench preview passes its working copy's). */
    public static void draw(DesignKind kind, ResourceLocation atlas, ResourceLocation bindingSheet, boolean mirrorPair,
                            PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        GearMesh.Mesh mesh = GearMesh.of(kind, mirrorPair);
        emit(buffers.getBuffer(RenderType.entityCutoutNoCull(atlas)), pose, mesh.deck(), light, overlay);
        emit(buffers.getBuffer(RenderType.entityCutoutNoCull(bindingSheet)), pose, mesh.bindings(), light, overlay);
    }

    private static void emit(VertexConsumer vc, PoseStack pose, List<GearMesh.Quad> quads, int light, int overlay) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (GearMesh.Quad q : quads) {
            for (int i = 0; i < 4; i++) {
                vc.vertex(m, q.x(i) * PX, q.y(i) * PX, q.z(i) * PX).color(255, 255, 255, 255).uv(q.u(i), q.v(i))
                        .overlayCoords(overlay).uv2(light).normal(n, q.nx(), q.ny(), q.nz()).endVertex();
            }
        }
    }
}
