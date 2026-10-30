package io.github.adang101.alpineshred.worldgen;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Structure types and piece types (design doc section 12). The structures themselves, their sets,
 * biome tags and template pools are data under {@code data/alpineshred/worldgen}, so datapacks can
 * retune or replace them; every type asks its {@code worldgen.*} switch first.
 */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(AlpineShred.MOD_ID, Registries.STRUCTURE_TYPE);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(AlpineShred.MOD_ID, Registries.STRUCTURE_PIECE);

    public static final RegistrySupplier<StructureType<ToggleableJigsawStructure>> TERRAIN_PARK = TYPES.register("terrain_park",
            () -> (StructureType<ToggleableJigsawStructure>) () -> ToggleableJigsawStructure.codec(WorldgenToggle.TERRAIN_PARKS, ModStructures::terrainParkType));
    public static final RegistrySupplier<StructureType<ToggleableJigsawStructure>> SNOW_FEATURE = TYPES.register("snow_feature",
            () -> (StructureType<ToggleableJigsawStructure>) () -> ToggleableJigsawStructure.codec(WorldgenToggle.SNOW_FEATURES, ModStructures::snowFeatureType));
    public static final RegistrySupplier<StructureType<GroomedRunStructure>> GROOMED_RUN = TYPES.register("groomed_run",
            () -> (StructureType<GroomedRunStructure>) () -> GroomedRunStructure.CODEC);
    public static final RegistrySupplier<StructureType<ResortStructure>> SKI_RESORT = TYPES.register("ski_resort",
            () -> (StructureType<ResortStructure>) () -> ResortStructure.CODEC);

    public static final RegistrySupplier<StructurePieceType> GROOMED_RUN_PIECE = PIECES.register("groomed_run",
            () -> (StructurePieceType) GroomedRunPiece::new);
    public static final RegistrySupplier<StructurePieceType> LODGE_PIECE = PIECES.register("lodge",
            () -> (StructurePieceType.StructureTemplateType) LodgePiece::new);

    private ModStructures() {
    }

    private static StructureType<?> terrainParkType() {
        return TERRAIN_PARK.get();
    }

    private static StructureType<?> snowFeatureType() {
        return SNOW_FEATURE.get();
    }

    public static void register() {
        TYPES.register();
        PIECES.register();
    }
}
