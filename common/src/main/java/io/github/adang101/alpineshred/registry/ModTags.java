package io.github.adang101.alpineshred.registry;

import io.github.adang101.alpineshred.AlpineShred;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
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

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> LIFT_BLOCKED = tag("lift_blocked");
        public static final TagKey<EntityType<?>> LIFT_PASSENGERS = tag("lift_passengers");

        private EntityTypes() {
        }

        private static TagKey<EntityType<?>> tag(String path) {
            return TagKey.create(Registries.ENTITY_TYPE, AlpineShred.id(path));
        }
    }

    public static final class Blocks {
        public static final TagKey<Block> SLIDE_SNOW = tag("slide/snow");
        public static final TagKey<Block> SLIDE_ICE = tag("slide/ice");
        public static final TagKey<Block> SLIDE_POWDER = tag("slide/powder");
        public static final TagKey<Block> SLIDE_BOXES = tag("slide/boxes");
        public static final TagKey<Block> GRINDABLE = tag("grindable");
        public static final TagKey<Block> GRINDABLE_VANILLA = tag("grindable_vanilla");
        /** Lift blocks Create's wrench may pick up (sneak-click). Other lift blocks can join by tag (README 80). */
        public static final TagKey<Block> WRENCH_LIFT_PICKUP = tag("wrench/lift_pickup");
        /** Blocks a rider sliding on gear passes through, slowed and with vision obscured (default: #minecraft:leaves). */
        public static final TagKey<Block> RIDE_THROUGH = tag("ride_through");
        /** Blocks a riding player wipes out on at a lower impact speed than other walls (default: logs, mangrove roots). */
        public static final TagKey<Block> TREE_TRUNKS = tag("tree_trunks");

        private Blocks() {
        }

        private static TagKey<Block> tag(String path) {
            return TagKey.create(Registries.BLOCK, AlpineShred.id(path));
        }
    }
}
