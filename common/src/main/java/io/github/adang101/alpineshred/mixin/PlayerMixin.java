package io.github.adang101.alpineshred.mixin;

import io.github.adang101.alpineshred.movement.RideHooks;
import io.github.adang101.alpineshred.movement.RideStateHolder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rider state and movement hand-off on every player (design doc section 7, mixin inventory):
 * <ul>
 *   <li>Synched data: the packed ride-state accessor (value 0 is {@code RideSnapshot.OFF}).</li>
 *   <li>Travel: hands movement to the slide controller while riding (the local player).</li>
 *   <li>Edge back-off: sneaking brakes while riding, so it must not stop the rider at ledges. The
 *       server replays every client move and must not back off either, or it lands short of the
 *       client and rubber-bands it (risk R2).</li>
 *   <li>Jump: the pop replaces vanilla's jump while riding.</li>
 *   <li>Movement emission: no footsteps while sliding.</li>
 * </ul>
 */
@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements RideStateHolder {
    @Unique
    private static final EntityDataAccessor<Integer> ALPINESHRED$RIDE_STATE =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);

    @Unique
    private boolean alpineshred$controlled;
    @Unique
    private boolean alpineshred$floatsOnPowder;
    @Unique
    private float alpineshred$rideStep;

    protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void alpineshred$defineRideState(CallbackInfo ci) {
        this.entityData.define(ALPINESHRED$RIDE_STATE, 0);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void alpineshred$ride(Vec3 travelVector, CallbackInfo ci) {
        if (RideHooks.travel((Player) (Object) this)) ci.cancel();
    }

    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void alpineshred$noEdgeBackOffWhileRiding(CallbackInfoReturnable<Boolean> cir) {
        if (RideHooks.suppressEdgeBackOff((Player) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void alpineshred$popReplacesJump(CallbackInfo ci) {
        if (RideHooks.suppressVanillaJump((Player) (Object) this)) ci.cancel();
    }

    /** No footstep sounds while sliding; game events (sculk) still fire. Sliding sounds are phase 3. */
    @Inject(method = "getMovementEmission", at = @At("HEAD"), cancellable = true)
    private void alpineshred$silentFeet(CallbackInfoReturnable<Entity.MovementEmission> cir) {
        if (RideHooks.silentFeet((Player) (Object) this)) cir.setReturnValue(Entity.MovementEmission.EVENTS);
    }

    @Override
    public int alpineshred$getRideBits() {
        return this.entityData.get(ALPINESHRED$RIDE_STATE);
    }

    @Override
    public void alpineshred$setRideBits(int bits) {
        this.entityData.set(ALPINESHRED$RIDE_STATE, bits);
    }

    @Override
    public boolean alpineshred$isControlled() {
        return alpineshred$controlled;
    }

    @Override
    public void alpineshred$setControlled(boolean controlled) {
        this.alpineshred$controlled = controlled;
    }

    @Override
    public float alpineshred$rideStep() {
        return alpineshred$rideStep;
    }

    @Override
    public void alpineshred$setRideStep(float step) {
        this.alpineshred$rideStep = step;
    }

    @Override
    public boolean alpineshred$floatsOnPowder() {
        return alpineshred$floatsOnPowder;
    }

    @Override
    public void alpineshred$setFloatsOnPowder(boolean floats) {
        this.alpineshred$floatsOnPowder = floats;
    }
}
