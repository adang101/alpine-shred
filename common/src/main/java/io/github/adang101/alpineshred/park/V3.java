package io.github.adang101.alpineshred.park;

/** A point or vector in block space, for the park geometry (no Minecraft types, so it unit-tests). */
public record V3(double x, double y, double z) {
    public static final V3 ZERO = new V3(0, 0, 0);

    public V3 add(V3 o) {
        return new V3(x + o.x, y + o.y, z + o.z);
    }

    public V3 add(double dx, double dy, double dz) {
        return new V3(x + dx, y + dy, z + dz);
    }

    public V3 sub(V3 o) {
        return new V3(x - o.x, y - o.y, z - o.z);
    }

    public V3 scale(double k) {
        return new V3(x * k, y * k, z * k);
    }

    public double dot(V3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double horizontalLength() {
        return Math.hypot(x, z);
    }

    public V3 normalize() {
        double l = length();
        return l < 1.0e-12 ? ZERO : scale(1 / l);
    }

    public double distance(V3 o) {
        return sub(o).length();
    }

    public double horizontalDistance(V3 o) {
        return Math.hypot(x - o.x, z - o.z);
    }

    /** Linear interpolation from this to {@code o}. */
    public V3 lerp(V3 o, double t) {
        return new V3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }
}
