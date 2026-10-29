package io.github.adang101.alpineshred.client.feel;

/**
 * Counters of what the feel systems actually did, for the client smoke test and the debug overlay.
 * Cheap enough to keep always on.
 */
public final class FeelStats {
    public static int clipsPlayed;
    public static int loopsStarted;
    public static int oneShotsPlayed;
    public static int particlesSpawned;
    public static int polePushes;
    public static float maxFovAdded;
    public static float maxRoll;

    private FeelStats() {
    }

    public static String summary() {
        return "clips=" + clipsPlayed + " loops=" + loopsStarted + " oneShots=" + oneShotsPlayed
                 + " particles=" + particlesSpawned + " polePushes=" + polePushes + " maxFov=" + maxFovAdded + " maxRoll=" + maxRoll;
    }
}
