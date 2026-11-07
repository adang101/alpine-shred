package io.github.adang101.alpineshred.compat.create;

/**
 * Which rule Create's wrench follows on which kind of block, with or without sneaking (phase 9 plan, WP4;
 * README 80). Pure, so the whole table is a unit test; {@link WrenchActions} supplies the kind and acts on the answer.
 */
public final class WrenchRule {
    /** What a clicked block is, as far as the wrench cares. */
    public enum Kind {
        /** A rail, ramp, box, coping or decor piece: what the park wrench changes. */
        PARK,
        /** A lift block in the wrench-pickup tag (pole, head, station, kinetic drive). */
        LIFT,
        /** Anything else: Create's own behaviour carries on. */
        OTHER
    }

    /** What the click does. */
    public enum Action {
        /** Run the park wrench's own click (cycle the piece). */
        PARK_CLICK,
        /** Take the block, as Create does for its own. */
        PICK_UP,
        /** Do nothing but say to use the lift linker. */
        LIFT_HINT,
        /** Not ours: leave the click alone. */
        PASS
    }

    private WrenchRule() {
    }

    /** Plain click: the block's own tool; sneak-click picks up, like Create's blocks. Park turning stays on the park wrench. */
    public static Action decide(Kind kind, boolean sneaking) {
        return switch (kind) {
            case PARK -> sneaking ? Action.PICK_UP : Action.PARK_CLICK;
            case LIFT -> sneaking ? Action.PICK_UP : Action.LIFT_HINT;
            case OTHER -> Action.PASS;
        };
    }
}
