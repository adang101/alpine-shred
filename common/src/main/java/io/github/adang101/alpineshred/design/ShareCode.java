package io.github.adang101.alpineshred.design;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Share codes (design doc section 22, "Color and templates"): {@code AS1:} and the base64url of
 * the deflated design, so a design can be pasted in chat or a forum post. Pasting checks the code
 * exactly as the server checks a save ({@link DesignCodec#decode}), and a code for skis does not
 * paste onto a board or the reverse. The author is not part of the code: whoever saves it is.
 *
 * <pre>
 * deflate(
 *   byte   kind (ordinal)    byte pair (ordinal)    3 bytes edge RGB
 *   UTF    name (modified UTF-8, as DataOutput writes it)
 *   int    length, then the top face deflated
 *   int    length, then the base face deflated)
 * </pre>
 */
public final class ShareCode {
    public static final String PREFIX = "AS1:";
    /** Codes longer than this are not read at all. */
    public static final int MAX_CODE_LENGTH = 16384;

    private ShareCode() {
    }

    public static String encode(Design d) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(d.kind().ordinal());
            out.writeByte(d.pair().ordinal());
            out.writeByte(d.edge() >> 16);
            out.writeByte(d.edge() >> 8);
            out.writeByte(d.edge());
            out.writeUTF(d.name());
            byte[] top = DesignCodec.topZ(d), base = DesignCodec.baseZ(d);
            out.writeInt(top.length);
            out.write(top);
            out.writeInt(base.length);
            out.write(base);
            out.flush();
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(DesignCodec.deflate(bytes.toByteArray()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads a code for {@code expected} gear. Throws for anything that is not a valid code of that
     * kind, with the reason; the faces must fit {@code maxCompressed} as a save's must.
     */
    public static Design decode(String code, DesignKind expected, int maxCompressed) throws DesignCodec.InvalidDesign {
        if (code == null) throw new DesignCodec.InvalidDesign(DesignStatus.INVALID, "no code");
        String s = code.strip();
        if (!s.startsWith(PREFIX)) throw new DesignCodec.InvalidDesign(DesignStatus.INVALID, "not an AS1 code");
        if (s.length() > MAX_CODE_LENGTH) throw new DesignCodec.InvalidDesign(DesignStatus.TOO_LARGE, "code too long");
        byte[] packed;
        try {
            packed = Base64.getUrlDecoder().decode(s.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new DesignCodec.InvalidDesign(DesignStatus.INVALID, "not base64url");
        }
        // Header, name (at most 32 characters, 3 bytes each in modified UTF-8), two lengths and the faces.
        int limit = 5 + 2 + Design.NAME_MAX * 3 + 8 + maxCompressed;
        byte[] body = inflateAtMost(packed, limit);
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
            int kindId = in.readUnsignedByte();
            if (kindId >= DesignKind.values().length) throw new DesignCodec.InvalidDesign(DesignStatus.INVALID, "unknown kind");
            DesignKind kind = DesignKind.values()[kindId];
            if (kind != expected) {
                throw new DesignCodec.InvalidDesign(DesignStatus.INVALID, "a code for " + kind.id + ", not " + expected.id);
            }
            int pairId = in.readUnsignedByte();
            Design.Pair pair = pairId == 1 ? Design.Pair.MIRROR : Design.Pair.SAME;
            int edge = in.readUnsignedByte() << 16 | in.readUnsignedByte() << 8 | in.readUnsignedByte();
            String name = in.readUTF();
            byte[] top = readBlock(in, maxCompressed);
            byte[] base = readBlock(in, maxCompressed);
            if (in.available() > 0) throw new DesignCodec.InvalidDesign(DesignStatus.INVALID, "trailing bytes");
            return DesignCodec.decode(kind, top, base, edge, pair, name, "", null, maxCompressed);
        } catch (IOException e) {
            throw new DesignCodec.InvalidDesign(DesignStatus.INVALID, "truncated code");
        }
    }

    private static byte[] readBlock(DataInputStream in, int max) throws IOException, DesignCodec.InvalidDesign {
        int n = in.readInt();
        if (n < 0 || n > max) throw new DesignCodec.InvalidDesign(DesignStatus.TOO_LARGE, "face of " + n + " bytes");
        byte[] b = new byte[n];
        in.readFully(b);
        return b;
    }

    /** Inflates up to {@code limit} bytes; a stream that goes on past it is rejected. */
    private static byte[] inflateAtMost(byte[] data, int limit) throws DesignCodec.InvalidDesign {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new DesignCodec.InvalidDesign(DesignStatus.INVALID, "truncated code");
                    }
                    continue;
                }
                out.write(buf, 0, n);
                if (out.size() > limit) throw new DesignCodec.InvalidDesign(DesignStatus.TOO_LARGE, "code inflates too far");
            }
            return out.toByteArray();
        } catch (java.util.zip.DataFormatException e) {
            throw new DesignCodec.InvalidDesign(DesignStatus.INVALID, "broken code");
        } finally {
            inflater.end();
        }
    }
}
