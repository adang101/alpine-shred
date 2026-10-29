package io.github.adang101.alpineshred.trick.advancement;

import net.minecraft.advancements.CriteriaTriggers;

/** The mod's advancement triggers, registered in common init (section 8). Lift rides join in phase 8. */
public final class ModTriggers {
    public static final TrickLandedTrigger TRICK_LANDED = CriteriaTriggers.register(new TrickLandedTrigger());
    public static final GrindCompletedTrigger GRIND_COMPLETED = CriteriaTriggers.register(new GrindCompletedTrigger());

    private ModTriggers() {
    }

    /** Loads the class, which registers the triggers. */
    public static void register() {
    }
}
