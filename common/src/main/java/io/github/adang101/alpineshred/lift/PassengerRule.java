package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Who may sit on a lift chair (design doc section 13). The rule itself is pure: players always; a type
 * on {@code #alpineshred:lift_passengers} always; otherwise a living mob when mobs are allowed, it is not
 * on {@code #alpineshred:lift_blocked} and it fits the size limits. {@link #allows(Entity)} reads the
 * tags and the config. Seat arithmetic is here too, so it can be tested without a world.
 */
public final class PassengerRule {
    /** Distance between neighbouring seats, in blocks. */
    public static final double SEAT_SPACING = 0.9;

    private PassengerRule() {
    }

    public static boolean allows(boolean isPlayer, boolean isLiving, double width, double height,
                                 boolean inBlockedTag, boolean inPassengersTag,
                                 boolean mobsAllowed, double maxWidth, double maxHeight) {
        if (isPlayer) return true;
        if (inPassengersTag) return true;
        if (!isLiving || !mobsAllowed || inBlockedTag) return false;
        return width <= maxWidth && height <= maxHeight;
    }

    public static boolean allows(Entity entity) {
        return allows(entity instanceof Player, entity instanceof LivingEntity,
                entity.getBbWidth(), entity.getBbHeight(),
                entity.getType().is(ModTags.EntityTypes.LIFT_BLOCKED),
                entity.getType().is(ModTags.EntityTypes.LIFT_PASSENGERS),
                AlpineConfig.LIFT_MOBS_ALLOWED.get(),
                AlpineConfig.LIFT_MAX_PASSENGER_WIDTH.get(), AlpineConfig.LIFT_MAX_PASSENGER_HEIGHT.get());
    }

    /** Whether a chair with {@code passengers} aboard has a free seat out of {@code seats}. */
    public static boolean hasFreeSeat(int passengers, int seats) {
        return passengers < seats;
    }

    /** Sideways offset of seat {@code index} out of {@code seats}, centred on the chair: +-0.45 for two. */
    public static double seatOffset(int index, int seats) {
        return (index - (seats - 1) / 2.0) * SEAT_SPACING;
    }
}
