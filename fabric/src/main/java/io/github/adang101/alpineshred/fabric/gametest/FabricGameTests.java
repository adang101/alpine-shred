package io.github.adang101.alpineshred.fabric.gametest;

import io.github.adang101.alpineshred.gametest.AlpineGameTests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Registers the shared game tests on Fabric (entry point {@code fabric-gametest}). Run with
 * {@code ./gradlew :fabric:runGametest}.
 */
public final class FabricGameTests implements FabricGameTest {
    private static final String TEMPLATE = "alpineshred:" + AlpineGameTests.TEMPLATE;

    @GameTest(template = TEMPLATE)
    public void dataFilesLoad(GameTestHelper helper) {
        AlpineGameTests.dataFilesLoad(helper);
    }

    @GameTest(template = TEMPLATE)
    public void tagsAreWired(GameTestHelper helper) {
        AlpineGameTests.tagsAreWired(helper);
    }

    @GameTest(template = TEMPLATE)
    public void accessorySlotsExist(GameTestHelper helper) {
        AlpineGameTests.accessorySlotsExist(helper);
    }

    @GameTest(template = TEMPLATE)
    public void strapNeedsGearAndMatchingBoots(GameTestHelper helper) {
        AlpineGameTests.strapNeedsGearAndMatchingBoots(helper);
    }

    @GameTest(template = TEMPLATE)
    public void skisNeedSkiBoots(GameTestHelper helper) {
        AlpineGameTests.skisNeedSkiBoots(helper);
    }

    @GameTest(template = TEMPLATE)
    public void strappedRidersWalkSlowerAndCannotSprint(GameTestHelper helper) {
        AlpineGameTests.strappedRidersWalkSlowerAndCannotSprint(helper);
    }

    @GameTest(template = TEMPLATE)
    public void removingGearUnstraps(GameTestHelper helper) {
        AlpineGameTests.removingGearUnstraps(helper);
    }

    @GameTest(template = TEMPLATE)
    public void swappingBootsUnstraps(GameTestHelper helper) {
        AlpineGameTests.swappingBootsUnstraps(helper);
    }

    @GameTest(template = TEMPLATE)
    public void rideStateSurvivesTheSyncedAccessor(GameTestHelper helper) {
        AlpineGameTests.rideStateSurvivesTheSyncedAccessor(helper);
    }

    @GameTest(template = TEMPLATE)
    public void waxRecipeSetsWaxAndKeepsOtherTags(GameTestHelper helper) {
        AlpineGameTests.waxRecipeSetsWaxAndKeepsOtherTags(helper);
    }

    @GameTest(template = TEMPLATE)
    public void gearProfileReadsEverything(GameTestHelper helper) {
        AlpineGameTests.gearProfileReadsEverything(helper);
    }
}
