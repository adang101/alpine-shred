package io.github.adang101.alpineshred.lift;

import org.junit.jupiter.api.Test;

import static io.github.adang101.alpineshred.lift.BoardingRule.Refusal.FULL;
import static io.github.adang101.alpineshred.lift.BoardingRule.Refusal.NONE;
import static io.github.adang101.alpineshred.lift.BoardingRule.Refusal.NO_PASS;
import static io.github.adang101.alpineshred.lift.BoardingRule.Refusal.TOO_FAR;
import static io.github.adang101.alpineshred.lift.BoardingRule.Refusal.TOO_FAST;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BoardingRuleTest {
    @Test
    void everythingInOrderBoards() {
        assertEquals(NONE, BoardingRule.check(2, 3, true, 2.5, 4.0, true, true));
        assertEquals(NONE, BoardingRule.check(3, 3, true, 4.0, 4.0, false, false)); // limits are inclusive
    }

    @Test
    void eachRefusalOnItsOwn() {
        assertEquals(TOO_FAR, BoardingRule.check(3.1, 3, true, 1, 4, false, false));
        assertEquals(FULL, BoardingRule.check(1, 3, false, 1, 4, false, false));
        assertEquals(TOO_FAST, BoardingRule.check(1, 3, true, 4.5, 4, false, false));
        assertEquals(NO_PASS, BoardingRule.check(1, 3, true, 1, 4, true, false));
    }

    @Test
    void theFirstReasonWins() {
        assertEquals(TOO_FAR, BoardingRule.check(9, 3, false, 9, 4, true, false));
        assertEquals(FULL, BoardingRule.check(1, 3, false, 9, 4, true, false));
        assertEquals(TOO_FAST, BoardingRule.check(1, 3, true, 9, 4, true, false));
    }
}
