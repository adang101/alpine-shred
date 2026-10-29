package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.park.GrindMaterial;
import io.github.adang101.alpineshred.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The copper rail family (design doc section 10): four oxidation stages plus waxed versions,
 * following vanilla copper rules ({@link WeatheringCopper}): unwaxed pieces weather on random ticks
 * ({@code rail.oxidation}), honeycomb turns a piece into its waxed block, an axe scrapes wax and
 * then oxidation off. More oxidation means more friction; waxed pieces are also slicker.
 */
public class CopperRailBlock extends RailBlock implements WeatheringCopper {
    private final WeatherState age;
    private final boolean waxedVariant;

    public CopperRailBlock(Properties properties, int stage, boolean waxedVariant) {
        super(properties, GrindMaterial.COPPER, stage);
        this.age = WeatherState.values()[stage];
        this.waxedVariant = waxedVariant;
        if (waxedVariant) registerDefaultState(defaultBlockState().setValue(ParkStates.WAXED, true));
    }

    @Override
    public WeatherState getAge() {
        return age;
    }

    public boolean isWaxedVariant() {
        return waxedVariant;
    }

    @Override
    public Optional<BlockState> getNext(BlockState state) {
        if (waxedVariant || oxidation() >= 3) return Optional.empty();
        return Optional.of(copyRail(state, ModBlocks.COPPER_RAILS.get(oxidation() + 1).get()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isRandomlyTicking(BlockState state) {
        return !waxedVariant && oxidation() < 3;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (AlpineConfig.RAIL_OXIDATION.on()) onRandomTick(state, level, pos, random);
    }

    @Override
    @Nullable
    protected BlockState waxedState(BlockState state) {
        if (waxedVariant) return null;
        return copyRail(state, ModBlocks.WAXED_COPPER_RAILS.get(oxidation()).get()).setValue(ParkStates.WAXED, true);
    }

    @Override
    @Nullable
    protected BlockState scrapedState(BlockState state) {
        if (waxedVariant) {
            return copyRail(state, ModBlocks.COPPER_RAILS.get(oxidation()).get()).setValue(ParkStates.WAXED, false);
        }
        if (oxidation() == 0) return null;
        Block previous = ModBlocks.COPPER_RAILS.get(oxidation() - 1).get();
        return copyRail(state, previous);
    }
}
