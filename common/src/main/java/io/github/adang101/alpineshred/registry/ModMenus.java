package io.github.adang101.alpineshred.registry;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.design.PaintBenchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

/** Menus (design doc section 22: the Paint Bench). */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(AlpineShred.MOD_ID, Registries.MENU);

    public static final RegistrySupplier<MenuType<PaintBenchMenu>> PAINT_BENCH = MENUS.register("paint_bench",
            () -> MenuRegistry.of(PaintBenchMenu::new));

    private ModMenus() {
    }

    public static void register() {
        MENUS.register();
    }
}
