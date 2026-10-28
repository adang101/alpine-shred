package io.github.adang101.alpineshred.gear;

import io.github.adang101.alpineshred.movement.RideMode;

/** Boards and skis ride differently (design doc section 7, "Board and skis differ"). */
public enum GearKind {
    BOARD("board", RideMode.BOARD),
    SKIS("skis", RideMode.SKIS);

    private final String serializedName;
    private final RideMode rideMode;

    GearKind(String serializedName, RideMode rideMode) {
        this.serializedName = serializedName;
        this.rideMode = rideMode;
    }

    public String serializedName() {
        return serializedName;
    }

    public RideMode rideMode() {
        return rideMode;
    }

    public static GearKind bySerializedName(String name) {
        for (GearKind kind : values()) {
            if (kind.serializedName.equalsIgnoreCase(name) || kind.name().equalsIgnoreCase(name)) {
                return kind;
            }
        }
        // The design doc writes "ski" in the Design NBT and "skis" in tables; accept both.
        if ("ski".equalsIgnoreCase(name)) return SKIS;
        return null;
    }
}
