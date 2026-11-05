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

    /**
     * Takes the packet's state, then runs it forward from the packet's game time to {@code now} with
     * {@link LiftMotion#advance}. A packet stamped a few ticks after {@code now} (see {@link #MAX_REWIND})
     * is rewound at its own speed instead, so the line is where the server's was at the client's game time.
     */
    public void apply(LiftLineSync sync, long now) {
        if (path == null || !nodes.equals(sync.nodes())) {
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
     * back by up to {@link #MAX_REWIND}, so the line stays where the server's was at the client's game time.
     */
    public void shift(long ticks) {
        if (ticks > 0) advance(ticks);
        else if (ticks < 0 && ticks >= -MAX_REWIND) rewind(-ticks);
    }

    /** One client tick. */
    public void tick() {
        double[] next = LiftMotion.step(speed, travel, target(), LiftMotion.isStopping(faulted, emergency),
                path.loopLength());
        speed = next[0];
        travel = next[1];
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
        return travel + speed / 20.0 * partialTick;
    }

    @Override
    public LiftPath path() {
        return path;
    }

    @Override
    public double travel() {
        return travel;
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
