package io.github.adang101.alpineshred.lift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoBoardTest {
    private static boolean board(boolean strapped, boolean riding, double h, double v, boolean free, boolean stretch) {
        return AutoBoard.shouldBoard(strapped, riding, h, v, free, stretch);
    }

    @Test
    void aStrappedPlayerNextToAFreeChairInTheStretchBoards() {
        assertTrue(board(true, false, 0.5, 0.4, true, true));
    }

    @Test
    void horizontalToleranceIsOnePointTwo() {
        assertTrue(board(true, false, 1.2, 0, true, true));
        assertFalse(board(true, false, 1.21, 0, true, true));
    }

    @Test
    void verticalToleranceIsTwoPointFiveEitherWay() {
        assertTrue(board(true, false, 0, 2.5, true, true));
        assertTrue(board(true, false, 0, -2.5, true, true));
        assertFalse(board(true, false, 0, 2.51, true, true));
        assertFalse(board(true, false, 0, -2.51, true, true));
    }

    @Test
    void aFullChairIsNotBoarded() {
        assertFalse(board(true, false, 0.5, 0.4, false, true));
    }

    @Test
    void aChairOutsideTheBoardingStretchIsNotBoarded() {
        assertFalse(board(true, false, 0.5, 0.4, true, false));
    }

    @Test
    void aPlayerWhoIsNotStrappedDoesNotAutoBoard() {
        assertFalse(board(false, false, 0.5, 0.4, true, true));
    }

    @Test
    void aPlayerAlreadyRidingDoesNotAutoBoard() {
        assertFalse(board(true, true, 0.5, 0.4, true, true));
    }

    @Test
    void theServerAllowsSlackOnBothAxes() {
        assertTrue(AutoBoard.serverAccepts(2.6, 3.9));
        assertFalse(AutoBoard.serverAccepts(2.8, 0));
        assertFalse(AutoBoard.serverAccepts(0, 4.1));
        assertEquals(1.2 + AutoBoard.SERVER_SLACK, 2.7, 1e-12);
    }
}
