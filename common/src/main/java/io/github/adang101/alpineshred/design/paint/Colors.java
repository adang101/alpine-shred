package io.github.adang101.alpineshred.design.paint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The color picker's math and the palette (design doc section 22, "Color and templates"): HSV to
 * RGB and back for the square and hue slider, hex for the field, and the 32 swatches (16 dye
 * colors, 8 grays, 8 earth tones). Colors here are 0xRRGGBB.
 */
public final class Colors {
    /** Vanilla's dye colors, in dye order (white, orange, ... black). */
    public static final int[] DYES = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};
    public static final int[] GRAYS = {0x000000, 0x242424, 0x484848, 0x6D6D6D, 0x919191, 0xB6B6B6, 0xDADADA, 0xFFFFFF};
    public static final int[] EARTH = {0x3B2A1A, 0x5C4033, 0x8B5A2B, 0xA0522D, 0xC19A6B, 0xD2B48C, 0x6B8E23, 0x556B2F};

    private Colors() {
    }

    /** The 32 palette swatches, in display order. */
    public static int[] palette() {
        int[] out = new int[32];
        System.arraycopy(DYES, 0, out, 0, 16);
        System.arraycopy(GRAYS, 0, out, 16, 8);
        System.arraycopy(EARTH, 0, out, 24, 8);
        return out;
    }

    /** {@code h} in degrees [0, 360), {@code s} and {@code v} in [0, 1]. */
    public static int hsvToRgb(double h, double s, double v) {
        h = ((h % 360) + 360) % 360;
        s = clamp01(s);
        v = clamp01(v);
        double c = v * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = v - c;
        double r, g, b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return channel(r + m) << 16 | channel(g + m) << 8 | channel(b + m);
    }

    /** {h, s, v} for an 0xRRGGBB color. */
    public static double[] rgbToHsv(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0, g = ((rgb >> 8) & 0xFF) / 255.0, b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double d = max - min;
        double h;
        if (d == 0) {
            h = 0;
        } else if (max == r) {
            h = 60 * (((g - b) / d) % 6);
        } else if (max == g) {
            h = 60 * ((b - r) / d + 2);
        } else {
            h = 60 * ((r - g) / d + 4);
        }
        if (h < 0) h += 360;
        return new double[]{h, max == 0 ? 0 : d / max, max};
    }

    /** {@code #rrggbb}. */
    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    /** Parses {@code #rrggbb}, {@code rrggbb} or {@code #rgb}; -1 if it is not one. */
    public static int parseHex(String s) {
        if (s == null) return -1;
        String t = s.strip();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        if (t.length() != 6) return -1;
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The recent-colors row: {@code color} moved to the front, at most {@code max} kept. */
    public static List<Integer> remember(List<Integer> recent, int color, int max) {
        List<Integer> out = new ArrayList<>(recent.size() + 1);
        out.add(color & 0xFFFFFF);
        for (int c : recent) {
            if ((c & 0xFFFFFF) != (color & 0xFFFFFF) && out.size() < max) out.add(c);
        }
        return out;
    }

    private static int channel(double v) {
        return (int) Math.round(clamp01(v) * 255);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
