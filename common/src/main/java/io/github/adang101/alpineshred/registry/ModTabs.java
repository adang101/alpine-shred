package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.gear.GogglesItem;
import io.github.adang101.alpineshred.gear.Lens;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** One creative tab, {@code alpineshred:main}: gear, wearables, tools, park blocks, lift parts. */
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(AlpineShred.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeTabRegistry.create(builder -> builder
            .title(Component.translatable("itemGroup.alpineshred.main"))
            .icon(() -> new ItemStack(ModItems.SNOWBOARD.get()))
            .displayItems((parameters, output) -> {
                ModItems.boardsAndSkis().forEach(item -> output.accept(item.get()));
                output.accept(ModItems.SKI_BOOTS.get());
                output.accept(ModItems.SNOWBOARD_BOOTS.get());
                output.accept(ModItems.SKI_HELMET.get());
                for (Lens lens : Lens.values()) {
                    output.accept(GogglesItem.withLens(new ItemStack(ModItems.GOGGLES.get()), lens));
                }
                output.accept(ModItems.MITTS.get());
                output.accept(ModItems.SKI_POLES.get());
                output.accept(ModItems.CARBON_SKI_POLES.get());
                output.accept(ModItems.BOARD_WAX.get());
                output.accept(ModItems.EDGE_TUNER.get());
                output.accept(ModItems.PARK_WRENCH.get());
                output.accept(ModItems.LIFT_LINKER.get());
                output.accept(ModItems.LIFT_PASS.get());
                output.accept(ModItems.GROOMED_SNOW.get());
            })));

    private ModTabs() {
    }

    public static void register() {
        TABS.register();
    }
}
