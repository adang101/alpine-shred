package io.github.adang101.alpineshred.gear;

import io.github.adang101.alpineshred.platform.PlatformHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The one gear abstraction (design doc section 3, rule 3, and section 6). Forge implements it over
 * Curios, Fabric over Trinkets. Nothing else in the mod touches either library.
 */
public interface GearAccess {

    /** The accessory slots this mod owns or uses. */
    enum Slot {
        /** Curios {@code snow_gear}, Trinkets {@code feet/snow_gear}. */
        BOARD,
        /** Curios {@code goggles}, Trinkets {@code head/face}. */
        GOGGLES,
        /** Curios {@code hands}, Trinkets {@code hand/glove}. */
        MITTS
    }

    Optional<ItemStack> get(Player player, Slot slot);

    /**
     * Puts a stack into a slot, replacing what was there. For commands and game tests; players
     * normally equip through the library's own screen.
     *
     * @return false if the player has no such slot (slot data missing or library not ready)
     */
    boolean set(Player player, Slot slot, ItemStack stack);

    /** Called with the player whenever an accessory slot changes (Curios change event, Trinkets callbacks). */
    void onGearChanged(Consumer<Player> listener);

    default Optional<ItemStack> board(Player player) {
        return get(player, Slot.BOARD);
    }

    default Optional<ItemStack> goggles(Player player) {
        return get(player, Slot.GOGGLES);
    }

    default Optional<ItemStack> mitts(Player player) {
        return get(player, Slot.MITTS);
    }

    static GearAccess current() {
        return PlatformHooks.gearAccess();
    }
}
