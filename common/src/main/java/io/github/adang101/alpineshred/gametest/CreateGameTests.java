package io.github.adang101.alpineshred.gametest;

import io.github.adang101.alpineshred.compat.create.CreateBridge;
import io.github.adang101.alpineshred.compat.create.CreateCompat;
import io.github.adang101.alpineshred.platform.PlatformHooks;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Create compatibility tests (phase 9 plan, decision 4). The CI "with Create" column runs the game tests with
 * Create on the runtime classpath and {@value #COLUMN_PROPERTY} set; the ordinary column leaves both out. Each
 * test asserts what its column declares, so a column cannot silently run without (or with) Create: kinetic
 * tests of later work packages start with {@link #inCreateColumn()} and return early outside it.
 * Nothing here names a Create type.
 */
public final class CreateGameTests {
    /** Set by the loader modules' {@code -PwithCreate} run configurations. */
    public static final String COLUMN_PROPERTY = "alpineshred.create";

    private CreateGameTests() {
    }

    public static boolean inCreateColumn() {
        return Boolean.getBoolean(COLUMN_PROPERTY);
    }

    /** With Create: the loader found it and the bridge is installed and live. Without: no bridge, so no Create class was loaded. */
    public static void createBridgeMatchesColumn(GameTestHelper helper) {
        boolean expected = inCreateColumn();
        check(PlatformHooks.isCreatePresent() == expected, "Create present = " + PlatformHooks.isCreatePresent() + " but the column expects " + expected);
        CreateBridge bridge = CreateCompat.bridge();
        if (expected) {
            check(bridge != CreateCompat.ABSENT, "Create is present but no bridge was installed");
            check(bridge.live(), "the Create bridge is not live");
            check(!bridge.version().isEmpty(), "the Create bridge reports no version");
        } else {
            check(bridge == CreateCompat.ABSENT, "a bridge is installed without Create");
            check(!bridge.live(), "the absent bridge claims to be live");
        }
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
