package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.trick.GrabType;

/**
 * One tick of rider input, sampled by the client driver from the keyboard and camera (design doc
 * section 7, "Controls"), or scripted by game tests. Keys mean different things on the ground and
 * in the air; the controller interprets them.
 *
 * @param strafe    A/D: -1 (A, left) to 1 (D, right)
 * @param forward   W/S: 1 (W), 0, -1 (S)
 * @param lookYaw   camera yaw, degrees
 * @param sneak     sneak key: brake on the ground, Method grab in the air
 * @param jump      jump key held (pop charges while held, fires on release)
 * @param grab      grab key held in the air (Z/X/C/V), or {@link GrabType#NONE}
 */
public record RideInput(double strafe, int forward, double lookYaw, boolean sneak, boolean jump, GrabType grab) {
    public static RideInput coast(double lookYaw) {
        return new RideInput(0, 0, lookYaw, false, false, GrabType.NONE);
    }

    public RideInput {
        strafe = Math.max(-1, Math.min(1, strafe));
        forward = Integer.signum(forward);
        if (grab == null) grab = GrabType.NONE;
    }

    /** Inputs during a wipeout: everything released, camera kept. */
    public RideInput locked() {
        return coast(lookYaw);
    }

    public RideInput withJump(boolean held) {
        return new RideInput(strafe, forward, lookYaw, sneak, held, grab);
    }

    /** The ground-riding view of these keys. */
    SlideModel.Input groundInput() {
        return new SlideModel.Input(strafe, lookYaw, forward > 0, forward < 0, sneak);
    }
}
