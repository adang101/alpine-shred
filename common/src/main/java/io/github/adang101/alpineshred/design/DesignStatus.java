package io.github.adang101.alpineshred.design;

import java.util.Locale;

/**
 * {@code s2c/design_result}'s status (design doc section 22, "Network and sync"): what became of a
 * save. Ordinal is the wire value; append only.
 */
public enum DesignStatus {
    OK, TOO_LARGE, INVALID, NO_COST, RATE_LIMITED, DISABLED, FORBIDDEN;

    private static final DesignStatus[] VALUES = values();

    public static DesignStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : INVALID;
    }

    /** Lang key of the bench message. */
    public String translationKey() {
        return "message.alpineshred.design." + name().toLowerCase(Locale.ROOT);
    }
}
