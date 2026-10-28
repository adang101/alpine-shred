package io.github.adang101.alpineshred.gear;

import io.github.adang101.alpineshred.registry.ModTags;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The {@code alpine} armor material (design doc section 5): defense feet 1 and head 2, durability
 * multiplier 14, enchantability 12, leather equip sound, repaired with {@code #alpineshred:repair/soft_gear}.
 *
 * <p>{@link #getName()} returns {@code alpineshred_alpine}, so the armor textures live at
 * {@code minecraft:textures/models/armor/alpineshred_alpine_layer_1.png}. A namespaced name
 * ({@code alpineshred:alpine}) works on Forge but makes vanilla's armor layer build an invalid
 * resource location on Fabric; the prefixed plain name works on both.
 */
public enum AlpineArmorMaterial implements ArmorMaterial {
    ALPINE;

    /** Vanilla's per-slot base durability (boots, leggings, chestplate, helmet order by type). */
    private static final int BOOTS_BASE = 13;
    private static final int LEGGINGS_BASE = 15;
    private static final int CHESTPLATE_BASE = 16;
    private static final int HELMET_BASE = 11;
    private static final int DURABILITY_MULTIPLIER = 14;

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        int base = switch (type) {
            case BOOTS -> BOOTS_BASE;
            case LEGGINGS -> LEGGINGS_BASE;
            case CHESTPLATE -> CHESTPLATE_BASE;
            case HELMET -> HELMET_BASE;
        };
        return base * DURABILITY_MULTIPLIER;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> 1;
            case HELMET -> 2;
            default -> 0;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(ModTags.Items.REPAIR_SOFT_GEAR);
    }

    @Override
    public String getName() {
        return "alpineshred_alpine";
    }

    @Override
    public float getToughness() {
        return 0;
    }

    @Override
    public float getKnockbackResistance() {
        return 0;
    }
}
