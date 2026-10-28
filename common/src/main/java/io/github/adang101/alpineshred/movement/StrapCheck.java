package io.github.adang101.alpineshred.movement;

import dev.architectury.platform.Platform;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearProfile;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side strap-in preconditions (design doc section 6, "Strap in and out"): board or skis
 * equipped, compatible boots worn, not mounted, not in a fluid, not elytra-flying, not spectator.
 */
public final class StrapCheck {
    private StrapCheck() {
    }

    public enum Result {
        OK("in"),
        DISABLED("disabled"),
        SPECTATOR("spectator"),
        VEHICLE("vehicle"),
        FLUID("fluid"),
        ELYTRA("elytra"),
        NO_GEAR("no_gear"),
        BOOTS_BOARD("boots_board"),
        BOOTS_SKIS("boots_skis");

        private final String key;

        Result(String key) {
            this.key = key;
        }

        public boolean ok() {
            return this == OK;
        }

        /** Action-bar message, {@code message.alpineshred.strap.<key>}. */
        public String translationKey() {
            return "message.alpineshred.strap." + key;
        }
    }

    public static Result check(Player player, GearProfile gear) {
        if (!ridingEnabled()) return Result.DISABLED;
        if (player.isSpectator()) return Result.SPECTATOR;
        // Lift chairs (phase 8) will be allowed here.
        if (player.isPassenger()) return Result.VEHICLE;
        if (player.isInWater() || player.isInLava()) return Result.FLUID;
        if (player.isFallFlying()) return Result.ELYTRA;
        if (!gear.hasGear()) return Result.NO_GEAR;
        if (!gear.bootsMatch()) return gear.kind() == GearKind.BOARD ? Result.BOOTS_BOARD : Result.BOOTS_SKIS;
        return Result.OK;
    }

    /** {@code movement.enabled}, and none of {@code movement.disableWithMods} is loaded. */
    public static boolean ridingEnabled() {
        if (!AlpineConfig.MOVEMENT_ENABLED.on()) return false;
        for (String modId : AlpineConfig.MOVEMENT_DISABLE_WITH_MODS.get()) {
            if (Platform.isModLoaded(modId)) return false;
        }
        return true;
    }
}
