package io.github.adang101.alpineshred.fabric;

import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import dev.emi.trinkets.api.event.TrinketEquipCallback;
import dev.emi.trinkets.api.event.TrinketUnequipCallback;
import io.github.adang101.alpineshred.gear.GearAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * {@link GearAccess} over Trinkets (design doc section 6). Slots: {@code feet/snow_gear} (ours),
 * {@code head/face} and {@code hand/glove}, all declared in {@code data/trinkets/slots/}. The only
 * class in the mod that imports Trinkets.
 */
public final class TrinketsGearAccess implements GearAccess {
    public static final TrinketsGearAccess INSTANCE = new TrinketsGearAccess();

    private final List<Consumer<Player>> listeners = new CopyOnWriteArrayList<>();
    private boolean subscribed;

    private TrinketsGearAccess() {
    }

    static String group(Slot slot) {
        return switch (slot) {
            case BOARD -> "feet";
            case GOGGLES -> "head";
            case MITTS -> "hand";
        };
    }

    static String name(Slot slot) {
        return switch (slot) {
            case BOARD -> "snow_gear";
            case GOGGLES -> "face";
            case MITTS -> "glove";
        };
    }

    @Override
    public Optional<ItemStack> get(Player player, Slot slot) {
        return inventory(player, slot)
                .filter(inventory -> inventory.getContainerSize() > 0)
                .map(inventory -> inventory.getItem(0))
                .filter(stack -> !stack.isEmpty());
    }

    @Override
    public boolean set(Player player, Slot slot, ItemStack stack) {
        Optional<TrinketInventory> inventory = inventory(player, slot);
        if (inventory.isEmpty() || inventory.get().getContainerSize() < 1) return false;
        inventory.get().setItem(0, stack);
        notifyChanged(player);
        return true;
    }

    @Override
    public synchronized void onGearChanged(Consumer<Player> listener) {
        listeners.add(listener);
        if (!subscribed) {
            subscribed = true;
            TrinketEquipCallback.EVENT.register((stack, slotReference, entity) -> onChange(entity));
            TrinketUnequipCallback.EVENT.register((stack, slotReference, entity) -> onChange(entity));
        }
    }

    private void onChange(LivingEntity entity) {
        if (entity instanceof Player player) notifyChanged(player);
    }

    private void notifyChanged(Player player) {
        for (Consumer<Player> listener : listeners) listener.accept(player);
    }

    private static Optional<TrinketInventory> inventory(Player player, Slot slot) {
        return TrinketsApi.getTrinketComponent(player).map(component -> {
            Map<String, TrinketInventory> slots = component.getInventory().get(group(slot));
            return slots == null ? null : slots.get(name(slot));
        });
    }
}
