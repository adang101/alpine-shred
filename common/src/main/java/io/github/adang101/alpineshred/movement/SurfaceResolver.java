package io.github.adang101.alpineshred.movement;

import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Maps blocks to {@link SurfaceProfile}s (design doc section 7, "Surface profiles"): the highest
 * priority profile whose {@code blocks} list names the block or one of its tags wins; any other block
 * with a collision shape is {@link SurfaceProfile#SCRAPE}; air, fluids and plants are nothing.
 *
 * <p>The profile list is compiled once per data reload into block sets and tag keys. Tags are
 * checked live, so a tag reload needs no cache flush.
 */
public final class SurfaceResolver {
    private record Matcher(SurfaceProfile profile, Set<Block> blocks, List<TagKey<Block>> tags) {
        boolean matches(BlockState state) {
            if (blocks.contains(state.getBlock())) return true;
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) return true;
            }
            return false;
        }
    }

    private static volatile List<SurfaceProfile> compiledFrom;
    private static volatile List<Matcher> matchers = List.of();

    private SurfaceResolver() {
    }

    /** The profile for a block state, or null when it is not a surface at all (air, fluid, plants). */
    @Nullable
    public static SurfaceProfile forState(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) return null;
        SurfaceProfile matched = match(state);
        if (matched != null) return matched;
        return state.getCollisionShape(level, pos).isEmpty() ? null : SurfaceProfile.SCRAPE;
    }

    /** Only explicit matches; never {@link SurfaceProfile#SCRAPE}. */
    @Nullable
    public static SurfaceProfile match(BlockState state) {
        for (Matcher m : matchers()) {
            if (m.matches(state)) return m.profile();
        }
        return null;
    }

    private static List<Matcher> matchers() {
        List<SurfaceProfile> current = TuningData.surfaces();
        if (current != compiledFrom) {
            synchronized (SurfaceResolver.class) {
                if (current != compiledFrom) {
                    matchers = compile(current);
                    compiledFrom = current;
                }
            }
        }
        return matchers;
    }

    private static List<Matcher> compile(List<SurfaceProfile> profiles) {
        List<Matcher> out = new ArrayList<>();
        for (SurfaceProfile profile : profiles) {
            Set<Block> blocks = new HashSet<>();
            List<TagKey<Block>> tags = new ArrayList<>();
            for (String entry : profile.blocks()) {
                boolean tag = entry.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? entry.substring(1) : entry);
                if (id == null) {
                    AlpineShred.LOGGER.warn("surface_profiles/{}: bad block id '{}'", profile.id(), entry);
                } else if (tag) {
                    tags.add(TagKey.create(Registries.BLOCK, id));
                } else {
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
                    if (block.isPresent()) {
                        blocks.add(block.get());
                    } else {
                        AlpineShred.LOGGER.debug("surface_profiles/{}: unknown block '{}' ignored", profile.id(), entry);
                    }
                }
            }
            if (!blocks.isEmpty() || !tags.isEmpty()) out.add(new Matcher(profile, Set.copyOf(blocks), List.copyOf(tags)));
        }
        return List.copyOf(out);
    }
}
