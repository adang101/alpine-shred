package io.github.adang101.alpineshred.movement;

/**
 * A minimal stand-in for a Minecraft player in a world of block columns: each integer (x, z) column
 * has a collision top and a surface. Movement collides with columns taller than one step, steps up
 * onto shorter ones while grounded, and lands on tops when falling. Enough to exercise every
 * branch of {@link SlideController} without Minecraft.
 */
final class VoxelBody implements SlideController.Body {
    interface Terrain {
        double top(int x, int z);

        SurfaceProfile surface(int x, int z);

        static Terrain flat(SurfaceProfile surface) {
            return of((x, z) -> 0, surface);
        }

        static Terrain of(java.util.function.IntBinaryOperator topFn, SurfaceProfile surface) {
            return of((x, z) -> (double) topFn.applyAsInt(x, z), (x, z) -> surface);
        }

        static Terrain of(java.util.function.BiFunction<Integer, Integer, Double> topFn,
                          java.util.function.BiFunction<Integer, Integer, SurfaceProfile> surfaceFn) {
            return new Terrain() {
                public double top(int x, int z) {
                    return topFn.apply(x, z);
                }

                public SurfaceProfile surface(int x, int z) {
                    return surfaceFn.apply(x, z);
                }
            };
        }
    }

    final Terrain terrain;
    double x, y, z;
    double vx, vz;
    boolean onGround = true;
    double stepUp = 1.0;

    VoxelBody(Terrain terrain, double x, double z) {
        this.terrain = terrain;
        this.x = x;
        this.z = z;
        this.y = top(x, z);
    }

    double top(double px, double pz) {
        return terrain.top((int) Math.floor(px), (int) Math.floor(pz));
    }

    @Override
    public double x() {
        return x;
    }

    @Override
    public double y() {
        return y;
    }

    @Override
    public double z() {
        return z;
    }

    @Override
    public double velocityX() {
        return vx;
    }

    @Override
    public double velocityZ() {
        return vz;
    }

    @Override
    public boolean onGround() {
        return onGround;
    }

    @Override
    public SlideController.Moved move(double dx, double dy, double dz) {
        double sx = x, sy = y, sz = z;
        boolean wall = false;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / 0.1));
        double px = x, pz = z;
        for (int i = 1; i <= steps; i++) {
            double cx = x + dx * i / steps, cz = z + dz * i / steps;
            double t = top(cx, cz);
            if (t > y + 1.0e-9) {
                if (onGround && t <= y + stepUp + 1.0e-9) {
                    y = t;
                } else {
                    wall = true;
                    break;
                }
            }
            px = cx;
            pz = cz;
        }
        x = px;
        z = pz;
        double ground = top(x, z);
        double ny = y + dy;
        boolean landed = false;
        if (ny <= ground) {
            ny = ground;
            landed = dy <= 0;
        }
        y = ny;
        onGround = landed;
        vx = x - sx;
        vz = z - sz;
        return new SlideController.Moved(x - sx, y - sy, z - sz, landed, wall);
    }

    @Override
    public void snapDown(double dy) {
        y -= dy;
        onGround = true;
    }

    @Override
    public double groundTop(double px, double pz, double fromY, double toY) {
        double t = top(px, pz);
        return t <= fromY && t >= toY ? t : Double.NaN;
    }

    @Override
    public SurfaceProfile surfaceUnderFeet() {
        if (y - top(x, z) > 0.01) return null;
        return terrain.surface((int) Math.floor(x), (int) Math.floor(z));
    }
}
