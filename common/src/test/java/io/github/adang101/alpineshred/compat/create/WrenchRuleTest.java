package io.github.adang101.alpineshred.compat.create;

import io.github.adang101.alpineshred.compat.create.WrenchRule.Action;
import io.github.adang101.alpineshred.compat.create.WrenchRule.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WrenchRuleTest {
    @Test
    void aPlainClickOnAParkPieceRunsTheParkWrench() {
        assertEquals(Action.PARK_CLICK, WrenchRule.decide(Kind.PARK, false));
    }

    @Test
    void aSneakClickOnAParkPiecePicksItUp() {
        assertEquals(Action.PICK_UP, WrenchRule.decide(Kind.PARK, true));
    }

    @Test
    void aPlainClickOnALiftBlockOnlyHints() {
        assertEquals(Action.LIFT_HINT, WrenchRule.decide(Kind.LIFT, false));
    }

    @Test
    void aSneakClickOnALiftBlockPicksItUp() {
        assertEquals(Action.PICK_UP, WrenchRule.decide(Kind.LIFT, true));
    }

    @Test
    void otherBlocksAreLeftAloneWhicheverWay() {
        assertEquals(Action.PASS, WrenchRule.decide(Kind.OTHER, false));
        assertEquals(Action.PASS, WrenchRule.decide(Kind.OTHER, true));
    }
}
