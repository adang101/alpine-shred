package io.github.adang101.alpineshred.forge.gametest;

import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.gametest.AlpineGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Registers the shared game tests on Forge. With {@code @PrefixGameTestTemplate(false)} the template
 * "empty" resolves to {@code alpineshred:empty}. Run with {@code ./gradlew :forge:runGameTestServer}.
 */
@GameTestHolder(AlpineShred.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgeGameTests {
    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void dataFilesLoad(GameTestHelper helper) {
        AlpineGameTests.dataFilesLoad(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void tagsAreWired(GameTestHelper helper) {
        AlpineGameTests.tagsAreWired(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void accessorySlotsExist(GameTestHelper helper) {
        AlpineGameTests.accessorySlotsExist(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void strapNeedsGearAndMatchingBoots(GameTestHelper helper) {
        AlpineGameTests.strapNeedsGearAndMatchingBoots(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void skisNeedSkiBoots(GameTestHelper helper) {
        AlpineGameTests.skisNeedSkiBoots(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void strappedRidersWalkSlowerAndCannotSprint(GameTestHelper helper) {
        AlpineGameTests.strappedRidersWalkSlowerAndCannotSprint(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void removingGearUnstraps(GameTestHelper helper) {
        AlpineGameTests.removingGearUnstraps(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void swappingBootsUnstraps(GameTestHelper helper) {
        AlpineGameTests.swappingBootsUnstraps(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void rideStateSurvivesTheSyncedAccessor(GameTestHelper helper) {
        AlpineGameTests.rideStateSurvivesTheSyncedAccessor(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void waxRecipeSetsWaxAndKeepsOtherTags(GameTestHelper helper) {
        AlpineGameTests.waxRecipeSetsWaxAndKeepsOtherTags(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void gearProfileReadsEverything(GameTestHelper helper) {
        AlpineGameTests.gearProfileReadsEverything(helper);
    }
}
