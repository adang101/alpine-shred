package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.movement.TuningData;
import io.github.adang101.alpineshred.park.Facing;
import io.github.adang101.alpineshred.park.GrindMaterial;
import io.github.adang101.alpineshred.park.GrindPath;
import io.github.adang101.alpineshred.park.RailPath;
import io.github.adang101.alpineshred.park.RailPlacement;
import io.github.adang101.alpineshred.park.RailShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Every rail piece (design doc section 10): {@code pipe_rail}, {@code flat_rail} and the copper
 * family share this class; material only changes friction and look. A piece has a shape and a
 * facing, no block entity, and its path comes from {@link RailPath}. Pieces connect, orient
 * themselves and follow the ground as they are placed ({@link RailPlacement}); honeycomb waxes a
 * rail and an axe scrapes the wax off, as with vanilla copper.
 */
public class RailBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final GrindMaterial material;
    private final int oxidation;

    public RailBlock(Properties properties, GrindMaterial material, int oxidation) {
        super(properties);
        this.material = material;
        this.oxidation = oxidation;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(ParkStates.RAIL_SHAPE, ParkStates.RailShapeProp.FLAT)
                .setValue(ParkStates.CONNECTED_A, false).setValue(ParkStates.CONNECTED_B, false)
                .setValue(ParkStates.LEGS, true).setValue(ParkStates.WAXED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ParkStates.RAIL_SHAPE, ParkStates.CONNECTED_A, ParkStates.CONNECTED_B, ParkStates.LEGS, ParkStates.WAXED);
    }

    public GrindMaterial material() {
        return material;
    }

    /** Copper oxidation stage, 0 (fresh) to 3 (oxidized); 0 for iron. */
    public int oxidation() {
        return oxidation;
    }

    public static RailShape shape(BlockState state) {
        return state.getValue(ParkStates.RAIL_SHAPE).shape;
    }

    public static Facing facing(BlockState state) {
        return ParkStates.facing(state.getValue(FACING));
    }

    public static RailPlacement.Piece piece(BlockState state) {
        return new RailPlacement.Piece(shape(state), facing(state));
    }

    /**
     * Friction along this rail (section 7 table: 0.0008, waxed 0.0004), raised by oxidation. The
     * tuning file carries all three numbers.
     */
    public double friction(BlockState state) {
        var t = TuningData.tuning();
        double base = state.getValue(ParkStates.WAXED) ? t.railWaxedFriction : t.railFriction;
        return base * (1 + t.railOxidationFriction * oxidation);
    }

    public GrindPath grindPath(BlockState state, BlockPos pos) {
        return new GrindPath(pos.asLong(), RailPath.at(shape(state), facing(state), pos.getX(), pos.getY(), pos.getZ()), material, friction(state));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.alpineshred.rail").withStyle(ChatFormatting.GRAY));
    }

    // ---- shapes -----------------------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ParkShapes.railOutline(shape(state), facing(state));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ParkShapes.railBar(shape(state), facing(state));
    }

    // ---- placement and upkeep -------------------------------------------------------------------

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Facing facing = ParkStates.facing(context.getHorizontalDirection());
        boolean sneak = context.getPlayer() != null && context.getPlayer().isSecondaryUseActive();
        RailPlacement.Choice choice = RailPlacement.choose(new LevelRails(level), LevelRails.cell(pos), facing, sneak);
        BlockState state = withPiece(defaultBlockState(), choice.piece());
        return refresh(level, pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(placer instanceof Player player)) return;
        // A piece placed beside the end of a line turns that end into a curve (vanilla-style corners).
        Facing facing = ParkStates.facing(player.getDirection());
        RailPlacement.Choice choice = RailPlacement.choose(new LevelRails(level), LevelRails.cell(pos), facing, player.isSecondaryUseActive());
        if (choice.reshapeAt() != null && choice.piece().equals(piece(state))) {
            BlockPos at = new BlockPos(choice.reshapeAt().x(), choice.reshapeAt().y(), choice.reshapeAt().z());
            BlockState neighbour = level.getBlockState(at);
            if (neighbour.getBlock() instanceof RailBlock rail) {
                level.setBlock(at, rail.refresh(level, at, withPiece(neighbour, choice.reshapeTo())), Block.UPDATE_ALL);
            }
        }
        refreshAround(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) refreshAround(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(state.getBlock())) refreshAround(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return refresh(level, pos, state);
    }

    static BlockState withPiece(BlockState state, RailPlacement.Piece piece) {
        return state.setValue(FACING, ParkStates.direction(piece.facing())).setValue(ParkStates.RAIL_SHAPE, ParkStates.RailShapeProp.of(piece.shape()));
    }

    /** Recomputes the connection end caps and legs. */
    BlockState refresh(BlockGetter level, BlockPos pos, BlockState state) {
        boolean[] ends = RailPlacement.connections(new LevelRails(level), LevelRails.cell(pos), piece(state));
        return state.setValue(ParkStates.CONNECTED_A, ends[0]).setValue(ParkStates.CONNECTED_B, ends[1])
                .setValue(ParkStates.LEGS, needsLegs(level, pos));
    }

    /**
     * Legs (section 10): the stubs that stand a rail on the ground, so they show when the block
     * below has a solid top face. A post carries the rail instead (a {@code rail_post}, fence or
     * wall: rails also work on vanilla fence posts), and over air there is nothing to stand on.
     */
    static boolean needsLegs(BlockGetter level, BlockPos pos) {
        BlockPos down = pos.below();
        BlockState below = level.getBlockState(down);
        if (below.getBlock() instanceof RailPostBlock || below.is(BlockTags.FENCES) || below.is(BlockTags.WALLS)) return false;
        return below.isFaceSturdy(level, down, Direction.UP);
    }

    /** Rises connect diagonally, beyond the six neighbours vanilla updates: refresh every rail around. */
    static void refreshAround(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (p.equals(pos)) continue;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof RailBlock rail) {
                BlockState updated = rail.refresh(level, p, s);
                if (updated != s) level.setBlock(p, updated, Block.UPDATE_CLIENTS);
            }
        }
    }

    // ---- wax and scraping -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.HONEYCOMB)) {
            BlockState waxed = waxedState(state);
            if (waxed == null) return InteractionResult.PASS;
            if (!level.isClientSide) {
                level.setBlock(pos, waxed, Block.UPDATE_ALL_IMMEDIATE);
                level.levelEvent(player, 3003, pos, 0); // wax on
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, waxed));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.getItem() instanceof AxeItem) {
            BlockState scraped = scrapedState(state);
            if (scraped == null) return InteractionResult.PASS;
            if (!level.isClientSide) {
                boolean wax = state.getValue(ParkStates.WAXED);
                level.playSound(null, pos, wax ? SoundEvents.AXE_WAX_OFF : SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0f, 1.0f);
                level.levelEvent(player, wax ? 3004 : 3005, pos, 0);
                level.setBlock(pos, scraped, Block.UPDATE_ALL_IMMEDIATE);
                held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, scraped));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS; // the wrench and everything else: the item decides
    }

    /** The waxed state, or null when already waxed. Copper turns into its waxed block. */
    @Nullable
    protected BlockState waxedState(BlockState state) {
        if (state.getValue(ParkStates.WAXED)) return null;
        return state.setValue(ParkStates.WAXED, true);
    }

    /** What an axe leaves: wax removed, or (copper) one oxidation stage scraped off; null for nothing. */
    @Nullable
    protected BlockState scrapedState(BlockState state) {
        if (!state.getValue(ParkStates.WAXED)) return null;
        return state.setValue(ParkStates.WAXED, false);
    }

    /** Copies every rail property onto another rail block's default state. */
    static BlockState copyRail(BlockState from, Block to) {
        BlockState s = to.defaultBlockState();
        return s.setValue(FACING, from.getValue(FACING)).setValue(ParkStates.RAIL_SHAPE, from.getValue(ParkStates.RAIL_SHAPE))
                .setValue(ParkStates.CONNECTED_A, from.getValue(ParkStates.CONNECTED_A))
                .setValue(ParkStates.CONNECTED_B, from.getValue(ParkStates.CONNECTED_B))
                .setValue(ParkStates.LEGS, from.getValue(ParkStates.LEGS))
                .setValue(ParkStates.WAXED, from.getValue(ParkStates.WAXED));
    }
}
