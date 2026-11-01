package io.github.adang101.alpineshred.lift;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.park.V3;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code lift_linker} (design doc section 13, "Linking with lift_linker"). Right-click a drive
 * station to start a chain (clicking one again restarts it), then tower heads in order, then the
 * return station to finish and create the line. Sneak and right-click a head to unlink its line. The
 * chain in progress lives in the item's NBT ({@value #CHAIN}): the dimension, the drive and the heads.
 * The rules are {@link LinkValidator}'s; every rejection says why in the action bar.
 *
 * <p>Linking through a faulted line's nodes replaces that line (the old one is removed and a new one,
 * not faulted, takes its place); a node of a healthy line is refused.
 */
public class LiftLinkerItem extends Item {
    private static final String CHAIN = "LinkChain";
    private static final String DIM = "Dim";
    private static final String DRIVE = "Drive";
    private static final String HEADS = "Heads";

    private final String descriptionKey;

    public LiftLinkerItem(String descriptionKey, Properties properties) {
        super(properties);
        this.descriptionKey = descriptionKey;
    }

    // ---- chain in the item ----

    /** The chain in progress: the drive first, then the heads; empty if none. */
    public static List<BlockPos> chain(ItemStack stack) {
        List<BlockPos> out = new ArrayList<>();
        CompoundTag tag = stack.getTagElement(CHAIN);
        if (tag == null || !tag.contains(DRIVE, 10)) return out;
        out.add(NbtUtils.readBlockPos(tag.getCompound(DRIVE)));
        ListTag heads = tag.getList(HEADS, 10);
        for (int i = 0; i < heads.size(); i++) out.add(NbtUtils.readBlockPos(heads.getCompound(i)));
        return out;
    }

    private static String chainDimension(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(CHAIN);
        return tag == null ? "" : tag.getString(DIM);
    }

    private static void saveChain(ItemStack stack, Level level, List<BlockPos> chain) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIM, level.dimension().location().toString());
        tag.put(DRIVE, NbtUtils.writeBlockPos(chain.get(0)));
        ListTag heads = new ListTag();
        for (int i = 1; i < chain.size(); i++) heads.add(NbtUtils.writeBlockPos(chain.get(i)));
        tag.put(HEADS, heads);
        stack.addTagElement(CHAIN, tag);
    }

    private static void clearChain(ItemStack stack) {
        stack.removeTagKey(CHAIN);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
        List<BlockPos> chain = chain(stack);
        if (!chain.isEmpty()) {
            BlockPos drive = chain.get(0);
            tooltip.add(Component.translatable("tooltip.alpineshred.lift_linker.chain", drive.getX() + " " + drive.getY() + " " + drive.getZ(),
                    chain.size() - 1).withStyle(ChatFormatting.AQUA));
        }
    }

    // ---- use ----

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof LiftNodeBlockEntity)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel) || context.getPlayer() == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            if (be instanceof LiftTowerHeadBlockEntity head) unlink(serverLevel, player, head);
            return InteractionResult.SUCCESS;
        }
        if (be instanceof LiftDriveStationBlockEntity) {
            start(serverLevel, player, stack, pos);
        } else if (be instanceof LiftTowerHeadBlockEntity) {
            addHead(serverLevel, player, stack, pos);
        } else if (be instanceof LiftReturnStationBlockEntity) {
            finish(serverLevel, player, stack, pos);
        }
        return InteractionResult.SUCCESS;
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.alpineshred.linker." + key, args), true);
    }

    private static String describe(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static LinkValidator.Limits limits() {
        return new LinkValidator.Limits(AlpineConfig.LIFT_MAX_SPAN.get(), AlpineConfig.LIFT_MAX_HEADS.get(),
                AlpineConfig.LIFT_MAX_LENGTH.get(), AlpineConfig.LIFT_MAX_LINES_PER_LEVEL.get());
    }

    /** Solid means anything with a collision shape; blocks in unloaded chunks count as solid (the safe answer). */
    private static BlockSolid solidIn(ServerLevel level) {
        return (x, y, z) -> {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.hasChunkAt(p)) return true;
            BlockState state = level.getBlockState(p);
            return !state.getCollisionShape(level, p).isEmpty();
        };
    }

    private static List<V3> toNodes(List<BlockPos> chain) {
        List<V3> nodes = new ArrayList<>(chain.size());
        for (BlockPos p : chain) nodes.add(LiftLine.node(p));
        return nodes;
    }

    /** The line this node belongs to if it is healthy (not faulted), else null. */
    private static LiftLine healthyLineOf(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof LiftNodeBlockEntity node) || node.lineId() == null) return null;
        LiftLine line = LiftManager.get(level, node.lineId());
        return line != null && !line.faulted ? line : null;
    }

    private void start(ServerLevel level, Player player, ItemStack stack, BlockPos drive) {
        if (healthyLineOf(level, drive) != null) {
            say(player, "already_linked", describe(drive));
            return;
        }
        saveChain(stack, level, List.of(drive));
        say(player, "started", describe(drive));
    }

    /** The chain in progress, or null (with a message) if there is none for this level. */
    private static List<BlockPos> activeChain(ServerLevel level, Player player, ItemStack stack) {
        List<BlockPos> chain = chain(stack);
        if (chain.isEmpty()) {
            say(player, "need_drive");
            return null;
        }
        if (!chainDimension(stack).equals(level.dimension().location().toString())) {
            clearChain(stack);
            say(player, "other_dimension");
            return null;
        }
        return chain;
    }

    private void addHead(ServerLevel level, Player player, ItemStack stack, BlockPos head) {
        List<BlockPos> chain = activeChain(level, player, stack);
        if (chain == null) return;
        if (chain.contains(head)) {
            say(player, "duplicate", describe(head));
            return;
        }
        if (healthyLineOf(level, head) != null) {
            say(player, "already_linked", describe(head));
            return;
        }
        List<BlockPos> next = new ArrayList<>(chain);
        next.add(head);
        LinkValidator.Result result = LinkValidator.checkAdd(toNodes(next), solidIn(level), limits());
        if (!result.ok()) {
            reject(player, result, next);
            return;
        }
        saveChain(stack, level, next);
        say(player, "added", next.size() - 1, describe(head));
    }

    private void finish(ServerLevel level, Player player, ItemStack stack, BlockPos returnPos) {
        List<BlockPos> chain = activeChain(level, player, stack);
        if (chain == null) return;
        if (returnPos.equals(chain.get(0))) {
            say(player, "same_station");
            return;
        }
        List<BlockPos> nodes = new ArrayList<>(chain);
        nodes.add(returnPos);
        // Every node must still be the right kind of block, and free of a healthy line.
        for (int i = 0; i < nodes.size(); i++) {
            BlockEntity be = level.getBlockEntity(nodes.get(i));
            boolean right = i == 0 ? be instanceof LiftDriveStationBlockEntity
                    : i == nodes.size() - 1 ? be instanceof LiftReturnStationBlockEntity : be instanceof LiftTowerHeadBlockEntity;
            if (!right) {
                say(player, "node_missing", describe(nodes.get(i)));
                return;
            }
            if (healthyLineOf(level, nodes.get(i)) != null) {
                say(player, "already_linked", describe(nodes.get(i)));
                return;
            }
        }
        // Faulted lines through these nodes are replaced; they do not count against the level's limit.
        Map<UUID, LiftLine> replaced = new LinkedHashMap<>();
        for (BlockPos p : nodes) {
            if (level.getBlockEntity(p) instanceof LiftNodeBlockEntity node && node.lineId() != null) {
                LiftLine old = LiftManager.get(level, node.lineId());
                if (old != null) replaced.put(old.id, old);
            }
        }
        int existing = LiftManager.lines(level).size() - replaced.size();
        LinkValidator.Result result = LinkValidator.checkFinish(toNodes(nodes), solidIn(level), limits(), existing);
        if (!result.ok()) {
            reject(player, result, nodes);
            return;
        }
        for (LiftLine old : replaced.values()) {
            LiftManager.remove(level, old.id);
            clearNodes(level, old);
        }
        LiftLine line = LiftManager.create(level, nodes);
        if (line == null) {
            say(player, "create_failed");
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            LiftNodeBlockEntity node = (LiftNodeBlockEntity) level.getBlockEntity(nodes.get(i));
            if (node instanceof LiftTowerHeadBlockEntity head) head.setLink(line.id, nodes.get(i - 1), nodes.get(i + 1));
            else node.setLineId(line.id);
        }
        clearChain(stack);
        player.displayClientMessage(Component.translatable("message.alpineshred.linker.created", nodes.size() - 2,
                String.format("%.0f", line.path().loopLength()), line.chairCount()), false);
    }

    private static void clearNodes(ServerLevel level, LiftLine line) {
        for (BlockPos p : line.nodePositions()) {
            if (level.hasChunkAt(p) && level.getBlockEntity(p) instanceof LiftNodeBlockEntity node) node.clearLine();
        }
    }

    private void unlink(ServerLevel level, Player player, LiftTowerHeadBlockEntity head) {
        UUID id = head.lineId();
        if (id == null) {
            say(player, "not_linked");
            return;
        }
        LiftLine line = LiftManager.get(level, id);
        if (line == null) {
            head.clearLine();
        } else {
            LiftManager.remove(level, id);
            clearNodes(level, line);
            head.clearLine();
        }
        say(player, "unlinked");
    }

    private static void reject(Player player, LinkValidator.Result r, List<BlockPos> nodes) {
        String at = r.span() >= 0 && r.span() + 1 < nodes.size() ? describe(nodes.get(r.span() + 1)) : "";
        switch (r.reason()) {
            case TOO_FAR -> say(player, "too_far", String.format("%.0f", r.value()), AlpineConfig.LIFT_MAX_SPAN.get());
            case TOO_STEEP -> say(player, "too_steep", String.format("%.0f", r.value()), (int) LinkValidator.MAX_SLOPE_DEGREES);
            case SAME_PLACE -> say(player, "same_place", at);
            case BLOCKED_LOADED -> say(player, "blocked_loaded", r.bx() + " " + r.by() + " " + r.bz());
            case BLOCKED_RETURN -> say(player, "blocked_return", r.bx() + " " + r.by() + " " + r.bz());
            case TOO_MANY_HEADS -> say(player, "too_many_heads", (int) r.value(), AlpineConfig.LIFT_MAX_HEADS.get());
            case TOO_LONG -> say(player, "too_long", String.format("%.0f", r.value()), AlpineConfig.LIFT_MAX_LENGTH.get());
            case LEVEL_FULL -> say(player, "level_full", AlpineConfig.LIFT_MAX_LINES_PER_LEVEL.get());
            case SAME_STATION -> say(player, "same_station");
            case OK -> {
            }
        }
    }
}
