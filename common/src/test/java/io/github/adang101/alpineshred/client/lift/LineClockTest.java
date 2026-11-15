package io.github.adang101.alpineshred.client.lift;

import io.github.adang101.alpineshred.lift.LiftLineSync;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // ---- README clarification 100: the drawn distance never hops ----

    /** The time packet's corrections, one per 20-tick period. */
    private static final int[] JUMPS = {3, -2, 5, -4, 1, -1, 9, -7};

    /** The signed distance from {@code from} to {@code to} on a loop. */
    private static double step(double from, double to, double loop) {
        double d = to - from;
        if (d < -loop / 2) d += loop;
        if (d > loop / 2) d -= loop;
        return d;
    }

    /** What the chairs, the cable texture and the bullwheels draw at the start of a client tick. */
    private static double shown(ClientLine line) {
        return line.travel();
    }

    @Test
    void theDrawnDistanceNeverHopsWhenTheServersTimePacketMovesTheClock() {
        LineClock clock = new LineClock();
        ClientLine line = new ClientLine(syncAt(1000), 1000);
        List<ClientLine> lines = List.of(line);
        accept(clock, lines, syncAt(1000), 1000);
        double loop = line.path().loopLength();
        double perTick = 2.5 / 20;
        long now = 1000;
        double last = shown(line);
        for (int jump : JUMPS) {
            for (int i = 0; i < 19; i++) {
                clock.follow(lines, ++now, 1);
                double d = step(last, shown(line), loop);
                assertTrue(d > 0 && d <= perTick * 1.25, "per-tick step " + d + " before a jump of " + jump);
                last = shown(line);
            }
            now += jump; // the time packet
            clock.follow(lines, ++now, 1);
            double d = step(last, shown(line), loop);
            assertTrue(d > 0 && d <= perTick * 1.25, "a clock jump of " + jump + " moved the drawn distance by " + d + " (a tick is " + perTick + ")");
            last = shown(line);
        }
        // With no more jumps the drawn distance finds the server's again within 2 seconds.
        for (int i = 0; i < 40; i++) clock.follow(lines, ++now, 1);
        assertEquals(serverTravel(line, now), shown(line), 1.0e-9);
    }

    @Test
    void aSyncThatDisagreesALittleWithTheDrawnDistanceIsSlewedNotSnapped() {
        LineClock clock = new LineClock();
        ClientLine line = new ClientLine(syncAt(1000), 1000);
        List<ClientLine> lines = List.of(line);
        accept(clock, lines, syncAt(1000), 1000);
        double loop = line.path().loopLength();
        double perTick = 2.5 / 20;
        long now = 1000;
        for (int i = 0; i < 10; i++) clock.follow(lines, ++now, 1);
        double last = shown(line);
        // A packet whose state is 0.2 blocks off what the client has drawn.
        LiftLineSync off = new LiftLineSync(ID, false, NODES, 24, serverTravel(line, now) + 0.2, 2.5, 2.5, now, false, true, false);
        accept(clock, lines, off, now);
        for (int i = 0; i < 30; i++) {
            clock.follow(lines, ++now, 1);
            double d = step(last, shown(line), loop);
            assertTrue(d > 0 && d <= perTick * 1.25, "per-tick step " + d + " after a sync that was 0.2 blocks off");
            last = shown(line);
        }
    }

    @Test
    void aClockJumpTooLargeToSlewIsFollowedAtOnce() {
        LineClock clock = new LineClock();
        ClientLine line = new ClientLine(syncAt(1000), 1000);
        List<ClientLine> lines = List.of(line);
        accept(clock, lines, syncAt(1000), 1000);
        long now = 1000;
        for (int i = 0; i < 5; i++) clock.follow(lines, ++now, 1);
        now += 40; // a long stall: 5 blocks, far beyond any smoothing
        clock.follow(lines, ++now, 1);
        assertEquals(serverTravel(line, now), shown(line), 1.0e-9);
    }
}
