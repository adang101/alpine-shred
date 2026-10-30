package io.github.adang101.alpineshred.design;

import io.github.adang101.alpineshred.registry.ModBlocks;
import io.github.adang101.alpineshred.registry.ModMenus;
import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Paint Bench's menu (design doc section 22, "Bench and flow"): one slot for a board or skis,
 * one for the dye a save costs, and the player's inventory. Like a crafting table, the bench keeps
 * nothing: closing the menu hands both slots back. The editor works on a copy of the design; the
 * server writes the slot's item only through a validated save.
 */
public class PaintBenchMenu extends AbstractContainerMenu {
    public static final int GEAR_SLOT = 0;
    public static final int DYE_SLOT = 1;
    /** Slot positions in the bench screen's 176 by 166 layout. */
    public static final int GEAR_X = 44, GEAR_Y = 35, DYE_X = 116, DYE_Y = 35;

    private final Container bench;
    private final ContainerLevelAccess access;

    /** Client side, from the open-screen packet. */
    public PaintBenchMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public PaintBenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.PAINT_BENCH.get(), id);
        this.access = access;
        this.bench = new SimpleContainer(2) {
            @Override
            public void setChanged() {
                super.setChanged();
                PaintBenchMenu.this.slotsChanged(this);
            }
        };
        addSlot(new Slot(bench, GEAR_SLOT, GEAR_X, GEAR_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isGear(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(bench, DYE_SLOT, DYE_X, DYE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModTags.Items.DESIGN_COST);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }

    public static boolean isGear(ItemStack stack) {
        return stack.is(ModTags.Items.BOARDS) || stack.is(ModTags.Items.SKIS);
    }

    public ItemStack gear() {
        return bench.getItem(GEAR_SLOT);
    }

    public ItemStack dye() {
        return bench.getItem(DYE_SLOT);
    }

    /** Server: the save's cost comes out of the dye slot in the same step as the write. */
    public void takeDye(int count) {
        bench.removeItem(DYE_SLOT, count);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.PAINT_BENCH.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, bench));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = index >= 0 && index < this.slots.size() ? this.slots.get(index) : null;
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int inventoryStart = 2, end = this.slots.size();
        if (index < inventoryStart) {
            if (!moveItemStackTo(stack, inventoryStart, end, true)) return ItemStack.EMPTY;
        } else if (isGear(stack)) {
            if (!moveItemStackTo(stack, GEAR_SLOT, GEAR_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(ModTags.Items.DESIGN_COST)) {
            if (!moveItemStackTo(stack, DYE_SLOT, DYE_SLOT + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }
}
