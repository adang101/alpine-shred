package io.github.adang101.alpineshred.client.lift;

import io.github.adang101.alpineshred.lift.LiftLine;
import io.github.adang101.alpineshred.lift.LiftLineSync;
import io.github.adang101.alpineshred.lift.LiftMotion;
import io.github.adang101.alpineshred.lift.LiftPath;
import io.github.adang101.alpineshred.lift.LiftStations;
import io.github.adang101.alpineshred.lift.LineView;
import io.github.adang101.alpineshred.park.V3;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A client's copy of a lift line: the path built from the synced nodes, the chair count from the
 * synced spacing, and the motion state. It uses no client-only classes, so game tests build it
 * directly. The path is reused while the nodes are unchanged.
 */
public final class ClientLine implements LineView {
    public final UUID id;
    private List<BlockPos> nodes = List.of();
    private LiftPath path;
    private int chairCount;
    private LiftStations.Result stations;

    public double travel;
    public double speed;
    public double targetSpeed;
    public boolean powered;
    public boolean faulted;
    public boolean emergency;

    /**
     * Blocks from {@link #travel}, which follows the server's clock, to the distance drawn (README clarification
     * 100). A clock jump or a correcting sync moves {@code travel} at once; the drawn distance would hop with it,
     * every chair, the cable and the bullwheels together, so the difference is kept here and worked off by
     * {@link #tick} at a bounded rate. Negative: the drawn distance trails the server's.
     */
    private double offset;
    private boolean placed;

    /** Builds the line from a (non-removed) sync and catches it up to {@code now}. */
    public ClientLine(LiftLineSync sync, long now) {
        this.id = sync.id();
        apply(sync, now);
    }

    /**
     * The most ticks a sync from the client's future is rewound by. A client whose game time lags the server's
     * by a few ticks would otherwise stay ahead of the server's chairs until the next sync; a larger gap is a
     * clock that is wrong, and the state is taken as it is.
     */
    public static final long MAX_REWIND = 10;

    /** The drawn distance works off at least this fraction of a tick's travel each tick (a 15% speed change)... */
    static final double SLEW_FRACTION = 0.15;
    /** ...or this fraction of the offset, so a large one does not take long (a 0.375 offset is under 0.25 in 2 ticks)... */
    static final double OFFSET_FRACTION = 0.25;
    /** ...but never more than this fraction of a tick's travel, so the drawn distance keeps running forward. */
    static final double MAX_SLEW_FRACTION = 0.75;
    /** ...and by at least this many blocks a tick, so a stopped line still settles (0.2 blocks a second). */
    static final double MIN_SLEW = 0.01;
    /** A difference above this many blocks (16 ticks at 2.5 blocks a second) is a stall, not drift: the drawn distance snaps. */
    static final double MAX_OFFSET = 2.0;

    /**
     * Takes the packet's state, then runs it forward from the packet's game time to {@code now} with
     * {@link LiftMotion#advance}. A packet stamped a few ticks after {@code now} (see {@link #MAX_REWIND})
     * is rewound at its own speed instead, so the line is where the server's was at the client's game time.
     */
    public void apply(LiftLineSync sync, long now) {
        double shown = shownTravel();
        boolean rebuilt = path == null || !nodes.equals(sync.nodes());
        if (rebuilt) {
            List<V3> v = new ArrayList<>(sync.nodes().size());
            for (BlockPos p : sync.nodes()) v.add(LiftLine.node(p));
            path = LiftPath.of(v);
            nodes = List.copyOf(sync.nodes());
            stations = LiftStations.compute(path, nodes.get(0).getY());
        }
        chairCount = LiftPath.chairCount(path.loopLength(), sync.spacing());
        travel = sync.travel();
        speed = sync.speed();
        targetSpeed = sync.targetSpeed();
        powered = sync.powered();
        faulted = sync.faulted();
        emergency = sync.emergency();
        long gap = now - sync.gameTime();
        if (gap > 0) advance(gap);
        else if (gap < 0 && gap >= -MAX_REWIND) rewind(-gap);
        absorb(shown, rebuilt);
    }

    /** The drawn distance before a change to {@link #travel}: that change then leaves it where it was. */
    private double shownTravel() {
        return placed ? travel + offset : 0;
    }

    /** Keeps the drawn distance at {@code shown} after {@link #travel} moved, up to {@link #MAX_OFFSET}. */
    private void absorb(double shown, boolean reset) {
        if (!placed || reset) {
            placed = true;
            offset = 0;
            return;
        }
        double d = shown - travel;
        double loop = path.loopLength();
        if (loop > 0) {
            d %= loop;
            if (d > loop / 2) d -= loop;
            else if (d < -loop / 2) d += loop;
        }
        offset = Math.abs(d) > MAX_OFFSET ? 0 : d;
    }

    /** Moves {@code travel} back by {@code ticks} ticks at the current speed. */
    private void rewind(long ticks) {
        double t = travel - speed / LiftMotion.TICKS_PER_SECOND * ticks;
        double loop = path.loopLength();
        if (loop > 0) {
            t %= loop;
            if (t < 0) t += loop;
        }
        travel = t;
    }

    /** {@code ticks} ticks of this line, as the server's manager runs them. */
    public void advance(long ticks) {
        double[] next = LiftMotion.advance(speed, travel, target(), LiftMotion.isStopping(faulted, emergency),
                path.loopLength(), ticks);
        speed = next[0];
        travel = next[1];
    }

    /**
     * The level's game time moved by {@code ticks} since the last client tick (one, normally; the server's
     * time packet moves it by the amount the client had drifted): runs the line forward by that many ticks, or
     * back by up to {@link #MAX_REWIND}, so the line stays where the server's was at the client's game time. The
     * drawn distance does not follow the jump: it moves {@code drawnTicks} ticks and the rest becomes the offset.
     */
    public void shift(long ticks, long drawnTicks) {
        // The drawn distance moves on by the ticks the client really ran (one for a client tick, none before a sync).
        double shown = shownTravel() + speed / LiftMotion.TICKS_PER_SECOND * drawnTicks;
        if (ticks > 0) advance(ticks);
        else if (ticks < 0 && ticks >= -MAX_REWIND) rewind(-ticks);
        absorb(shown, false);
    }

    /** One client tick. */
    public void tick() {
        double after = LiftMotion.nextSpeed(speed, target(), LiftMotion.isStopping(faulted, emergency));
        travel = LiftMotion.nextTravel(travel, after, path.loopLength());
        speed = after;
        if (offset != 0) {
            double stepLength = Math.abs(after) / LiftMotion.TICKS_PER_SECOND;
            double slew = Math.max(Math.min(Math.max(SLEW_FRACTION * stepLength, OFFSET_FRACTION * Math.abs(offset)),
                    MAX_SLEW_FRACTION * stepLength), MIN_SLEW);
            offset = Math.abs(offset) <= slew ? 0 : offset - Math.copySign(slew, offset);
        }
    }

    private double target() {
        return LiftMotion.targetFor(powered, faulted, emergency, targetSpeed);
    }

    /** The node positions, drive first, as last synced. */
    public List<BlockPos> nodes() {
        return nodes;
    }

    /** The loop distance run {@code partialTick} of a tick after the last client tick, for drawing between ticks. */
    public double travelAt(float partialTick) {
        return travel() + speed / 20.0 * partialTick;
    }

    @Override
    public LiftPath path() {
        return path;
    }

    /** The distance drawn this tick: {@link #travel} plus the offset still being worked off, on the loop. */
    @Override
    public double travel() {
        if (offset == 0) return travel;
        double t = travel + offset;
        double loop = path.loopLength();
        if (loop > 0) {
            t %= loop;
            if (t < 0) t += loop;
        }
        return t;
    }

    @Override
    public int chairCount() {
        return chairCount;
    }

    @Override
    public LiftStations.Result stations() {
        return stations;
    }

    @Override
    public double speed() {
        return speed;
    }

    @Override
    public boolean faulted() {
        return faulted;
    }
}
