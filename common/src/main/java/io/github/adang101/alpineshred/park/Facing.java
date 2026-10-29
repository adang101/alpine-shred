package io.github.adang101.alpineshred.park;

/**
 * The four horizontal directions, in Minecraft's terms (north is -z, east is +x), with the block
 * rotation the park pieces use: a piece is authored facing north and turned clockwise, seen from
 * above, the same way a blockstate {@code y} rotation turns its model.
 */
public enum Facing {
    NORTH(0, -1, 180), EAST(1, 0, -90), SOUTH(0, 1, 0), WEST(-1, 0, 90);

    public final int dx;
    public final int dz;
    /** Minecraft yaw looking this way. */
    public final float yaw;

    Facing(int dx, int dz, float yaw) {
        this.dx = dx;
        this.dz = dz;
        this.yaw = yaw;
    }

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public Facing opposite() {
        return values()[(ordinal() + 2) % 4];
    }

    public Facing clockwise() {
        return values()[(ordinal() + 1) % 4];
    }

    public Facing counterClockwise() {
        return values()[(ordinal() + 3) % 4];
    }

    /** Degrees of clockwise turn from north: 0, 90, 180, 270 (the blockstate {@code y}). */
    public int rotation() {
        return ordinal() * 90;
    }

    public V3 vector() {
        return new V3(dx, 0, dz);
    }

    /** Turns a block-local point authored for north into this facing, about the block center. */
    public V3 rotateLocal(V3 p) {
        return switch (this) {
            case NORTH -> p;
            case EAST -> new V3(1 - p.z(), p.y(), p.x());
            case SOUTH -> new V3(1 - p.x(), p.y(), 1 - p.z());
            case WEST -> new V3(p.z(), p.y(), 1 - p.x());
        };
    }

    /** Turns a direction authored for north into this facing. */
    public V3 rotateVector(V3 v) {
        return switch (this) {
            case NORTH -> v;
            case EAST -> new V3(-v.z(), v.y(), v.x());
            case SOUTH -> new V3(-v.x(), v.y(), -v.z());
            case WEST -> new V3(v.z(), v.y(), -v.x());
        };
    }

    /** The facing nearest a Minecraft yaw. */
    public static Facing fromYaw(double yaw) {
        double d = ((yaw % 360) + 360) % 360; // 0 south, 90 west, 180 north, 270 east
        int quadrant = (int) Math.floor((d + 45) / 90) % 4;
        return switch (quadrant) {
            case 0 -> SOUTH;
            case 1 -> WEST;
            case 2 -> NORTH;
            default -> EAST;
        };
    }

    public static Facing byName(String name) {
        for (Facing f : values()) {
            if (f.serializedName().equals(name)) return f;
        }
        return null;
    }
}
