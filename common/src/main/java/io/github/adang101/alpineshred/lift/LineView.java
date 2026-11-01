package io.github.adang101.alpineshred.lift;

/**
 * What a chair needs to know about its line: the path, how far the loop has run, and how many chairs
 * hang on it. {@link LiftLine} is the server's view; the client's line cache (WP5) implements it too.
 */
public interface LineView {
    LiftPath path();

    /** Loop distance run so far, in blocks. */
    double travel();

    int chairCount();

    /** The boarding and unload intervals of the loaded cable. */
    LiftStations.Result stations();

    /** Current speed in blocks per second (never negative). */
    double speed();

    /** Whether the line is faulted: its riders may then get off anywhere. */
    boolean faulted();
}
