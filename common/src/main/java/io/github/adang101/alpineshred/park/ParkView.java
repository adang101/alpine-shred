package io.github.adang101.alpineshred.park;

import java.util.List;

/**
 * What the slide controller can ask the world about park features around a rider: grindable paths,
 * quarter pipes, and lip ramps. The game answers from blocks; tests answer from lists.
 */
public interface ParkView {
    ParkView NONE = new ParkView() {
        @Override
        public GrindPhysics.Lookup grinds() {
            return p -> List.of();
        }

        @Override
        public PipeSection pipeNear(V3 feet) {
            return null;
        }

        @Override
        public boolean lipNear(V3 feet) {
            return false;
        }
    };

    /** Grind paths around a point. */
    GrindPhysics.Lookup grinds();

    /** The quarter pipe whose arc the rider at {@code feet} is over or on, or null. */
    PipeSection pipeNear(V3 feet);

    /** True if the ground at or just behind {@code feet} is a ramp marked as a lip. */
    boolean lipNear(V3 feet);
}
