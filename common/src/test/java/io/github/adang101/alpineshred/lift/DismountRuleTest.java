package io.github.adang101.alpineshred.lift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DismountRuleTest {
    @Test
    void aClosedBarHighAboveTheGroundRefusesTheDismount() {
        assertFalse(DismountRule.allowed(true, 12, false, false));
    }

    @Test
    void anOpenBarAllowsIt() {
        assertTrue(DismountRule.allowed(false, 12, false, false));
    }

    @Test
    void closeToTheGroundAllowsIt() {
        assertTrue(DismountRule.allowed(true, 4.0, false, false));
        assertFalse(DismountRule.allowed(true, 4.01, false, false));
        assertTrue(DismountRule.allowed(true, -0.5, false, false)); // seat inside a block
    }

    @Test
    void aFaultedLineAllowsIt() {
        assertTrue(DismountRule.allowed(true, 30, true, false));
    }

    @Test
    void dismountAnywhereAllowsIt() {
        assertTrue(DismountRule.allowed(true, 30, false, true));
    }
}
