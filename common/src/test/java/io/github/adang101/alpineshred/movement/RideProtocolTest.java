package io.github.adang101.alpineshred.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RideProtocolTest {

    @Test
    void clientsMayOnlyProposeRidingStatesWhileStrapped() {
        assertTrue(RideTransitions.clientMayRequest(RideState.IDLE, RideState.GLIDE));
        assertTrue(RideTransitions.clientMayRequest(RideState.CARVE, RideState.AIR));
        assertTrue(RideTransitions.clientMayRequest(RideState.AIR, RideState.WIPEOUT));
        assertFalse(RideTransitions.clientMayRequest(RideState.OFF, RideState.GLIDE), "strapping in is strap_toggle's job");
        assertFalse(RideTransitions.clientMayRequest(RideState.GLIDE, RideState.OFF));
        assertTrue(RideTransitions.clientMayRequest(RideState.AIR, RideState.GRIND), "allowed; the server checks the rail");
        assertTrue(RideTransitions.needsWorldCheck(RideState.GRIND));
        assertTrue(RideTransitions.needsWorldCheck(RideState.SLIDE));
        assertFalse(RideTransitions.needsWorldCheck(RideState.GLIDE));
        assertFalse(RideTransitions.clientMayRequest(RideState.GLIDE, RideState.LIFT));
        assertFalse(RideTransitions.clientMayRequest(RideState.LIFT, RideState.GLIDE));
    }

    @Test
    void changesSendAtMostTenPerSecond() {
        StateSendThrottle throttle = new StateSendThrottle();
        RideSnapshot glide = RideSnapshot.OFF.withState(RideState.GLIDE).withMode(RideMode.BOARD);
        assertTrue(throttle.shouldSend(100, glide.pack()));
        assertFalse(throttle.shouldSend(100, glide.pack()), "unchanged");
        assertFalse(throttle.shouldSend(101, glide.withMotion(0.5, 10, 0).pack()), "heading change within 2 ticks");
        assertTrue(throttle.shouldSend(102, glide.withMotion(0.5, 20, 0).pack()));
        assertFalse(throttle.shouldSend(103, glide.withState(RideState.AIR).pack()), "state changes wait too");
        assertTrue(throttle.shouldSend(104, glide.withState(RideState.AIR).pack()), "and go out on the next allowed tick");
    }

    @Test
    void tappingSteerKeysNeverExceedsTheServerRateLimit() {
        StateSendThrottle throttle = new StateSendThrottle();
        RideSnapshot glide = RideSnapshot.OFF.withState(RideState.GLIDE).withMode(RideMode.BOARD);
        int sent = 0;
        for (int tick = 0; tick < 200; tick++) {
            RideState state = tick % 2 == 0 ? RideState.GLIDE : RideState.CARVE; // flips every tick
            if (throttle.shouldSend(tick, glide.withState(state).pack())) sent++;
        }
        assertTrue(sent <= 100, "sent " + sent + " in 10 seconds");
    }
}
