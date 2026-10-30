package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.park.GrindMaterial;
import io.github.adang101.alpineshred.park.block.BoxBlock;
import io.github.adang101.alpineshred.park.block.CopperRailBlock;
import io.github.adang101.alpineshred.park.block.ParkDecorBlock;
import io.github.adang101.alpineshred.park.block.ParkShapes;
import io.github.adang101.alpineshred.park.block.PipeBlock;
import io.github.adang101.alpineshred.park.block.PipeCopingBlock;
import io.github.adang101.alpineshred.park.block.PlasticBoxBlock;
import io.github.adang101.alpineshred.park.block.RailBlock;
import io.github.adang101.alpineshred.park.block.RailPostBlock;
import io.github.adang101.alpineshred.park.block.RampBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Blocks: {@code groomed_snow} (phase 1) and the park blocks (phase 4, design doc sections 10, 11
 * and the appendix), and the Paint Bench (section 22). Lift blocks arrive with their phase.
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(AlpineShred.MOD_ID, Registries.BLOCK);

    private static final List<RegistrySupplier<? extends Block>> PARK = new ArrayList<>();
    /** Copper oxidation stage prefixes, as vanilla names its copper blocks. */
    private static final String[] STAGES = {"", "exposed_", "weathered_", "oxidized_"};

    /** Flattened, packed snow: the fastest, grippiest snow surface (section 7). */
    public static final RegistrySupplier<Block> GROOMED_SNOW = BLOCKS.register("groomed_snow",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.SNOW_BLOCK)));

    /** Section 22: a crafting table for paint. */
    public static final RegistrySupplier<io.github.adang101.alpineshred.design.PaintBenchBlock> PAINT_BENCH = BLOCKS.register("paint_bench",
            () -> new io.github.adang101.alpineshred.design.PaintBenchBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE)));

    // ---- rails (section 10) ---------------------------------------------------------------------------
    public static final RegistrySupplier<RailBlock> PIPE_RAIL = park("pipe_rail",
            () -> new RailBlock(rail(MapColor.METAL, SoundType.METAL), GrindMaterial.METAL, 0));
    public static final RegistrySupplier<RailBlock> FLAT_RAIL = park("flat_rail",
            () -> new RailBlock(rail(MapColor.METAL, SoundType.METAL), GrindMaterial.METAL, 0));
    /** Unwaxed copper rails by oxidation stage: fresh, exposed, weathered, oxidized. */
    public static final List<RegistrySupplier<CopperRailBlock>> COPPER_RAILS = copperRails(false);
    /** Their waxed counterparts, by the same stage. */
    public static final List<RegistrySupplier<CopperRailBlock>> WAXED_COPPER_RAILS = copperRails(true);
    public static final RegistrySupplier<RailPostBlock> RAIL_POST = park("rail_post",
            () -> new RailPostBlock(rail(MapColor.METAL, SoundType.METAL)));

    // ---- boxes, ramps, pipes (section 11) ---------------------------------------------------------------
    public static final RegistrySupplier<BoxBlock> WOOD_BOX = park("wood_box",
            () -> new BoxBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS), GrindMaterial.WOOD));
    public static final RegistrySupplier<BoxBlock> METAL_BOX = park("metal_box",
            () -> new BoxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops(), GrindMaterial.METAL));
    public static final RegistrySupplier<PlasticBoxBlock> PLASTIC_BOX = park("plastic_box",
            () -> new PlasticBoxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.5f, 3.0f).sound(SoundType.BAMBOO_WOOD)));
    public static final RegistrySupplier<RampBlock> PARK_RAMP = park("park_ramp",
            () -> new RampBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.0f).sound(SoundType.WOOD)));
    public static final RegistrySupplier<PipeBlock> QUARTER_PIPE = park("quarter_pipe",
            () -> new PipeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.0f).sound(SoundType.WOOD)));
    public static final RegistrySupplier<PipeCopingBlock> PIPE_COPING = park("pipe_coping",
            () -> new PipeCopingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(2.0f, 6.0f).sound(SoundType.METAL)));

    // ---- decor (section 11) ----------------------------------------------------------------------------
    public static final RegistrySupplier<ParkDecorBlock> SLOPE_MARKER_GREEN = park("slope_marker_green", () -> decor(MapColor.COLOR_GREEN, ParkShapes::marker));
    public static final RegistrySupplier<ParkDecorBlock> SLOPE_MARKER_BLUE = park("slope_marker_blue", () -> decor(MapColor.COLOR_BLUE, ParkShapes::marker));
    public static final RegistrySupplier<ParkDecorBlock> SLOPE_MARKER_BLACK = park("slope_marker_black", () -> decor(MapColor.COLOR_BLACK, ParkShapes::marker));
    public static final RegistrySupplier<ParkDecorBlock> PARK_FLAG = park("park_flag", () -> decor(MapColor.COLOR_RED, ParkShapes::flag));
    public static final RegistrySupplier<ParkDecorBlock> START_GATE = park("start_gate", () -> decor(MapColor.WOOD, ParkShapes::gate));

    private ModBlocks() {
    }

    private static <T extends Block> RegistrySupplier<T> park(String id, java.util.function.Supplier<T> block) {
        RegistrySupplier<T> supplier = BLOCKS.register(id, block);
        PARK.add(supplier);
        return supplier;
    }

    private static BlockBehaviour.Properties rail(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0f, 6.0f).sound(sound).noOcclusion()
                .requiresCorrectToolForDrops();
    }

    private static ParkDecorBlock decor(MapColor color, java.util.function.Function<io.github.adang101.alpineshred.park.Facing, net.minecraft.world.phys.shapes.VoxelShape> shape) {
        return new ParkDecorBlock(BlockBehaviour.Properties.of().mapColor(color).strength(0.5f).sound(SoundType.WOOD).noOcclusion(), shape);
    }

    private static List<RegistrySupplier<CopperRailBlock>> copperRails(boolean waxed) {
        List<RegistrySupplier<CopperRailBlock>> out = new ArrayList<>();
        for (int stage = 0; stage < STAGES.length; stage++) {
            int s = stage;
            String id = (waxed ? "waxed_" : "") + STAGES[stage] + "copper_pipe_rail";
            out.add(park(id, () -> new CopperRailBlock(rail(MapColor.COLOR_ORANGE, SoundType.COPPER), s, waxed)));
        }
        return Collections.unmodifiableList(out);
    }

    /** Every park block, in registration order (creative tab, datagen, tests). */
    public static List<RegistrySupplier<? extends Block>> parkBlocks() {
        return Collections.unmodifiableList(PARK);
    }

    public static void register() {
        BLOCKS.register();
    }
}
