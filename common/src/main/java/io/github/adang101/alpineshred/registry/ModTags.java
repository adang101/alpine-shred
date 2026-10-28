package io.github.adang101.alpineshred.registry;

import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Every tag the mod reads (design doc section 18, "Tags"). */
public final class ModTags {
    private ModTags() {
    }

    public static final class Items {
        public static final TagKey<Item> BOARDS = tag("gear/boards");
        public static final TagKey<Item> SKIS = tag("gear/skis");
        public static final TagKey<Item> POLES = tag("gear/poles");
        public static final TagKey<Item> BINDING_BOARD = tag("binding/board");
        public static final TagKey<Item> BINDING_SKI = tag("binding/ski");
        public static final TagKey<Item> REPAIR_SOFT_GEAR = tag("repair/soft_gear");
        public static final TagKey<Item> DESIGN_COST = tag("design_cost");

        private Items() {
        }

        private static TagKey<Item> tag(String path) {
            return TagKey.create(Registries.ITEM, AlpineShred.id(path));
        }
    }

    public static final class Blocks {
        public static final TagKey<Block> SLIDE_SNOW = tag("slide/snow");
        public static final TagKey<Block> SLIDE_ICE = tag("slide/ice");
        public static final TagKey<Block> SLIDE_POWDER = tag("slide/powder");
        public static final TagKey<Block> SLIDE_BOXES = tag("slide/boxes");
        public static final TagKey<Block> GRINDABLE = tag("grindable");
        public static final TagKey<Block> GRINDABLE_VANILLA = tag("grindable_vanilla");

        private Blocks() {
        }

        private static TagKey<Block> tag(String path) {
            return TagKey.create(Registries.BLOCK, AlpineShred.id(path));
        }
    }
}
