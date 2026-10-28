package io.github.adang101.alpineshred.forge;

import io.github.adang101.alpineshred.gear.GearAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * {@link GearAccess} over Curios 5.x (design doc section 6). Slots: {@code snow_gear} and
 * {@code goggles} (ours, from {@code data/alpineshred/curios/slots/}) and the standard
 * {@code hands}. The only class in the mod that imports Curios.
 */
public final class CuriosGearAccess implements GearAccess {
    public static final CuriosGearAccess INSTANCE = new CuriosGearAccess();

    private final List<Consumer<Player>> listeners = new CopyOnWriteArrayList<>();
    private boolean subscribed;

    private CuriosGearAccess() {
    }

    static String slotId(Slot slot) {
        return switch (slot) {
            case BOARD -> "snow_gear";
            case GOGGLES -> "goggles";
            case MITTS -> "hands";
        };
    }

    @Override
    public Optional<ItemStack> get(Player player, Slot slot) {
        return handler(player, slot)
                .filter(handler -> handler.getSlots() > 0)
                .map(handler -> handler.getStacks().getStackInSlot(0))
                .filter(stack -> !stack.isEmpty());
    }

    @Override
    public boolean set(Player player, Slot slot, ItemStack stack) {
        Optional<ICurioStacksHandler> handler = handler(player, slot);
        if (handler.isEmpty() || handler.get().getSlots() < 1) return false;
        handler.get().getStacks().setStackInSlot(0, stack);
        notifyChanged(player);
        return true;
    }

    @Override
    public synchronized void onGearChanged(Consumer<Player> listener) {
        listeners.add(listener);
        if (!subscribed) {
            subscribed = true;
            MinecraftForge.EVENT_BUS.addListener(this::onCurioChange);
        }
    }

    private void onCurioChange(CurioChangeEvent event) {
        if (event.getEntity() instanceof Player player) notifyChanged(player);
    }

    private void notifyChanged(Player player) {
        for (Consumer<Player> listener : listeners) listener.accept(player);
    }

    private static Optional<ICurioStacksHandler> handler(Player player, Slot slot) {
        return CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler(slotId(slot)));
    }
}
