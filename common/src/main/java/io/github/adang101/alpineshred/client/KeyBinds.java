package io.github.adang101.alpineshred.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings (design doc section 18), category {@code key.categories.alpineshred}. Steering,
 * braking, jumping, flips and the Method grab use vanilla bindings.
 *
 * <p>Default {@code X} and {@code C} also belong to vanilla's creative hotbar save/load activators;
 * they only act together with a number key in creative, but the controls screen marks them as
 * conflicts. Players can rebind either set.
 */
public final class KeyBinds {
    public static final String CATEGORY = "key.categories.alpineshred";

    public static final KeyMapping STRAP = key("strap", GLFW.GLFW_KEY_G);
    public static final KeyMapping GRAB_INDY = key("grab_indy", GLFW.GLFW_KEY_Z);
    public static final KeyMapping GRAB_MUTE = key("grab_mute", GLFW.GLFW_KEY_X);
    public static final KeyMapping GRAB_NOSE = key("grab_nose", GLFW.GLFW_KEY_C);
    public static final KeyMapping GRAB_TAIL = key("grab_tail", GLFW.GLFW_KEY_V);
    public static final KeyMapping STANCE = key("stance", InputConstants.UNKNOWN.getValue());

    private KeyBinds() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.alpineshred." + name, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    public static void register() {
        KeyMappingRegistry.register(STRAP);
        KeyMappingRegistry.register(GRAB_INDY);
        KeyMappingRegistry.register(GRAB_MUTE);
        KeyMappingRegistry.register(GRAB_NOSE);
        KeyMappingRegistry.register(GRAB_TAIL);
        KeyMappingRegistry.register(STANCE);
    }
}
