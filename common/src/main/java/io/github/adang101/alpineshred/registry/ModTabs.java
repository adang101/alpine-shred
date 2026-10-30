package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.gear.GogglesItem;
import io.github.adang101.alpineshred.gear.Lens;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
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
                output.accept(ModItems.PAINT_BENCH.get());
                for (var item : ModItems.parkItems()) {
                    if (item.getId().getPath().equals("plastic_box")) {
                        for (DyeColor color : DyeColor.values()) output.accept(plasticBox(item.get(), color));
                    } else {
                        output.accept(item.get());
                    }
                }
            })));

    private ModTabs() {
    }

    /** A plastic box item that places in {@code color} (vanilla's {@code BlockStateTag}). */
    public static ItemStack plasticBox(net.minecraft.world.item.Item item, DyeColor color) {
        ItemStack stack = new ItemStack(item);
        if (color != DyeColor.WHITE) {
            CompoundTag states = new CompoundTag();
            states.putString("color", color.getSerializedName());
            stack.getOrCreateTag().put("BlockStateTag", states);
        }
        return stack;
    }

    public static void register() {
        TABS.register();
    }
}
