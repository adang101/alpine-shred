package io.github.adang101.alpineshred.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only view of the vanilla move-check state: the move packet counter clocks the ride validator
 * ({@code RideServer}), and the risk R2 game test, which drives {@code handleMovePlayer} directly,
 * answers the login teleport and watches the floating counter.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerAccessor {
    @Accessor("awaitingTeleport")
    int alpineshred$awaitingTeleport();

    @Accessor("awaitingPositionFromClient")
    Vec3 alpineshred$awaitingPosition();

    @Accessor("clientIsFloating")
    boolean alpineshred$clientIsFloating();

    /** Move packets accepted for checking since login; the ride validator's clock. */
    @Accessor("receivedMovePacketCount")
    int alpineshred$receivedMovePackets();
}
