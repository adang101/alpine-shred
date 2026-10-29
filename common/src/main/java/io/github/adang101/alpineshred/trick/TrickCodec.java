package io.github.adang101.alpineshred.trick;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wire formats of {@code c2s/air_report}, {@code c2s/grind_report} and {@code s2c/trick_result}
 * (section 15), over plain data streams so they are unit-tested without Minecraft. Readers reject
 * values no honest client sends (NaN, impossible counts); the validator does the rest.
 */
public final class TrickCodec {
    public static final int MAX_LINES = 32;
    public static final int MAX_ID_CHARS = 32;

    private TrickCodec() {
    }

    public static void writeAir(DataOutput out, AirReport r) throws IOException {
        out.writeFloat((float) r.yawAccum());
        out.writeFloat((float) r.pitchAccum());
        for (GrabType type : GrabType.values()) out.writeShort(Math.min(Short.MAX_VALUE, r.grab(type)));
        out.writeFloat((float) r.apex());
        out.writeShort(Math.min(Short.MAX_VALUE, r.airTicks()));
        out.writeFloat((float) r.takeoffRelative());
        out.writeFloat((float) r.headingError());
        out.writeFloat((float) r.flipResidual());
        out.writeByte((r.switchLanding() ? 1 : 0) | (r.fromRail() ? 2 : 0) | (r.endsOnRail() ? 4 : 0));
    }

    public static AirReport readAir(DataInput in) throws IOException {
        double yaw = finite(in.readFloat()), pitch = finite(in.readFloat());
        int[] grabs = new int[GrabType.values().length];
        for (int i = 0; i < grabs.length; i++) grabs[i] = in.readShort();
        double apex = finite(in.readFloat());
        int air = in.readShort();
        double takeoff = finite(in.readFloat()), heading = finite(in.readFloat()), flip = finite(in.readFloat());
        int flags = in.readByte();
        return new AirReport(yaw, pitch, grabs, apex, air, takeoff, heading, flip, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
    }

    public static void writeGrind(DataOutput out, GrindReport g) throws IOException {
        out.writeInt(g.x());
        out.writeInt(g.y());
        out.writeInt(g.z());
        out.writeByte((g.box() ? 1 : 0) | (g.switchEntry() ? 2 : 0));
        out.writeByte(g.variant());
        out.writeFloat((float) g.entryAxis());
        out.writeFloat((float) g.lengthBlocks());
        out.writeShort(Math.min(Short.MAX_VALUE, g.durationTicks()));
        out.writeByte(Math.min(127, g.switchUps()));
    }

    public static GrindReport readGrind(DataInput in) throws IOException {
        int x = in.readInt(), y = in.readInt(), z = in.readInt();
        int flags = in.readByte();
        int variant = in.readByte();
        double axis = finite(in.readFloat()), length = finite(in.readFloat());
        int ticks = in.readShort();
        int switchUps = in.readByte();
        return new GrindReport(x, y, z, (flags & 1) != 0, variant, axis, (flags & 2) != 0, length, ticks, switchUps);
    }

    public static void writeResult(DataOutput out, TrickResult r) throws IOException {
        out.writeInt(r.points());
        int n = Math.min(MAX_LINES, r.lines().size());
        out.writeByte(n);
        for (int i = 0; i < n; i++) {
            TrickResult.Line line = r.lines().get(i);
            String id = line.id().length() > MAX_ID_CHARS ? line.id().substring(0, MAX_ID_CHARS) : line.id();
            out.writeUTF(id);
            out.writeInt(line.points());
        }
        out.writeByte(r.quality().ordinal());
        out.writeFloat((float) r.multiplier());
        out.writeInt(r.comboTotal());
        out.writeShort(Math.min(Short.MAX_VALUE, r.landings()));
        out.writeByte((r.switchLanding() ? 1 : 0) | (r.banked() ? 2 : 0) | (r.bail() ? 4 : 0));
    }

    public static TrickResult readResult(DataInput in) throws IOException {
        int points = in.readInt();
        int n = in.readByte();
        if (n < 0 || n > MAX_LINES) throw new IOException("trick result with " + n + " lines");
        List<TrickResult.Line> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = in.readUTF();
            if (id.length() > MAX_ID_CHARS) throw new IOException("trick id too long");
            lines.add(new TrickResult.Line(id, in.readInt()));
        }
        LandingQuality quality = LandingQuality.byId(in.readByte());
        double multiplier = finite(in.readFloat());
        int total = in.readInt();
        int landings = in.readShort();
        int flags = in.readByte();
        return new TrickResult(points, lines, quality, (flags & 1) != 0, multiplier, total, landings, (flags & 2) != 0, (flags & 4) != 0);
    }

    private static double finite(float f) throws IOException {
        if (!Float.isFinite(f)) throw new IOException("not a finite number");
        return f;
    }
}
