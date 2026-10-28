package io.github.adang101.alpineshred.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.movement.SlideController;
import io.github.adang101.alpineshred.movement.SlopeMath;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /alpineshred debug ride on|off} (design doc section 18): the controller's state, speed,
 * grade and surface, next to the state the server echoed back and the server validator's counts.
 * Text only, top right, so it does not cover the F3 screen. Risk R4 (jittery slope sampling) is
 * judged with this on real terrain.
 */
public final class RideDebugOverlay {
    private static final int COLOR = 0xFFFFFF;
    private static final int WARN = 0xFF7777;

    private static boolean enabled;
    private static int violations;
    private static int corrections;
    private static float serverSpeed;
    private static String lastReason = "";

    private RideDebugOverlay() {
    }

    public static void register() {
        ClientGuiEvent.RENDER_HUD.register((graphics, tickDelta) -> {
            if (enabled) render(graphics);
        });
    }

    static void setEnabled(boolean on) {
        enabled = on;
        if (!on) serverStats(0, 0, 0, "");
    }

    static void serverStats(int v, int c, float speed, String reason) {
        violations = v;
        corrections = c;
        serverSpeed = speed;
        lastReason = reason;
    }

    static void reset() {
        setEnabled(false);
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;

        List<String> lines = new ArrayList<>();
        RideState synced = Riders.state(player);
        SlideController c = RideDriver.INSTANCE.controller();
        lines.add("Alpine Shred ride debug");
        if (c == null) {
            lines.add("not driving  synced " + synced);
        } else {
            SlopeMath.Slope slope = c.slope();
            SurfaceProfile surface = c.surface();
            lines.add(fmt("state %s (%s)  synced %s", c.state(), c.mode().name().toLowerCase(Locale.ROOT), synced));
            lines.add(fmt("speed %.3f b/t  %.1f b/s", c.speed(), c.speed() * 20));
            lines.add(fmt("heading %.0f  travel %.0f  lean %+.2f%s", c.heading(), c.travelYaw(), c.lean(), c.switchStance() ? "  switch" : ""));
            lines.add(fmt("grade %.1f deg  downhill %.0f", slope.degrees(), slope.downhillYaw()));
            lines.add(fmt("surface %s", surface == null ? "none" : surface.id() + " (" + surface.category().name().toLowerCase(Locale.ROOT) + ")"));
            lines.add(fmt("ground %s  vy %+.3f  charge %d  wipeout %d", c.grounded() ? "yes" : "no", c.verticalVelocity(), c.chargeTicks(), c.wipeoutTicks()));
        }
        lines.add(fmt("server: seen %.3f b/t  violations %d  corrections %d", serverSpeed, violations, corrections));
        if (!lastReason.isEmpty()) lines.add("last: " + lastReason);
        if (!RideDriver.INSTANCE.protocolOk()) lines.add("protocol mismatch: riding off");

        Font font = mc.font;
        int y = 4;
        int right = graphics.guiWidth() - 4;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean warn = line.startsWith("last:") || line.startsWith("protocol") || (i > 0 && corrections > 0 && line.startsWith("server"));
            graphics.drawString(font, line, right - font.width(line), y, warn ? WARN : COLOR, true);
            y += font.lineHeight + 1;
        }
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
