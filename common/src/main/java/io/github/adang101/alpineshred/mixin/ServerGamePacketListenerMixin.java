package io.github.adang101.alpineshred.mixin;

import io.github.adang101.alpineshred.movement.RideHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Vanilla's "moved too quickly" check allows 10 blocks of movement per server tick, and counts more
 * than 5 move packets in one tick as one: a rider at full speed covers 10 blocks in about 13
 * client ticks, so any server stall over 0.6 seconds teleported every fast rider back. A rider the
 * slide controller moves has its speed checked by the ride validator on the rider clock, so for
 * them vanilla's allowance is raised ({@link RideHooks#vanillaMoveAllowance}).
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @ModifyConstant(method = "handleMovePlayer", constant = @Constant(floatValue = 100.0F))
    private float alpineshred$riderMoveAllowance(float allowance) {
        return RideHooks.vanillaMoveAllowance(player, allowance);
    }
}
