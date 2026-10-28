package io.github.adang101.alpineshred.movement;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Read and write the synced rider state of a player. */
public final class Riders {
    private Riders() {
    }

    public static RideSnapshot get(Player player) {
        return RideSnapshot.unpack(holder(player).alpineshred$getRideBits());
    }

    public static RideState state(Player player) {
        return RideSnapshot.stateOf(holder(player).alpineshred$getRideBits());
    }

    public static boolean isStrapped(Player player) {
        return state(player).isStrapped();
    }

    /** True for strapped players; false for any other entity. */
    public static boolean isStrapped(Entity entity) {
        return entity instanceof Player player && isStrapped(player);
    }

    /** Server side this syncs to every tracking client; client side it is local until corrected. */
    public static void set(Player player, RideSnapshot snapshot) {
        holder(player).alpineshred$setRideBits(snapshot.pack());
    }

    private static RideStateHolder holder(Player player) {
        return (RideStateHolder) player;
    }
}
