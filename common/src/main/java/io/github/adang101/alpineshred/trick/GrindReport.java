package io.github.adang101.alpineshred.trick;

/**
 * What the client says happened on one rail or box ({@code c2s/grind_report}, sections 8 and 15),
 * sent once when it leaves.
 *
 * @param x, y, z       a block of the rail or box ridden (the first)
 * @param box           a box slide, not a rail grind
 * @param variant       the entry variant (section 8's angle bands), 0 to 3
 * @param entryAxis     the board's angle off the rail axis at entry, 0 to 90 degrees
 * @param switchEntry   entered riding backward
 * @param lengthBlocks  distance ridden along it
 * @param durationTicks ticks on it
 * @param switchUps     variant changes on it
 */
public record GrindReport(int x, int y, int z, boolean box, int variant, double entryAxis, boolean switchEntry,
                          double lengthBlocks, int durationTicks, int switchUps) {
}
