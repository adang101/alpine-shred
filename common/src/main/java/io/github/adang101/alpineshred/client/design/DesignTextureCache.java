package io.github.adang101.alpineshred.client.design;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.design.Design;
import io.github.adang101.alpineshred.design.DesignAtlas;
import io.github.adang101.alpineshred.design.DesignKind;
import io.github.adang101.alpineshred.design.DesignNbt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Textures for designed gear (design doc section 22, "Rendering"). {@link #texture} gives the
 * atlas to draw a stack with: its design's dynamic texture, keyed by the item and the design's
 * hash, or the item's default look ({@code textures/gear/<item>.png}) when it has no design, the
 * design is someone else's and {@code client.showCustomDesigns} is off, or its texture is still
 * queued. At most {@code client.designCacheSize} textures are kept (least recently used go, and are
 * closed), at most {@value #BUILDS_PER_FRAME} are built per frame, and a resource reload clears it.
 */
public final class DesignTextureCache {
    public static final int BUILDS_PER_FRAME = 2;
    /** The budget window: about one frame at 60 frames per second. */
    private static final long FRAME_NANOS = 16_000_000L;

    private record Key(ResourceLocation item, int hash) {
    }

    private static final LinkedHashMap<Key, ResourceLocation> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    /** Default atlases read from the resource packs, per item (null entry: missing or unreadable). */
    private static final Map<ResourceLocation, Optional<int[]>> DEFAULTS = new HashMap<>();
    private static long window = Long.MIN_VALUE;
    private static int builtInWindow;
    private static int created;
    private static long buildNanos, maxBuildNanos;
    private static int builds;

    private DesignTextureCache() {
    }

    /** The default look's texture for an item: {@code <namespace>:textures/gear/<path>.png}. */
    public static ResourceLocation defaultTexture(ResourceLocation item) {
        return new ResourceLocation(item.getNamespace(), "textures/gear/" + item.getPath() + ".png");
    }

    /**
     * The atlas texture to draw {@code stack} with.
     *
     * @param custom false to draw the default look whatever the stack holds
     */
    public static ResourceLocation texture(ItemStack stack, boolean custom) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation fallback = defaultTexture(item);
        DesignKind kind = DesignNbt.kindOf(stack);
        Integer hash = custom ? DesignNbt.hash(stack) : null;
        if (kind == null || hash == null) return fallback;
        Key key = new Key(item, hash);
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) return cached;
        if (!budget()) return fallback;
        Design design = DesignNbt.read(stack);
        if (design == null) {
            CACHE.put(key, fallback); // unreadable: remember to draw the default look
            return fallback;
        }
        long start = System.nanoTime();
        ResourceLocation built = upload(kind, DesignAtlas.build(kind, design, defaults(item, kind), 0x808080));
        long took = System.nanoTime() - start;
        buildNanos += took;
        maxBuildNanos = Math.max(maxBuildNanos, took);
        builds++;
        CACHE.put(key, built);
        trim();
        return built;
    }

    /** Whether one more texture may be built in this frame's window. */
    private static boolean budget() {
        long now = System.nanoTime() / FRAME_NANOS;
        if (now != window) {
            window = now;
            builtInWindow = 0;
        }
        if (builtInWindow >= BUILDS_PER_FRAME) return false;
        builtInWindow++;
        return true;
    }

    private static void trim() {
        int max = AlpineConfig.CLIENT_DESIGN_CACHE_SIZE.value();
        Iterator<Map.Entry<Key, ResourceLocation>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > max && it.hasNext()) {
            ResourceLocation id = it.next().getValue();
            it.remove();
            release(id);
        }
    }

    /** Uploads an ARGB atlas as a new dynamic texture. */
    static ResourceLocation upload(DesignKind kind, int[] argb) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, kind.width, kind.atlasHeight(), false);
        fill(image, kind, argb);
        DynamicTexture texture = new DynamicTexture(image);
        created++;
        return Minecraft.getInstance().getTextureManager().register("alpineshred_design_" + created, texture);
    }

    /** Writes an ARGB atlas into an image (NativeImage pixels are ABGR). */
    static void fill(NativeImage image, DesignKind kind, int[] argb) {
        for (int y = 0; y < kind.atlasHeight(); y++) {
            for (int x = 0; x < kind.width; x++) image.setPixelRGBA(x, y, abgr(argb[y * kind.width + x]));
        }
    }

    static int abgr(int argb) {
        return (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >> 16) & 0xFF;
    }

    private static void release(ResourceLocation id) {
        if (id.getNamespace().equals("minecraft") && id.getPath().startsWith("dynamic/")) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    /** The default atlas of {@code item} as ARGB, or null when its texture is missing or the wrong size. */
    public static int[] defaults(ResourceLocation item, DesignKind kind) {
        return DEFAULTS.computeIfAbsent(item, i -> Optional.ofNullable(readDefaults(i, kind))).orElse(null);
    }

    private static int[] readDefaults(ResourceLocation item, DesignKind kind) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(defaultTexture(item));
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            if (image.getWidth() != kind.width || image.getHeight() != kind.atlasHeight()) {
                AlpineShred.LOGGER.warn("{} is {}x{}, not the {}x{} atlas", defaultTexture(item), image.getWidth(), image.getHeight(),
                        kind.width, kind.atlasHeight());
                return null;
            }
            int[] out = new int[kind.width * kind.atlasHeight()];
            for (int y = 0; y < kind.atlasHeight(); y++) {
                for (int x = 0; x < kind.width; x++) out[y * kind.width + x] = abgr(image.getPixelRGBA(x, y));
            }
            return out;
        } catch (Exception e) {
            AlpineShred.LOGGER.warn("Could not read {}: {}", defaultTexture(item), e.getMessage());
            return null;
        }
    }

    /** Resource reload (and leaving a world): every texture closed, defaults read again. */
    public static void clear() {
        for (ResourceLocation id : CACHE.values()) release(id);
        CACHE.clear();
        DEFAULTS.clear();
    }

    /** Section 22's budget read-out: textures built, their mean and longest build time in milliseconds. */
    public static String buildStats() {
        return String.format(java.util.Locale.ROOT, "%d built, mean %.3f ms, longest %.3f ms", builds,
                builds == 0 ? 0 : buildNanos / 1.0e6 / builds, maxBuildNanos / 1.0e6);
    }

    public static double meanBuildMillis() {
        return builds == 0 ? 0 : buildNanos / 1.0e6 / builds;
    }

    /** Textures held now (for the debug overlay and tests). */
    public static int size() {
        return CACHE.size();
    }
}
