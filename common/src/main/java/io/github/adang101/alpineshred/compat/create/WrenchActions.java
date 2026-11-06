package io.github.adang101.alpineshred.compat.create;

import io.github.adang101.alpineshred.park.item.ParkWrenchItem;
import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's wrench on our blocks (phase 9 plan, WP4; README 80). The loaders' click handlers only translate their
 * event into {@link #onClick}; the decision is {@link WrenchRule}. No Create class is named: the wrench is matched
 * by the {@code create:wrench} item tag, and nothing here loads without Create's presence being tested first
 * (a tag nobody fills matches nothing).
 */
public final class WrenchActions {
    /** Create's wrench item tag, made by id so no Create class is needed. */
    public static final TagKey<Item> WRENCH = TagKey.create(Registries.ITEM, new ResourceLocation("create", "wrench"));

    private WrenchActions() {
    }

    public static boolean isWrench(ItemStack stack) {
        return stack.is(WRENCH);
    }

    public static WrenchRule.Kind kindOf(BlockState state) {
        if (state.is(ModTags.Blocks.WRENCH_LIFT_PICKUP)) return WrenchRule.Kind.LIFT;
        return ParkWrenchItem.handles(state) ? WrenchRule.Kind.PARK : WrenchRule.Kind.OTHER;
    }

    /**
     * A click with {@code held} on {@code pos}, on either side. PASS leaves the click to Create and vanilla;
     * anything else means we handled it and the loader cancels the event.
     */
    public static InteractionResult onClick(Level level, BlockPos pos, Player player, ItemStack held) {
        if (!isWrench(held) || !player.mayBuild()) return InteractionResult.PASS;
        BlockState state = level.getBlockState(pos);
        boolean sneak = player.isSecondaryUseActive();
        switch (WrenchRule.decide(kindOf(state), sneak)) {
            case PARK_CLICK:
                // The park wrench's own code, never a copy (README 80); non-sneak, since sneak picks up here.
                return ParkWrenchItem.use(level, pos, player, false);
            case PICK_UP:
                pickUp(level, pos, state, player);
                return InteractionResult.sidedSuccess(level.isClientSide);
            case LIFT_HINT:
                if (level.isClientSide) player.displayClientMessage(Component.translatable("message.alpineshred.wrench.lift_hint"), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            default:
                return InteractionResult.PASS;
        }
    }

    /**
     * Takes the block into the player's inventory (nothing extra in creative). {@code destroyBlock} removes it through
     * the same {@code onRemove} as breaking it by hand, so a lift block faults its line exactly as it does then
     * (README 80); the drops are skipped and the block item is handed over instead.
     */
    private static void pickUp(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide) return;
        level.destroyBlock(pos, false, player);
        if (player.getAbilities().instabuild) return;
        ItemStack taken = new ItemStack(state.getBlock());
        if (!player.getInventory().add(taken)) player.drop(taken, false);
    }
}
