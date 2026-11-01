package io.github.adang101.alpineshred.client.lift;

import io.github.adang101.alpineshred.lift.LiftLine;
import io.github.adang101.alpineshred.lift.LiftLineSync;
import io.github.adang101.alpineshred.lift.LiftMotion;
import io.github.adang101.alpineshred.lift.LiftPath;
import io.github.adang101.alpineshred.lift.LineView;
import io.github.adang101.alpineshred.park.V3;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A client's copy of a lift line: the path built from the synced nodes, the chair count from the
 * synced spacing, and the motion state. It uses no client-only classes, so game tests build it
 * directly. The path is reused while the nodes are unchanged.
 */
public final class ClientLine implements LineView {
    private List<BlockPos> nodes = List.of();
    private LiftPath path;
    private int chairCount;

    public double travel;
    public double speed;
    public double targetSpeed;
    public boolean powered;
    public boolean faulted;
    public boolean emergency;

    /** Builds the line from a (non-removed) sync and catches it up to {@code now}. */
    public ClientLine(LiftLineSync sync, long now) {
        apply(sync, now);
    }

    /**
     * Takes the packet's state, then runs it forward from the packet's game time to {@code now} with
     * {@link LiftMotion#advance}, never by a negative number of ticks.
     */
    public void apply(LiftLineSync sync, long now) {
        if (path == null || !nodes.equals(sync.nodes())) {
            List<V3> v = new ArrayList<>(sync.nodes().size());
            for (BlockPos p : sync.nodes()) v.add(LiftLine.node(p));
            path = LiftPath.of(v);
            nodes = List.copyOf(sync.nodes());
        }
        chairCount = LiftPath.chairCount(path.loopLength(), sync.spacing());
        travel = sync.travel();
        speed = sync.speed();
        targetSpeed = sync.targetSpeed();
        powered = sync.powered();
        faulted = sync.faulted();
        emergency = sync.emergency();
        long ticks = Math.max(0, now - sync.gameTime());
        if (ticks > 0) advance(ticks);
    }

    /** {@code ticks} ticks of this line, as the server's manager runs them. */
    public void advance(long ticks) {
        double[] next = LiftMotion.advance(speed, travel, target(), LiftMotion.isStopping(faulted, emergency),
                path.loopLength(), ticks);
        speed = next[0];
        travel = next[1];
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
}
