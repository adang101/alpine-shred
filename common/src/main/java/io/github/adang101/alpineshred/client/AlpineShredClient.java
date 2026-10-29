package io.github.adang101.alpineshred.client;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import dev.architectury.registry.client.rendering.ColorHandlerRegistry;
import dev.architectury.registry.item.ItemPropertiesRegistry;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.client.feel.RideEffects;
import io.github.adang101.alpineshred.client.feel.RiderTracker;
import io.github.adang101.alpineshred.client.particle.SnowParticles;
import io.github.adang101.alpineshred.client.platform.ClientPlatformHooks;
import io.github.adang101.alpineshred.config.AlpineConfig;
import io.github.adang101.alpineshred.gear.GogglesItem;
import io.github.adang101.alpineshred.movement.RideState;
import io.github.adang101.alpineshred.movement.Riders;
import io.github.adang101.alpineshred.network.ModNetwork;
import io.github.adang101.alpineshred.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Client initialization (design doc section 3, rule 5): the only entry into {@code client/}, called
 * from the Forge client dist and the Fabric client entry point.
 */
public final class AlpineShredClient {
    /** Hint shown once per client the first time the rider actually slides (section 7). */
    public static final String HINT_STEER = "steer";

    private static RideState lastLocalState = RideState.OFF;

    private AlpineShredClient() {
    }

    public static void init() {
        AlpineConfig.loadClient(Platform.getConfigFolder(), AlpineShred.LOGGER::warn);
        KeyBinds.register();

        ColorHandlerRegistry.registerItemColors((stack, tintIndex) -> tintIndex > 0 ? -1 : dyeColor(stack),
                ModItems.SKI_BOOTS, ModItems.SNOWBOARD_BOOTS, ModItems.SKI_HELMET, ModItems.MITTS);

        ClientLifecycleEvent.CLIENT_SETUP.register(minecraft -> ItemPropertiesRegistry.register(ModItems.GOGGLES.get(),
                AlpineShred.id("lens"), (stack, level, entity, seed) -> GogglesItem.lens(stack).modelValue()));

        ClientPlatformHooks.registerPlayerRenderLayer(SnowGearLayer::new);
        ClientNetwork.register();
        RideDriver.INSTANCE.register();
        RideDebugOverlay.register();
        SnowParticles.register();
        RiderTracker.register();
        RideEffects.register();
        ClientSmoke.register();
        ClientTickEvent.CLIENT_POST.register(AlpineShredClient::clientTick);
    }

    private static int dyeColor(ItemStack stack) {
        return stack.getItem() instanceof DyeableLeatherItem dyeable ? dyeable.getColor(stack) : -1;
    }

    private static void clientTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            lastLocalState = RideState.OFF;
            return;
        }
        while (KeyBinds.STRAP.consumeClick()) {
            ModNetwork.sendStrapToggle(!Riders.isStrapped(player));
        }
        while (KeyBinds.STANCE.consumeClick()) {
            String next = AlpineConfig.CLIENT_STANCE.is("goofy") ? "regular" : "goofy";
            AlpineConfig.CLIENT_STANCE.set(next);
            AlpineConfig.CLIENT.save(Platform.getConfigFolder(), AlpineShred.LOGGER::warn);
            player.displayClientMessage(Component.translatable("message.alpineshred.stance." + next), true);
        }

        RideState state = RideDriver.INSTANCE.localState(player);
        if (state != lastLocalState) {
            onLocalStateChange(player, lastLocalState, state);
            lastLocalState = state;
        }
    }

    private static void onLocalStateChange(LocalPlayer player, RideState from, RideState to) {
        // The steering hint waits for the first real slide.
        if (to == RideState.GLIDE || to == RideState.CARVE) showHintOnce(player, HINT_STEER);
    }

    private static void showHintOnce(LocalPlayer player, String hint) {
        if (!AlpineConfig.CLIENT_HINTS.on()) return;
        List<String> seen = AlpineConfig.CLIENT_HINTS_SEEN.get();
        if (seen.contains(hint)) return;
        player.displayClientMessage(Component.translatable("message.alpineshred.hint." + hint), true);
        List<String> updated = new ArrayList<>(seen);
        updated.add(hint);
        AlpineConfig.CLIENT_HINTS_SEEN.set(List.copyOf(updated));
        AlpineConfig.CLIENT.save(Platform.getConfigFolder(), AlpineShred.LOGGER::warn);
    }
}
