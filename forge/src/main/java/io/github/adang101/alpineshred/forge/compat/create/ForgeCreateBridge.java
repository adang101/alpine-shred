package io.github.adang101.alpineshred.forge.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.architectury.platform.Platform;
import io.github.adang101.alpineshred.compat.create.CreateBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Forge's {@link CreateBridge}. The only class that names Create types for now; it is loaded solely through
 * {@link ForgeCreateCompat#init()}, which the entry point calls behind {@code PlatformHooks.isCreatePresent()}.
 */
final class ForgeCreateBridge implements CreateBridge {
    /** Create's kinetic base class: loaded here so the Create column proves the dependency resolves; the kinetic drive extends it. */
    private static final Class<?> KINETIC_BASE = KineticBlockEntity.class;
    private static final ResourceLocation SHAFT = new ResourceLocation("create", "shaft");

    /** Live once Create has registered its content, so a listed-but-broken Create jar does not pass. */
    @Override
    public boolean live() {
        return KINETIC_BASE != null && BuiltInRegistries.BLOCK.containsKey(SHAFT);
    }

    @Override
    public String version() {
        return Platform.getMod("create").getVersion();
    }
}
