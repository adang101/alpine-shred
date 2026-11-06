package io.github.adang101.alpineshred.lift;

/**
 * May a rider leave a chair by sneaking (design doc section 13, "Unloading and dismount")? Yes when the
 * bar is open, the chair is within {@link #MAX_HEIGHT} blocks of the ground, the line is faulted, or
 * {@code lift.dismountAnywhere} is on. Pure; the same call is made on client and server.
 */
public final class DismountRule {
    public static final double MAX_HEIGHT = 4.0;

    private DismountRule() {
    }

    public static boolean allowed(boolean barDown, double heightAboveGround, boolean faulted, boolean dismountAnywhere) {
        return dismountAnywhere || !barDown || faulted || heightAboveGround <= MAX_HEIGHT;
    }
}
