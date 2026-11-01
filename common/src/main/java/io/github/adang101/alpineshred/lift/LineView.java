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
}
