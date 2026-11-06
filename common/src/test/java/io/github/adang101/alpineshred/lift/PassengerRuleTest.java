package io.github.adang101.alpineshred.lift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassengerRuleTest {
    private static final double W = 1.2, H = 2.4;

    private static boolean mob(double width, double height, boolean blocked, boolean listed, boolean mobsAllowed) {
        return PassengerRule.allows(false, true, width, height, blocked, listed, mobsAllowed, W, H);
    }

    @Test
    void aPlayerIsAllowed() {
        assertTrue(PassengerRule.allows(true, true, 0.6, 1.8, false, false, true, W, H));
    }

    @Test
    void aCowSizedMobIsAllowed() {
        assertTrue(mob(0.9, 1.4, false, false, true));
    }

    @Test
    void aBlockedMobIsRefused() {
        assertFalse(mob(0.9, 1.4, true, false, true));
    }

    @Test
    void aTooWideOrTooTallMobIsRefused() {
        assertFalse(mob(1.3, 1.4, false, false, true));
        assertFalse(mob(0.9, 2.5, false, false, true));
        assertTrue(mob(W, H, false, false, true)); // the limits are inclusive
    }

    @Test
    void aListedTypeIsAllowedEvenWhenTooBig() {
        assertTrue(mob(3.0, 5.0, false, true, true));
        assertTrue(mob(3.0, 5.0, false, true, false));
    }

    @Test
    void mobsOffRefusesMobsButNotPlayers() {
        assertFalse(mob(0.9, 1.4, false, false, false));
        assertTrue(PassengerRule.allows(true, true, 0.6, 1.8, false, false, false, W, H));
    }

    @Test
    void aNonLivingEntityIsRefusedUnlessListed() {
        assertFalse(PassengerRule.allows(false, false, 0.5, 0.5, false, false, true, W, H));
        assertTrue(PassengerRule.allows(false, false, 0.5, 0.5, false, true, true, W, H));
    }

    @Test
    void seatsAreLimited() {
        assertTrue(PassengerRule.hasFreeSeat(0, 2));
        assertTrue(PassengerRule.hasFreeSeat(1, 2));
        assertFalse(PassengerRule.hasFreeSeat(2, 2));
    }

    @Test
    void seatsAreCentredAndNinetyCentimetresApart() {
        assertEquals(0.0, PassengerRule.seatOffset(0, 1), 1e-12);
        assertEquals(-0.45, PassengerRule.seatOffset(0, 2), 1e-12);
        assertEquals(0.45, PassengerRule.seatOffset(1, 2), 1e-12);
        assertEquals(-0.9, PassengerRule.seatOffset(0, 3), 1e-12);
        assertEquals(0.9, PassengerRule.seatOffset(2, 3), 1e-12);
    }
}
