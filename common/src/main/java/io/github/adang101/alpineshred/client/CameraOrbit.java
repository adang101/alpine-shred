package io.github.adang101.alpineshred.client;

/**
 * Swings the third-person camera around the player by a fixed yaw, for scripted screenshots: the
 * client smoke test uses it to photograph riders from the side, where the board reads lengthwise.
 * Off unless something calls {@link #set}; the camera is vanilla while it is off.
 */
public final class CameraOrbit {
    private static boolean active;
    private static float yawOffset;
    private static float pitch;
    private static float distance = 4f;

    private CameraOrbit() {
    }

    /**
     * @param yawOffset degrees added to the player's look yaw; 0 is vanilla's behind-the-back view,
     *                  -90 looks at a regular snowboarder's chest
     * @param pitch     camera pitch, degrees (positive looks down)
     * @param distance  blocks from the eyes, before vanilla's wall clipping
     */
    public static void set(float yawOffset, float pitch, float distance) {
        CameraOrbit.yawOffset = yawOffset;
        CameraOrbit.pitch = pitch;
        CameraOrbit.distance = distance;
        active = true;
    }

    public static void clear() {
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static float yawOffset() {
        return yawOffset;
    }

    public static float pitch() {
        return pitch;
    }

    public static float distance() {
        return distance;
    }
}
