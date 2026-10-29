package io.github.adang101.alpineshred.client.trick;

import net.minecraft.network.chat.Component;

/**
 * Display names for trick ids (section 8): {@code spin_360} is "360", {@code flip_back_2} "Double
 * back flip", {@code grind_nose_switch} "Switch nose grind". Unknown ids show as themselves.
 */
public final class TrickNames {
    private TrickNames() {
    }

    public static Component label(String id) {
        if (id.startsWith("spin_") && id.substring(5).chars().allMatch(Character::isDigit)) {
            return Component.translatable("trick.alpineshred.spin", id.substring(5));
        }
        if (id.startsWith("flip_front_") || id.startsWith("flip_back_")) {
            boolean front = id.startsWith("flip_front_");
            String n = id.substring(front ? 11 : 10);
            String key = "trick.alpineshred.flip_" + (front ? "front" : "back") + ("1".equals(n) ? "" : "_n");
            return Component.translatable(key, n);
        }
        boolean switched = id.endsWith("_switch");
        String base = switched ? id.substring(0, id.length() - 7) : id;
        Component name;
        if (base.startsWith("slide_")) {
            name = Component.translatable("trick.alpineshred.slide", Component.translatable("trick.alpineshred.grind_" + base.substring(6)));
        } else {
            name = Component.translatable("trick.alpineshred." + base);
        }
        return switched ? Component.translatable("trick.alpineshred.switch", name) : name;
    }
}
