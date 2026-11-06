package io.github.adang101.alpineshred.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla can only set a mob's persistence flag; lift chairs restore the original when a mob gets off. */
@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("persistenceRequired")
    void alpineshred$setPersistenceRequired(boolean value);
}
