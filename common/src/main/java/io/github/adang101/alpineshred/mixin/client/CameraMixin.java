package io.github.adang101.alpineshred.mixin.client;

import io.github.adang101.alpineshred.client.CameraOrbit;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link CameraOrbit}: when it is on and the camera is detached (third person), re-aim the camera
 * from the player's eyes at the orbit yaw and pull it back, with vanilla's own wall clipping.
 * Nothing changes while the orbit is off.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void move(double distanceOffset, double verticalOffset, double horizontalOffset);

    @Shadow
    private double getMaxZoom(double maxZoom) {
        throw new AssertionError();
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void alpineshred$orbit(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                   float partialTick, CallbackInfo ci) {
        if (!detached || !CameraOrbit.active()) return;
        setRotation(entity.getViewYRot(partialTick) + CameraOrbit.yawOffset(), CameraOrbit.pitch());
        Vec3 eyes = entity.getEyePosition(partialTick);
        setPosition(eyes.x, eyes.y, eyes.z);
        move(-getMaxZoom(CameraOrbit.distance()), 0, 0);
    }
}
