package io.github.adang101.alpineshred.mixin;

import io.github.adang101.alpineshred.movement.RideStateHolder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the packed ride-state accessor to every player (design doc section 7, mixin inventory
 * "Synched data"). Value 0 is {@code RideSnapshot.OFF}. The mod is required on both sides
 * (section 2), so client and server always agree on the accessor id.
 */
@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements RideStateHolder {
    @Unique
    private static final EntityDataAccessor<Integer> ALPINESHRED$RIDE_STATE =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);

    protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void alpineshred$defineRideState(CallbackInfo ci) {
        this.entityData.define(ALPINESHRED$RIDE_STATE, 0);
    }

    @Override
    public int alpineshred$getRideBits() {
        return this.entityData.get(ALPINESHRED$RIDE_STATE);
    }

    @Override
    public void alpineshred$setRideBits(int bits) {
        this.entityData.set(ALPINESHRED$RIDE_STATE, bits);
    }
}
