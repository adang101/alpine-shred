package io.github.adang101.alpineshred.client.feel;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.architectury.event.events.client.ClientGuiEvent;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.feel.CameraEffects;
import io.github.adang101.alpineshred.feel.ScreenEffects;
import io.github.adang101.alpineshred.gear.GearAccess;
import io.github.adang101.alpineshred.gear.GearNbt;
import io.github.adang101.alpineshred.gear.GearProfile;
import io.github.adang101.alpineshred.gear.GearProfiles;
import io.github.adang101.alpineshred.gear.GearWear;
import io.github.adang101.alpineshred.gear.GogglesItem;
import io.github.adang101.alpineshred.gear.Lens;
import io.github.adang101.alpineshred.movement.RideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * The local rider's camera and screen (design doc sections 9 and 17): camera roll and speed field
 * of view (read by the {@code GameRendererMixin}), screen effects, and the basic HUD: the optional
 * speedometer ({@code client.hudSpeed}) and wax and edge while riding. The trick popup arrives
 * with phase 5.
 */
public final class RideEffects {
    private static final ResourceLocation VIGNETTE = AlpineShred.id("textures/gui/overlay/vignette.png");
    private static final ResourceLocation SPEED_LINES = AlpineShred.id("textures/gui/overlay/speed_lines.png");
    private static final ResourceLocation GOGGLES_FRAME = AlpineShred.id("textures/gui/overlay/goggles_frame.png");
    private static final int OVERLAY_SIZE = 256;

    private static final CameraEffects CAMERA = new CameraEffects();
    private static final ScreenEffects SCREEN = new ScreenEffects();
    private static double speed;
    private static boolean riding;
    private static int lineFrame;

    private RideEffects() {
    }

    public static void register() {
        ClientGuiEvent.RENDER_HUD.register(RideEffects::render);
    }

    /** Degrees of camera roll this frame (positive rolls toward the rider's right). */
    public static float roll(float partialTick) {
        return (float) CAMERA.roll(partialTick);
    }

    /** Degrees added to the field of view this frame. */
    public static float fov(float partialTick) {
        return (float) CAMERA.fov(partialTick);
    }

    static void tick(LocalPlayer player, @Nullable RiderView view) {
        boolean reduceMotion = AlpineConfig.CLIENT_REDUCE_MOTION.on();
        riding = RiderTracker.riding(view);
        speed = view == null ? 0 : view.speed;
        RideState state = view == null ? RideState.OFF : view.state;
        CAMERA.tick(riding, state == RideState.WIPEOUT, view == null ? 0 : view.lean, speed,
                AlpineConfig.CLIENT_FOV_EFFECT.value(), reduceMotion);
        SCREEN.tick(state, speed, inSnowfall(player), goggles(player).isPresent(), AlpineConfig.CLIENT_FLASH_SAFE.on(), reduceMotion);
        FeelStats.maxFovAdded = Math.max(FeelStats.maxFovAdded, (float) CAMERA.fov(1));
        FeelStats.maxRoll = Math.max(FeelStats.maxRoll, (float) Math.abs(CAMERA.roll(1)));
        lineFrame++;
    }

    static void reset() {
        CAMERA.reset();
        SCREEN.reset();
        riding = false;
        speed = 0;
    }

    private static boolean inSnowfall(LocalPlayer player) {
        Level level = player.level();
        if (!level.isRaining()) return false;
        BlockPos pos = player.blockPosition();
        return level.canSeeSky(pos) && level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW;
    }

    private static Optional<ItemStack> goggles(LocalPlayer player) {
        return GearAccess.current().goggles(player);
    }

    private static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        if (firstPerson) {
            goggles(player).ifPresent(stack -> {
                if (!AlpineConfig.CLIENT_GOGGLES_OVERLAY.on()) return;
                Lens lens = GogglesItem.lens(stack);
                if ((lens.overlayArgb() >>> 24) > 0) graphics.fill(0, 0, w, h, lens.overlayArgb());
                overlay(graphics, GOGGLES_FRAME, w, h, 1, 1, 1, 1);
            });
        }
        if (SCREEN.speedLines() > 0) {
            // Two mirrored copies alternate every few ticks so the streaks flicker past.
            boolean flip = (lineFrame / 3) % 2 == 0;
            overlay(graphics, SPEED_LINES, w, h, 1, 1, 1, (float) SCREEN.speedLines() * 0.6f, flip);
        }
        if (SCREEN.whiteout() > 0) overlay(graphics, VIGNETTE, w, h, 1, 1, 1, (float) SCREEN.whiteout());
        if (SCREEN.wipeoutPulse() > 0) overlay(graphics, VIGNETTE, w, h, 0.85f, 0.1f, 0.1f, (float) SCREEN.wipeoutPulse());

        RiderView view = RiderTracker.localView();
        if (view == null || !view.state.isStrapped()) return;
        Font font = mc.font;
        float scale = (float) AlpineConfig.CLIENT_HUD_SCALE.value();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        int sw = (int) (w / scale), sh = (int) (h / scale);
        if (AlpineConfig.CLIENT_HUD_SPEED.on()) {
            String text = String.format(Locale.ROOT, "%.1f b/s", view.speed * 20);
            graphics.drawString(font, text, sw / 2 + 96, sh - 22, 0xFFFFFF, true);
        }
        GearProfile gear = GearProfiles.get(player);
        if (gear.hasGear() && riding) {
            int wax = GearNbt.wax(gear.board());
            String text = Component.translatable("hud.alpineshred.gear",
                    wax > 0 ? Math.round(100.0 * wax / GearWear.WAX_BLOCKS) + "%" : "-", gear.edge()).getString();
            graphics.drawString(font, text, sw / 2 + 96, sh - 11, 0xC0D8F0, true);
        }
        graphics.pose().popPose();
    }

    private static void overlay(GuiGraphics graphics, ResourceLocation texture, int w, int h, float r, float g, float b, float a) {
        overlay(graphics, texture, w, h, r, g, b, a, false);
    }

    private static void overlay(GuiGraphics graphics, ResourceLocation texture, int w, int h, float r, float g, float b, float a, boolean flip) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        graphics.setColor(r, g, b, Math.min(1, a));
        if (flip) {
            graphics.blit(texture, 0, 0, w, h, OVERLAY_SIZE, 0, -OVERLAY_SIZE, OVERLAY_SIZE, OVERLAY_SIZE, OVERLAY_SIZE);
        } else {
            graphics.blit(texture, 0, 0, w, h, 0, 0, OVERLAY_SIZE, OVERLAY_SIZE, OVERLAY_SIZE, OVERLAY_SIZE);
        }
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
