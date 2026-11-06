package io.github.adang101.alpineshred.lift;

/**
 * What commands a lift line (design doc section 13, "Drive"): whether it should run, and how fast.
 * The standalone drive station implements it; phase 9 adds a Create-powered variant. Code that needs
 * a drive's state must go through this interface and never name the implementing class.
 */
public interface LiftDriveSource {
    /** Whether the drive wants the line to run now. */
    boolean enabled();

    /** The speed in blocks per second the drive wants while it is enabled. */
    double targetSpeed();
}
