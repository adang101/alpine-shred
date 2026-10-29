package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.movement.SurfaceProfile;
import io.github.adang101.alpineshred.movement.TuningData;
import io.github.adang101.alpineshred.park.BoxMaterial;
import io.github.adang101.alpineshred.park.GrindMaterial;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Matches blocks against {@code box_materials.json} (design doc section 11): what a box or a grind
 * block is made of and its friction. Box surfaces become a {@link SurfaceProfile} of category
 * {@code BOX} per material, so the slide controller reads the friction like any other surface.
 */
public final class BoxMaterials {
    private record Matcher(BoxMaterial entry, Set<Block> blocks, List<TagKey<Block>> tags) {
        boolean matches(BlockState state) {
            if (blocks.contains(state.getBlock())) return true;
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) return true;
            }
            return false;
        }
    }

    private static volatile List<BoxMaterial> compiledFrom;
    private static volatile List<Matcher> matchers = List.of();
    private static final Map<String, SurfaceProfile> PROFILES = new ConcurrentHashMap<>();

    private BoxMaterials() {
    }

    @Nullable
    public static BoxMaterial forState(BlockState state) {
        for (Matcher m : matchers()) {
            if (m.matches(state)) return m.entry();
        }
        return null;
    }

    /** Friction of a block from the table, or the material's default when no entry names it. */
    public static double friction(BlockState state, GrindMaterial fallback) {
        BoxMaterial entry = forState(state);
        if (entry != null) return entry.friction();
        for (BoxMaterial d : BoxMaterial.DEFAULTS) {
            if (d.material() == fallback) return d.friction();
        }
        return SurfaceProfile.BOX.friction();
    }

    public static GrindMaterial material(BlockState state) {
        BoxMaterial entry = forState(state);
        if (entry != null) return entry.material();
        if (state.getBlock() instanceof BoxBlock box) return box.material();
        return GrindMaterial.STONE;
    }

    /**
     * The surface a box-tagged block slides like: its material's friction, halved (by default) when
     * waxed. Ids are {@code box_<material>} and {@code box_<material>_waxed}.
     */
    public static SurfaceProfile profile(BlockState state) {
        BoxMaterial entry = forState(state);
        GrindMaterial material = entry != null ? entry.material() : material(state);
        double friction = entry != null ? entry.friction() : SurfaceProfile.BOX.friction();
        boolean waxed = state.hasProperty(ParkStates.WAXED) && state.getValue(ParkStates.WAXED);
        if (waxed) friction *= TuningData.tuning().boxWaxedFrictionMul;
        String id = "box_" + material.serializedName() + (waxed ? "_waxed" : "");
        double f = friction;
        SurfaceProfile cached = PROFILES.get(id);
        if (cached == null || cached.friction() != f) {
            cached = new SurfaceProfile(id, SurfaceProfile.Category.BOX, f, SurfaceProfile.BOX.grip(), SurfaceProfile.BOX.priority(), List.of());
            PROFILES.put(id, cached);
        }
        return cached;
    }

    /** The material of a box surface profile made by {@link #profile}, or null. */
    @Nullable
    public static GrindMaterial materialOf(SurfaceProfile profile) {
        if (profile == null || profile.category() != SurfaceProfile.Category.BOX) return null;
        String id = profile.id();
        if (!id.startsWith("box_")) return GrindMaterial.STONE;
        String name = id.substring(4).replace("_waxed", "");
        GrindMaterial m = GrindMaterial.byName(name);
        return m != null ? m : GrindMaterial.STONE;
    }

    private static List<Matcher> matchers() {
        List<BoxMaterial> current = TuningData.boxMaterials();
        if (current != compiledFrom) {
            synchronized (BoxMaterials.class) {
                if (current != compiledFrom) {
                    matchers = compile(current);
                    compiledFrom = current;
                }
            }
        }
        return matchers;
    }

    private static List<Matcher> compile(List<BoxMaterial> table) {
        List<Matcher> out = new ArrayList<>();
        for (BoxMaterial entry : table) {
            Set<Block> blocks = new HashSet<>();
            List<TagKey<Block>> tags = new ArrayList<>();
            for (String s : entry.blocks()) {
                boolean tag = s.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? s.substring(1) : s);
                if (id == null) {
                    AlpineShred.LOGGER.warn("box_materials: bad block id '{}'", s);
                } else if (tag) {
                    tags.add(TagKey.create(Registries.BLOCK, id));
                } else {
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
                    block.ifPresent(blocks::add);
                }
            }
            if (!blocks.isEmpty() || !tags.isEmpty()) out.add(new Matcher(entry, Set.copyOf(blocks), List.copyOf(tags)));
        }
        return List.copyOf(out);
    }
}
