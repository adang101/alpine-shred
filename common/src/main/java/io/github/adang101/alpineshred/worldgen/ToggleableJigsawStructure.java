package io.github.adang101.alpineshred.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code alpineshred:terrain_park} and {@code alpineshred:snow_feature} (design doc section 12): a
 * vanilla jigsaw structure, the same fields in the same JSON, that first asks its
 * {@code worldgen.*} switch. Vanilla's {@link JigsawStructure} is final, so this holds one built
 * from the same fields and hands its pieces on.
 */
public class ToggleableJigsawStructure extends Structure {
    private final Holder<StructureTemplatePool> startPool;
    private final Optional<ResourceLocation> startJigsawName;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;
    private final WorldgenToggle toggle;
    private final Supplier<StructureType<?>> type;
    private final JigsawStructure jigsaw;

    public ToggleableJigsawStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, Optional<ResourceLocation> startJigsawName,
                                     int maxDepth, HeightProvider startHeight, boolean useExpansionHack,
                                     Optional<Heightmap.Types> projectStartToHeightmap, int maxDistanceFromCenter,
                                     WorldgenToggle toggle, Supplier<StructureType<?>> type) {
        super(settings);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.toggle = toggle;
        this.type = type;
        this.jigsaw = new JigsawStructure(settings, startPool, startJigsawName, maxDepth, startHeight, useExpansionHack,
                projectStartToHeightmap, maxDistanceFromCenter);
    }

    /** The codec for one switch and type: vanilla jigsaw's fields. */
    public static Codec<ToggleableJigsawStructure> codec(WorldgenToggle toggle, Supplier<StructureType<?>> type) {
        return RecordCodecBuilder.<ToggleableJigsawStructure>mapCodec(i -> i.group(
                settingsCodec(i),
                StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
                ResourceLocation.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(s -> s.startJigsawName),
                Codec.intRange(0, 7).fieldOf("size").forGetter(s -> s.maxDepth),
                HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
                Codec.BOOL.fieldOf("use_expansion_hack").forGetter(s -> s.useExpansionHack),
                Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(s -> s.projectStartToHeightmap),
                Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter)
        ).apply(i, (settings, pool, name, depth, height, hack, project, distance) ->
                new ToggleableJigsawStructure(settings, pool, name, depth, height, hack, project, distance, toggle, type))).codec();
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!toggle.allows(context)) return Optional.empty();
        // The jigsaw assembles from its own context of the same seed and chunk, so it builds the
        // same pieces it would as a vanilla structure; its start piece is the stub's position.
        StructureStart start = jigsaw.generate(context.registryAccess(), context.chunkGenerator(), context.biomeSource(), context.randomState(),
                context.structureTemplateManager(), context.seed(), context.chunkPos(), 0, context.heightAccessor(), context.validBiome());
        if (!start.isValid()) return Optional.empty();
        List<StructurePiece> pieces = start.getPieces();
        BoundingBox first = pieces.get(0).getBoundingBox();
        BlockPos at = new BlockPos(first.getCenter().getX(), first.minY(), first.getCenter().getZ());
        return Optional.of(new GenerationStub(at, builder -> pieces.forEach(builder::addPiece)));
    }

    @Override
    public StructureType<?> type() {
        return type.get();
    }
}
