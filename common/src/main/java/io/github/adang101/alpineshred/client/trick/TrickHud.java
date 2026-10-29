package io.github.adang101.alpineshred.client.trick;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import io.github.adang101.alpineshred.client.RideDriver;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.registry.ModSounds;
import io.github.adang101.alpineshred.trick.Combo;
import io.github.adang101.alpineshred.trick.LandingQuality;
import io.github.adang101.alpineshred.trick.TrickResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * The trick popup (design doc section 17, "HUD"): at the top center, the tricks of the combo in
 * progress stacking up, its running score and multiplier, and under them a timer bar that drains
 * over the 60 ground ticks the combo has to continue. On a bank it shows the total and fades; on a
 * bail, "Bail!". Every number is the server's ({@code s2c/trick_result}); the popup only counts the
 * timer down, pausing it in the air and on rails as the server does.
 */
public final class TrickHud {
    private static final int MAX_LINES = 5;
    private static final int FADE_TICKS = 50;

    private record Line(Component text, int points) {
    }

    private static final Deque<Line> lines = new ArrayDeque<>();
    private static int comboTotal;
    private static double multiplier = 1;
    private static int timer = -1;
    private static int fade;
    private static Component banner;
    private static int bannerColor;
    /** Everything received, oldest first, for the smoke test to compare with the server. */
    private static final List<TrickResult> received = Collections.synchronizedList(new ArrayList<>());
    private static int lastBankedTotal = -1;

    private TrickHud() {
    }

    public static void register() {
        ClientGuiEvent.RENDER_HUD.register(TrickHud::render);
        ClientTickEvent.CLIENT_POST.register(mc -> tick());
    }

    /** {@code s2c/trick_result} for the local rider. */
    public static void accept(TrickResult result) {
        received.add(result);
        Minecraft mc = Minecraft.getInstance();
        if (result.bail()) {
            lines.clear();
            comboTotal = 0;
            timer = -1;
            show(Component.translatable("trick.alpineshred.popup.bail").withStyle(ChatFormatting.BOLD), 0xFF5555);
            play(mc, ModSounds.BAIL.get(), 1.0f);
            return;
        }
        if (result.banked()) {
            lastBankedTotal = result.comboTotal();
            timer = -1;
            lines.clear();
            comboTotal = 0;
            show(Component.translatable("trick.alpineshred.popup.bank", result.comboTotal()).withStyle(ChatFormatting.BOLD), 0xFFDD55);
            play(mc, ModSounds.COMBO_BANK.get(), (float) Math.min(2.0, 0.8 + 0.2 * result.multiplier()));
            return;
        }
        for (TrickResult.Line l : result.lines()) {
            lines.addLast(new Line(TrickNames.label(l.id()), l.points()));
            while (lines.size() > MAX_LINES) lines.removeFirst();
        }
        comboTotal = result.comboTotal();
        multiplier = result.multiplier();
        timer = Combo.WINDOW_TICKS;
        fade = 0;
        banner = result.quality() == LandingQuality.CLEAN ? null
                : Component.translatable("trick.alpineshred.quality." + result.quality().serializedName());
        bannerColor = result.quality() == LandingQuality.PERFECT ? 0x55FF55 : 0xFFAA00;
    }

    private static void show(Component text, int color) {
        banner = text;
        bannerColor = color;
        fade = FADE_TICKS;
    }

    private static void play(Minecraft mc, net.minecraft.sounds.SoundEvent sound, float pitch) {
        LocalPlayer p = mc.player;
        if (p != null) p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 0.8f, pitch, false);
    }

    private static void tick() {
        if (fade > 0 && --fade == 0) banner = null;
        if (timer > 0) {
            LocalPlayer p = Minecraft.getInstance().player;
            RideState state = p == null ? RideState.OFF : RideDriver.INSTANCE.localState(p);
            if (state.isGroundRiding()) timer--;
        }
    }

    private static void render(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int y = 8;
        if (timer >= 0) {
            for (Line line : lines) {
                Component text = Component.empty().append(line.text())
                        .append(Component.literal("  " + line.points()).withStyle(ChatFormatting.GRAY));
                g.drawCenteredString(font, text, cx, y, 0xFFFFFF);
                y += 10;
            }
            String score = String.format(Locale.ROOT, "%d  ×%.2f", comboTotal, multiplier);
            g.drawCenteredString(font, Component.literal(score).withStyle(ChatFormatting.BOLD), cx, y + 2, 0xFFFFFF);
            y += 14;
            int width = 80;
            int filled = Math.round(width * Math.max(0, timer) / (float) Combo.WINDOW_TICKS);
            g.fill(cx - width / 2, y, cx + width / 2, y + 3, 0x80000000);
            g.fill(cx - width / 2, y, cx - width / 2 + filled, y + 3, 0xFFFFDD55);
            y += 8;
        }
        if (banner != null) {
            int alpha = fade > 0 ? Math.max(16, Math.min(255, fade * 255 / 20)) : 255;
            g.drawCenteredString(font, banner, cx, y, (alpha << 24) | (bannerColor & 0xFFFFFF));
        }
    }

    public static List<TrickResult> received() {
        synchronized (received) {
            return List.copyOf(received);
        }
    }

    /** The last banked total the popup showed, or -1. */
    public static int lastBankedTotal() {
        return lastBankedTotal;
    }
}
