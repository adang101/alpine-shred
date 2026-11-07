package io.github.adang101.alpineshred.compat.create;

/**
 * Holder for the loader's {@link CreateBridge}. Empty ({@link #ABSENT}) until a loader installs one behind
 * {@code PlatformHooks.isCreatePresent()}, so common code can always ask without loading a Create class.
 */
public final class CreateCompat {
    /** The bridge when Create is not installed: nothing is live. */
    public static final CreateBridge ABSENT = new CreateBridge() {
        @Override
        public boolean live() {
            return false;
        }

        @Override
        public String version() {
            return "";
        }
    };

    private static volatile CreateBridge bridge = ABSENT;

    private CreateCompat() {
    }

    public static CreateBridge bridge() {
        return bridge;
    }

    /** Called once by the loader's {@code compat.create} initialiser. */
    public static void install(CreateBridge installed) {
        bridge = installed;
    }
}
