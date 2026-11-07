package io.github.adang101.alpineshred.client.lift;

import io.github.adang101.alpineshred.lift.LiftLineSync;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** README clarification 73: a sync that arrives with a time-packet jump must not be shifted by the jump twice. */
class LineClockTest {
    private static final UUID ID = UUID.randomUUID();
    private static final List<BlockPos> NODES = List.of(new BlockPos(0, 64, 0), new BlockPos(60, 80, 0));

    /** A line running steadily at 2.5 blocks per second, 0.125 per tick, with travel {@code 10 + 0.125 * (gt - 1000)}. */
    private static LiftLineSync syncAt(long gt) {
        return new LiftLineSync(ID, false, NODES, 24, 10 + 0.125 * (gt - 1000), 2.5, 2.5, gt, false, true, false);
    }

    private static double serverTravel(ClientLine line, long gt) {
        return (10 + 0.125 * (gt - 1000)) % line.path().loopLength();
    }

    /** What ClientLiftLines does for a sync: follow the clock first, then apply. */
    private static void accept(LineClock clock, List<ClientLine> lines, LiftLineSync sync, long now) {
        clock.follow(lines, now, 0);
        lines.get(0).apply(sync, now);
    }

    @Test
    void aSyncThatArrivesWithAClockJumpIsNotShiftedByTheJumpAgain() {
        LineClock clock = new LineClock();
        ClientLine line = new ClientLine(syncAt(1000), 1000);
        List<ClientLine> lines = List.of(line);
        accept(clock, lines, syncAt(1000), 1000);
        long now = 1000;
        for (int i = 0; i < 12; i++) clock.follow(lines, ++now, 1);
        assertEquals(serverTravel(line, now), line.travel, 1.0e-9);
        for (int jump : new int[]{3, -2, 5, -4}) {
            // The time packet moves the clock, then the resend stamped with the server's time arrives, then the client ticks.
            now += jump;
            accept(clock, lines, syncAt(now), now);
            clock.follow(lines, ++now, 1);
            assertEquals(serverTravel(line, now), line.travel, 1.0e-9, "after a jump of " + jump);
            for (int i = 0; i < 20; i++) clock.follow(lines, ++now, 1);
            assertEquals(serverTravel(line, now), line.travel, 1.0e-9, "20 ticks after a jump of " + jump);
        }
    }

    @Test
    void aClockJumpWithoutASyncMovesTheLineByTheJump() {
        LineClock clock = new LineClock();
        ClientLine line = new ClientLine(syncAt(1000), 1000);
        List<ClientLine> lines = List.of(line);
        accept(clock, lines, syncAt(1000), 1000);
        long now = 1000;
        for (int i = 0; i < 5; i++) clock.follow(lines, ++now, 1);
        now += 4;
        clock.follow(lines, ++now, 1);
        assertEquals(serverTravel(line, now), line.travel, 1.0e-9);
        now -= 3;
        clock.follow(lines, ++now, 1);
        assertEquals(serverTravel(line, now), line.travel, 1.0e-9);
    }
}
