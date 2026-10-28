package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearStats;

/**
 * Everything about the rider's setup the controller reads each tick: tuning, gear and the server
 * settings that apply to riding.
 *
 * @param edge          stack {@code Edge}, 0 to 100
 * @param waxed         stack has {@code Wax} left
 * @param speedCapMul   server {@code movement.maxSpeedMul}
 * @param scrapeEnabled server {@code movement.scrapeEnabled}
 */
public record RideContext(MovementTuning tuning, GearStats gear, int edge, boolean waxed,
                          double speedCapMul, SlideModel.SteerMode steerMode, boolean scrapeEnabled) {

    public GearKind kind() {
        return gear.kind();
    }

    public SlideModel.Conditions conditions(SurfaceProfile surface, SlopeMath.Slope slope) {
        return new SlideModel.Conditions(gear, edge, waxed, surface, slope.angle(), slope.downhillYaw(), speedCapMul);
    }

    public static RideContext defaults(GearStats gear) {
        return new RideContext(MovementTuning.DEFAULT, gear, 100, false, 1.0, SlideModel.SteerMode.RIDE, true);
    }
}
