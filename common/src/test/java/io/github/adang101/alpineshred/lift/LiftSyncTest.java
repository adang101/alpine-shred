package io.github.adang101.alpineshred.lift;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lift sync packet's wire format and the pure catch-up rules the server and clients share. */
class LiftSyncTest {
    private static FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void aFullSyncRoundTrips() {
        LiftLineSync sync = new LiftLineSync(UUID.randomUUID(), false,
                List.of(new BlockPos(1, 70, -3), new BlockPos(41, 64, 5), new BlockPos(-300, 10, 90000)),
                24, 123.456789012345, 1.3, 2.5, 9_876_543_210L, true, false, true);
        FriendlyByteBuf b = buf();
        sync.write(b);
        assertEquals(sync, LiftLineSync.read(b));
        assertEquals(0, b.readableBytes());
        for (int flags = 0; flags < 8; flags++) {
            LiftLineSync f = new LiftLineSync(sync.id(), false, sync.nodes(), 24, 0, 0, 0, 0,
                    (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
            FriendlyByteBuf fb = buf();
            f.write(fb);
            assertEquals(f, LiftLineSync.read(fb));
        }
    }

    @Test
    void aRemovedSyncCarriesOnlyTheId() {
        UUID id = UUID.randomUUID();
        FriendlyByteBuf b = buf();
        LiftLineSync.removed(id).write(b);
        assertEquals(17, b.readableBytes());
        LiftLineSync back = LiftLineSync.read(b);
        assertNotNull(back);
        assertTrue(back.removed());
        assertEquals(id, back.id());
    }

    @Test
    void malformedSyncsAreRejected() {
        FriendlyByteBuf b = buf();
        b.writeUUID(UUID.randomUUID());
        b.writeBoolean(false);
        b.writeVarInt(5000);
        assertNull(LiftLineSync.read(b));
        FriendlyByteBuf truncated = buf();
        truncated.writeUUID(UUID.randomUUID());
        truncated.writeBoolean(false);
        truncated.writeVarInt(2);
        assertNull(LiftLineSync.read(truncated));
    }

    @Test
    void targetIsTheDrivesOnlyWhilePoweredAndRunning() {
        assertEquals(2.5, LiftMotion.targetFor(true, false, false, 2.5));
        assertEquals(0, LiftMotion.targetFor(false, false, false, 2.5));
        assertEquals(0, LiftMotion.targetFor(true, true, false, 2.5));
        assertEquals(0, LiftMotion.targetFor(true, false, true, 2.5));
        assertEquals(0, LiftMotion.targetFor(true, true, true, 2.5));
    }

    @Test
    void faultedAndEmergencyLinesStopAtTheEmergencyRate() {
        assertFalse(LiftMotion.isStopping(false, false));
        assertTrue(LiftMotion.isStopping(true, false));
        assertTrue(LiftMotion.isStopping(false, true));
        assertTrue(LiftMotion.isStopping(true, true));
    }

    @Test
    void catchUpWithTheSharedRulesEqualsStepping() {
        double speed = 1.0, travel = 10.0, loop = 400;
        double target = LiftMotion.targetFor(true, false, false, 2.5);
        double[] a = LiftMotion.advance(speed, travel, target, LiftMotion.isStopping(false, false), loop, 137);
        double s = speed, t = travel;
        for (int i = 0; i < 137; i++) {
            double[] n = LiftMotion.step(s, t, target, false, loop);
            s = n[0];
            t = n[1];
        }
        assertEquals(s, a[0], 1e-9);
        assertEquals(t, a[1], 1e-9);
    }
}
