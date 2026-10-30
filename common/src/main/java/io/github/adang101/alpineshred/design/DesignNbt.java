package io.github.adang101.alpineshred.design;

import io.github.adang101.alpineshred.gear.GearKind;
import io.github.adang101.alpineshred.gear.GearProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The {@code Design} key on a board or skis (design doc section 22, "Data format"). Other keys
 * ({@code Wax}, {@code Edge}, {@code display}) are never touched. A tag of an unknown version, or
 * one that does not decode, reads as no design (the default look) and is left as it is.
 */
public final class DesignNbt {
    public static final String KEY = "Design";
    /** Reading is lenient up to the largest {@code design.maxCompressedBytes} a server may set. */
    public static final int READ_MAX_COMPRESSED = 16384;

    private DesignNbt() {
    }

    /** The design kind for a stack: boards are boards, skis are skis, anything else null. */
    @Nullable
    public static DesignKind kindOf(ItemStack stack) {
        GearKind kind = GearProfile.kindOf(stack);
        if (kind == null) return null;
        return kind == GearKind.SKIS ? DesignKind.SKI : DesignKind.BOARD;
    }

    @Nullable
    public static CompoundTag tag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(KEY, Tag.TAG_COMPOUND) ? tag.getCompound(KEY) : null;
    }

    public static boolean has(ItemStack stack) {
        return tag(stack) != null;
    }

    /** The stored hash (the texture cache's key) without decoding, or null when there is no readable v1 design. */
    @Nullable
    public static Integer hash(ItemStack stack) {
        CompoundTag d = tag(stack);
        if (d == null || d.getInt("v") != Design.VERSION || !d.contains("hash", Tag.TAG_INT)) return null;
        return d.getInt("hash");
    }

    /** Decodes the stack's design, or null for none, another version, the wrong kind, or bad data. */
    @Nullable
    public static Design read(ItemStack stack) {
        DesignKind expected = kindOf(stack);
        CompoundTag d = tag(stack);
        if (expected == null || d == null) return null;
        return read(d, expected);
    }

    @Nullable
    public static Design read(CompoundTag d, DesignKind expected) {
        if (d.getInt("v") != Design.VERSION) return null;
        DesignKind kind = DesignKind.byId(d.getString("kind"));
        if (kind != expected) return null;
        try {
            UUID authorId = d.hasUUID("authorId") ? d.getUUID("authorId") : null;
            return DesignCodec.decode(kind, d.getByteArray("top"), d.getByteArray("base"), d.getInt("edge"),
                    Design.Pair.byId(d.getString("pair")), d.getString("name"), d.getString("author"), authorId, READ_MAX_COMPRESSED);
        } catch (DesignCodec.InvalidDesign e) {
            return null;
        }
    }

    /** Writes the design over any previous one. */
    public static void write(ItemStack stack, Design design) {
        stack.getOrCreateTag().put(KEY, toTag(design));
    }

    public static CompoundTag toTag(Design design) {
        CompoundTag d = new CompoundTag();
        d.putInt("v", Design.VERSION);
        d.putString("kind", design.kind().id);
        d.putByteArray("top", DesignCodec.topZ(design));
        d.putByteArray("base", DesignCodec.baseZ(design));
        d.putInt("edge", design.edge());
        if (design.kind() == DesignKind.SKI) d.putString("pair", design.pair().id());
        d.putString("name", design.name());
        d.putString("author", design.author());
        if (design.authorId() != null) d.put("authorId", NbtUtils.createUUID(design.authorId()));
        d.putInt("hash", design.hash());
        return d;
    }

    /** Reset: the design is removed, and the tag with it when nothing else is left. */
    public static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(KEY);
        if (tag.isEmpty()) stack.setTag(null);
    }
}
