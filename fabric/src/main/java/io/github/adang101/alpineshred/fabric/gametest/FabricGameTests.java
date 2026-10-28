package io.github.adang101.alpineshred.fabric.gametest;

import io.github.adang101.alpineshred.gametest.AlpineGameTests;
import io.github.adang101.alpineshred.gametest.RideGameTests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Registers the shared game tests on Fabric (entry point {@code fabric-gametest}). Run with
 * {@code ./gradlew :fabric:runGametest}.
 */
public final class FabricGameTests implements FabricGameTest {
    private static final String TEMPLATE = "alpineshred:" + AlpineGameTests.TEMPLATE;
    private static final String LARGE = "alpineshred:" + RideGameTests.LARGE;

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

    // ---- phase 2: riding ----

    @GameTest(template = TEMPLATE)
    public void surfaceProfilesMatchBlocksAndTags(GameTestHelper helper) {
        RideGameTests.surfaceProfilesMatchBlocksAndTags(helper);
    }

    @GameTest(template = LARGE)
    public void bodyFindsTheSurfaceUnderfoot(GameTestHelper helper) {
        RideGameTests.bodyFindsTheSurfaceUnderfoot(helper);
    }

    @GameTest(template = TEMPLATE)
    public void stepHeightAndPowderHooksFollowTheController(GameTestHelper helper) {
        RideGameTests.stepHeightAndPowderHooksFollowTheController(helper);
    }

    @GameTest(template = LARGE)
    public void staircaseDescentNeverFlickersToAir(GameTestHelper helper) {
        RideGameTests.staircaseDescentNeverFlickersToAir(helper);
    }

    @GameTest(template = LARGE)
    public void kickerLaunchesAndLands(GameTestHelper helper) {
        RideGameTests.kickerLaunchesAndLands(helper);
    }

    @GameTest(template = LARGE)
    public void wallHitWipesOutAndLocksInput(GameTestHelper helper) {
        RideGameTests.wallHitWipesOutAndLocksInput(helper);
    }

    @GameTest(template = LARGE)
    public void ridersStepUpOneBlockAtSpeedOnly(GameTestHelper helper) {
        RideGameTests.ridersStepUpOneBlockAtSpeedOnly(helper);
    }

    @GameTest(template = LARGE)
    public void powderCarriesFastRidersOnly(GameTestHelper helper) {
        RideGameTests.powderCarriesFastRidersOnly(helper);
    }

    @GameTest(template = TEMPLATE)
    public void rideStateClaimsFollowTheTransitionTable(GameTestHelper helper) {
        RideGameTests.rideStateClaimsFollowTheTransitionTable(helper);
    }

    @GameTest(template = LARGE)
    public void wipeoutDamageUsesObservedSpeedAndHelmets(GameTestHelper helper) {
        RideGameTests.wipeoutDamageUsesObservedSpeedAndHelmets(helper);
    }

    @GameTest(template = LARGE)
    public void serverWearsWaxEdgesAndBoards(GameTestHelper helper) {
        RideGameTests.serverWearsWaxEdgesAndBoards(helper);
    }

    @GameTest(template = LARGE)
    public void validatorCorrectsAnImpossibleRide(GameTestHelper helper) {
        RideGameTests.validatorCorrectsAnImpossibleRide(helper);
    }

    @GameTest(template = LARGE)
    public void vanillaAcceptsARideAtTheSpeedCap(GameTestHelper helper) {
        RideGameTests.vanillaAcceptsARideAtTheSpeedCap(helper);
    }

    @GameTest(template = LARGE)
    public void vanillaAcceptsKickerAir(GameTestHelper helper) {
        RideGameTests.vanillaAcceptsKickerAir(helper);
    }
}
