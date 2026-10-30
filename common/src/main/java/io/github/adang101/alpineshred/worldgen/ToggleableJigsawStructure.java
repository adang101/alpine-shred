package io.github.adang101.alpineshred.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code alpineshred:terrain_park} and {@code alpineshred:snow_feature} (design doc section 12): a
 * vanilla jigsaw structure, the same fields in the same JSON, that first asks its
 * {@code worldgen.*} switch.
 */
public class ToggleableJigsawStructure extends JigsawStructure {
    private final Holder<StructureTemplatePool> startPool;
    private final Optional<ResourceLocation> startJigsawName;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;
    private final WorldgenToggle toggle;
    private final Supplier<StructureType<?>> type;

    public ToggleableJigsawStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, Optional<ResourceLocation> startJigsawName,
                                     int maxDepth, HeightProvider startHeight, boolean useExpansionHack,
                                     Optional<Heightmap.Types> projectStartToHeightmap, int maxDistanceFromCenter,
                                     WorldgenToggle toggle, Supplier<StructureType<?>> type) {
        super(settings, startPool, startJigsawName, maxDepth, startHeight, useExpansionHack, projectStartToHeightmap, maxDistanceFromCenter);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.toggle = toggle;
        this.type = type;
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
        return super.findGenerationPoint(context);
    }

    @Override
    public StructureType<?> type() {
        return type.get();
    }
}
