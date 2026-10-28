package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearStats;

import java.util.function.DoubleUnaryOperator;

/**
 * A one-dimensional stand-in for the world: a rider travelling along +Z (yaw 0) over a height
 * profile, using the real {@link SlideModel}, {@link SlopeMath}, {@link GroundContact} and
 * {@link Ballistics} code in the same order the client controller will. Heights are collision
 * tops, so a voxel staircase is modelled exactly.
 */
final class TrackSim {

    interface Terrain {
        double height(double x);

        /** Exact slope declared by a park ramp at {@code x}, or NaN for ordinary terrain. */
        default double declaredGrade(double x) {
            return Double.NaN;
        }

        default SurfaceProfile surface(double x) {
            return SurfaceProfile.GROOMED;
        }

        static Terrain plane(double degrees, SurfaceProfile surface) {
            double grade = Math.tan(Math.toRadians(degrees));
            return new Terrain() {
                public double height(double x) {
                    return -x * grade;
                }

                public SurfaceProfile surface(double x) {
                    return surface;
                }
            };
        }

        static Terrain staircase(double grade, SurfaceProfile surface) {
            return new Terrain() {
                public double height(double x) {
                    return -Math.floor(x * grade);
                }

                public SurfaceProfile surface(double x) {
                    return surface;
                }
            };
        }

        /** Flat run-in, a park ramp from x = 0 rising {@code grade} per block for {@code length}, then a drop. */
        static Terrain kicker(double grade, double length, double dropDepth) {
            return new Terrain() {
                public double height(double x) {
                    if (x < 0) return 0;
                    if (x <= length) return x * grade;
                    return length * grade - dropDepth;
                }

                public double declaredGrade(double x) {
                    return x >= 0 && x <= length ? grade : Double.NaN;
                }
            };
        }

        static Terrain of(DoubleUnaryOperator h) {
            return h::applyAsDouble;
        }
    }

    final MovementTuning tuning;
    final GearStats gear;
    final Terrain terrain;
    final SlideModel.State state;
    final SlopeMath.Filter filter = new SlopeMath.Filter();

    double x;
    double y;
    double vy;
    boolean grounded = true;

    private final double[] recentClimb = new double[2];
    int ticks;
    int airborneEvents;
    int launches;
    int snaps;
    int airTicks;
    double launchY = Double.NaN;
    double apex = Double.NEGATIVE_INFINITY;
    double distance;

    TrackSim(MovementTuning tuning, GearStats gear, Terrain terrain, double startX, double startSpeed) {
        this.tuning = tuning;
        this.gear = gear;
        this.terrain = terrain;
        this.x = startX;
        this.y = terrain.height(startX);
        this.state = new SlideModel.State(0, startSpeed);
    }

    void tick(SlideModel.Input input) {
        ticks++;
        if (grounded) {
            groundTick(input);
        } else {
            airTick();
        }
    }

    private void groundTick(SlideModel.Input input) {
        double c = SlopeMath.castSample(y, terrain.height(x));
        SlopeMath.Slope raw = SlopeMath.fromHeights(state.travelYaw, c,
                SlopeMath.castSample(y, terrain.height(x + 1)), SlopeMath.castSample(y, terrain.height(x - 1)), c, c, 1.0);
        SlopeMath.Slope slope = filter.update(raw, tuning.gradeSmoothing);
        SlideModel.Conditions conditions = new SlideModel.Conditions(gear, 100, false, terrain.surface(x),
                slope.angle(), slope.downhillYaw(), 1.0);
        SlideModel.stepGround(state, input, conditions, tuning, SlideModel.SteerMode.RIDE);

        double dx = state.speed * SlideModel.dirZ(state.travelYaw);
        double nx = x + dx;
        double ground = terrain.height(nx);
        distance += Math.abs(dx);

        // Collision lifts the rider over anything higher along this tick's path (vanilla move with
        // step-up), so a rider crossing a ramp's lip leaves from the lip, not from below it.
        double peak = y;
        double peakDeclared = Double.NaN;
        for (int i = 1; i <= 8; i++) {
            double px = x + dx * i / 8.0;
            double h = terrain.height(px);
            if (h > peak + 1e-9) {
                peak = h;
                peakDeclared = terrain.declaredGrade(px);
            }
        }
        if (peak > y + 1e-9) {
            double climb = !Double.isNaN(peakDeclared) ? peakDeclared
                    : (Math.abs(dx) > 1e-9 ? (peak - y) / Math.abs(dx) : 0);
            pushClimb(climb);
            y = peak;
        } else if (ground >= y - 1e-9) {
            pushClimb(0);
        }
        if (ground >= y - 1e-9) {
            y = ground;
            x = nx;
            return;
        }
        double gap = y - ground;
        double recent = Math.max(recentClimb[0], recentClimb[1]);
        GroundContact.Result r = GroundContact.resolve(tuning, true, false, 0, gap, recent, state.speed);
        x = nx;
        switch (r.kind()) {
            case GROUNDED, SNAP -> {
                y = ground;
                snaps++;
                pushClimb(0);
            }
            case LAUNCH -> {
                grounded = false;
                vy = r.launchVy();
                launches++;
                startAir();
            }
            case AIRBORNE -> {
                grounded = false;
                vy = 0;
                airborneEvents++;
                startAir();
            }
        }
    }

    private void startAir() {
        launchY = y;
        apex = y;
        airTicks = 0;
    }

    private void airTick() {
        x += state.speed * SlideModel.dirZ(state.travelYaw);
        y += vy;
        vy = Ballistics.nextVerticalVelocity(vy);
        airTicks++;
        apex = Math.max(apex, y);
        double ground = terrain.height(x);
        if (y <= ground) {
            y = ground;
            vy = 0;
            grounded = true;
            recentClimb[0] = recentClimb[1] = 0;
        }
    }

    private void pushClimb(double grade) {
        recentClimb[0] = recentClimb[1];
        recentClimb[1] = grade;
    }

    double apexAboveLaunch() {
        return apex - launchY;
    }
}
