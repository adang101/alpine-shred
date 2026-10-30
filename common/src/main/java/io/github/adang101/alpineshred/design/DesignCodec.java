package io.github.adang101.alpineshred.design;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Bytes for designs (design doc section 22, "Data format" and "Server validation on save"): faces
 * as raw RGBA, deflated; strict inflation (exactly {@code w * h * 4} bytes per face, no more, which
 * blocks decompression bombs); the CRC32 hash; the name rules; and the one check that every path
 * in (the save packet, the item tag, a share code) goes through.
 */
public final class DesignCodec {
    /** {@code design.maxCompressedBytes}'s default: both deflated faces together. */
    public static final int DEFAULT_MAX_COMPRESSED = 8192;

    private DesignCodec() {
    }

    /** Thrown for bytes that are not a valid face or design. */
    public static final class InvalidDesign extends Exception {
        public final DesignStatus status;

        public InvalidDesign(DesignStatus status, String message) {
            super(message);
            this.status = status;
        }
    }

    // ---- faces ------------------------------------------------------------------------------------

    /** ARGB pixels to raw RGBA bytes. */
    public static byte[] raw(int[] face) {
        byte[] out = new byte[face.length * 4];
        for (int i = 0; i < face.length; i++) {
            int p = face[i];
            out[i * 4] = (byte) (p >> 16);
            out[i * 4 + 1] = (byte) (p >> 8);
            out[i * 4 + 2] = (byte) p;
            out[i * 4 + 3] = (byte) (p >>> 24);
        }
        return out;
    }

    /** Raw RGBA bytes to ARGB pixels; rule 3: every alpha is 0 or 255. */
    public static int[] fromRaw(byte[] raw, DesignKind kind) throws InvalidDesign {
        if (raw.length != kind.faceBytes()) throw new InvalidDesign(DesignStatus.INVALID, "face is " + raw.length + " bytes");
        int[] out = new int[kind.pixels()];
        for (int i = 0; i < out.length; i++) {
            int a = raw[i * 4 + 3] & 0xFF;
            if (a != 0 && a != 255) throw new InvalidDesign(DesignStatus.INVALID, "alpha " + a + " at pixel " + i);
            out[i] = a << 24 | (raw[i * 4] & 0xFF) << 16 | (raw[i * 4 + 1] & 0xFF) << 8 | raw[i * 4 + 2] & 0xFF;
        }
        return out;
    }

    public static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
            byte[] buf = new byte[1024];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /**
     * Inflates exactly {@code size} bytes. Fewer, more (rule 2's decompression-bomb guard: inflation
     * stops at {@code size} and any byte past it rejects), or a broken stream is invalid.
     */
    public static byte[] inflate(byte[] data, int size) throws InvalidDesign {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            byte[] out = new byte[size];
            int n = 0;
            while (n < size) {
                int got = inflater.inflate(out, n, size - n);
                if (got == 0) break; // the end of the stream, or of the input
                n += got;
            }
            if (n != size) throw new InvalidDesign(DesignStatus.INVALID, "face inflates to " + n + " of " + size + " bytes");
            if (!inflater.finished()) {
                if (inflater.inflate(new byte[1]) > 0) {
                    throw new InvalidDesign(DesignStatus.TOO_LARGE, "face inflates past " + size + " bytes");
                }
                if (!inflater.finished()) throw new InvalidDesign(DesignStatus.INVALID, "truncated deflate stream");
            }
            return out;
        } catch (DataFormatException e) {
            throw new InvalidDesign(DesignStatus.INVALID, "broken deflate stream: " + e.getMessage());
        } finally {
            inflater.end();
        }
    }

    /** CRC32 of the kind's id, the raw top, the raw base and the edge's three bytes. */
    public static int hash(DesignKind kind, byte[] rawTop, byte[] rawBase, int edge) {
        CRC32 crc = new CRC32();
        crc.update(kind.id.getBytes(StandardCharsets.UTF_8));
        crc.update(rawTop);
        crc.update(rawBase);
        crc.update(new byte[]{(byte) (edge >> 16), (byte) (edge >> 8), (byte) edge});
        return (int) crc.getValue();
    }

    // ---- names --------------------------------------------------------------------------------------

    /**
     * Rule 4: control characters and formatting codes (a section sign and the character after it)
     * stripped, trimmed, cut to {@value Design#NAME_MAX} characters (code points).
     */
    public static String sanitizeName(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\u00a7') {
                if (i < name.length()) i += Character.charCount(name.codePointAt(i));
                continue;
            }
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT
                    || Character.getType(cp) == Character.LINE_SEPARATOR || Character.getType(cp) == Character.PARAGRAPH_SEPARATOR) {
                continue;
            }
            sb.appendCodePoint(cp);
        }
        String s = sb.toString().trim();
        if (s.codePointCount(0, s.length()) > Design.NAME_MAX) s = s.substring(0, s.offsetByCodePoints(0, Design.NAME_MAX)).trim();
        return s;
    }

    // ---- the check ----------------------------------------------------------------------------------

    /**
     * Rules 2 to 5 on deflated faces: the compressed size within {@code maxCompressed}, each face
     * exactly the kind's size with alpha 0 or 255. Pixels outside the outline become transparent,
     * the edge is masked and the name sanitized ({@link Design}'s constructor). Author fields are
     * the caller's (the server writes them).
     */
    public static Design decode(DesignKind kind, byte[] topZ, byte[] baseZ, int edge, Design.Pair pair, String name,
                                String author, UUID authorId, int maxCompressed) throws InvalidDesign {
        if (topZ == null || baseZ == null) throw new InvalidDesign(DesignStatus.INVALID, "missing face");
        if (topZ.length + baseZ.length > maxCompressed) {
            throw new InvalidDesign(DesignStatus.TOO_LARGE, "compressed design is " + (topZ.length + baseZ.length) + " bytes");
        }
        int[] top = fromRaw(inflate(topZ, kind.faceBytes()), kind);
        int[] base = fromRaw(inflate(baseZ, kind.faceBytes()), kind);
        return new Design(kind, top, base, edge, pair, name, author, authorId);
    }

    /** The deflated top face, as the item tag and the save packet carry it. */
    public static byte[] topZ(Design d) {
        return deflate(raw(d.top()));
    }

    public static byte[] baseZ(Design d) {
        return deflate(raw(d.base()));
    }
}
