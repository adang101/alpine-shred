package io.github.adang101.alpineshred.gear;

/**
 * Wear rules for boards and skis (design doc section 5, "Item state" and "Wear model").
 * Pure math plus a small accumulator, so the server can recompute wear from position deltas.
 */
public final class GearWear {
    public static final int MAX_EDGE = 100;
    /** {@code board_wax} sets the counter to this many blocks of waxed sliding. */
    public static final int WAX_BLOCKS = 3000;

    public static final double BLOCKS_PER_DAMAGE_SNOW = 128.0;
    public static final double BLOCKS_PER_DAMAGE_GRIND = 16.0;
    public static final double BLOCKS_PER_DAMAGE_SCRAPE = 4.0;
    public static final int DAMAGE_PER_BAIL = 3;
    public static final double CARVE_BLOCKS_PER_EDGE = 40.0;
    /** Floating-point slack so 100 x 0.3 blocks counts as 30, not 29. */
    private static final double EPSILON = 1.0e-9;

    private GearWear() {
    }

    /** Grip multiplier from edge sharpness: {@code 0.85 + 0.15 * Edge / 100}. */
    public static double edgeGripMultiplier(int edge) {
        int clamped = Math.max(0, Math.min(MAX_EDGE, edge));
        return 0.85 + 0.15 * clamped / MAX_EDGE;
    }

    /** Where the blocks were travelled, which decides the wear rate. */
    public enum Contact {
        SNOW, GRIND, SCRAPE, AIR
    }

    /**
     * Fractional distance accumulators for one rider. Call {@link #travel} with each tick's
     * distance and read back whole units of wear; leftovers carry over to the next tick.
     */
    public static final class Accumulator {
        private double durabilityBlocks;
        private double durabilityUnitBlocks = BLOCKS_PER_DAMAGE_SNOW;
        private double carveBlocks;
        private double waxBlocks;

        /** Result of one {@link #travel} call. All values are whole units to apply to the stack. */
        public record Wear(int durabilityDamage, int edgeLoss, int waxUsed) {
            public static final Wear NONE = new Wear(0, 0, 0);

            public boolean isEmpty() {
                return durabilityDamage == 0 && edgeLoss == 0 && waxUsed == 0;
            }
        }

        /**
         * @param blocks           horizontal distance this tick
         * @param contact          surface class this tick
         * @param carving          true while an edge is engaged (CARVE state)
         * @param waxed            true while the stack has wax left
         * @param durabilityMul    server {@code gear.durabilityMul}; 0 disables durability wear
         */
        public Wear travel(double blocks, Contact contact, boolean carving, boolean waxed, double durabilityMul) {
            if (!(blocks > 0) || contact == Contact.AIR) return Wear.NONE;

            int damage = 0;
            if (durabilityMul > 0) {
                double unit = switch (contact) {
                    case SNOW -> BLOCKS_PER_DAMAGE_SNOW;
                    case GRIND -> BLOCKS_PER_DAMAGE_GRIND;
                    case SCRAPE -> BLOCKS_PER_DAMAGE_SCRAPE;
                    case AIR -> Double.POSITIVE_INFINITY;
                } / durabilityMul;
                // Distance is tracked in "damage units" so switching surfaces mid-run neither loses
                // nor double-counts the partial unit already ridden.
                if (unit != durabilityUnitBlocks) {
                    durabilityBlocks = durabilityBlocks / durabilityUnitBlocks * unit;
                    durabilityUnitBlocks = unit;
                }
                durabilityBlocks += blocks;
                damage = (int) Math.floor(durabilityBlocks / unit + EPSILON);
                durabilityBlocks -= damage * unit;
            }

            int edge = 0;
            if (carving && contact == Contact.SNOW) {
                carveBlocks += blocks;
                edge = (int) Math.floor(carveBlocks / CARVE_BLOCKS_PER_EDGE + EPSILON);
                carveBlocks -= edge * CARVE_BLOCKS_PER_EDGE;
            }

            int wax = 0;
            if (waxed && contact == Contact.SNOW) {
                waxBlocks += blocks;
                wax = (int) Math.floor(waxBlocks + EPSILON);
                waxBlocks -= wax;
            }

            return damage == 0 && edge == 0 && wax == 0 ? Wear.NONE : new Wear(damage, edge, wax);
        }

        /** Damage for a bail, scaled by the server multiplier and rounded to at least 1 when enabled. */
        public static int bailDamage(double durabilityMul) {
            if (durabilityMul <= 0) return 0;
            return Math.max(1, (int) Math.round(DAMAGE_PER_BAIL * durabilityMul));
        }

        public void reset() {
            durabilityBlocks = 0;
            durabilityUnitBlocks = BLOCKS_PER_DAMAGE_SNOW;
            carveBlocks = 0;
            waxBlocks = 0;
        }
    }
}
