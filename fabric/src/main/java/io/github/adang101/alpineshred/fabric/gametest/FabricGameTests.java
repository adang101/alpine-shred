package io.github.adang101.alpineshred.fabric.gametest;

import io.github.adang101.alpineshred.gametest.AlpineGameTests;
import io.github.adang101.alpineshred.gametest.ParkGameTests;
import io.github.adang101.alpineshred.gametest.TrickGameTests;
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
    public void serverTickLeavesRidersWhereTheClientPutThem(GameTestHelper helper) {
        RideGameTests.serverTickLeavesRidersWhereTheClientPutThem(helper);
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

    // ---- phase 3: feel ----

    @GameTest(template = TEMPLATE)
    public void soundsAndParticlesAreRegistered(GameTestHelper helper) {
        AlpineGameTests.soundsAndParticlesAreRegistered(helper);
    }

    // ---- phase 4: park ----

    @GameTest(template = LARGE)
    public static void railsConnectAndFollowTheGround(GameTestHelper helper) {
        ParkGameTests.railsConnectAndFollowTheGround(helper);
    }

    @GameTest(template = LARGE)
    public static void tenPieceRailChainGrindsEndToEnd(GameTestHelper helper) {
        ParkGameTests.tenPieceRailChainGrindsEndToEnd(helper);
    }

    @GameTest(template = LARGE)
    public static void serverAcceptsARailGrind(GameTestHelper helper) {
        ParkGameTests.serverAcceptsARailGrind(helper);
    }

    @GameTest(template = LARGE)
    public static void waxAndOxidationChangeRailFriction(GameTestHelper helper) {
        ParkGameTests.waxAndOxidationChangeRailFriction(helper);
    }

    @GameTest(template = LARGE)
    public static void fencesAndWallsGrind(GameTestHelper helper) {
        ParkGameTests.fencesAndWallsGrind(helper);
    }

    @GameTest(template = LARGE)
    public static void boxSlideRunsTheBox(GameTestHelper helper) {
        ParkGameTests.boxSlideRunsTheBox(helper);
    }

    @GameTest(template = LARGE)
    public static void serverAcceptsABoxSlide(GameTestHelper helper) {
        ParkGameTests.serverAcceptsABoxSlide(helper);
    }

    @GameTest(template = LARGE)
    public static void rampsStackIntoRuns(GameTestHelper helper) {
        ParkGameTests.rampsStackIntoRuns(helper);
    }

    @GameTest(template = LARGE)
    public static void wrenchCyclesAndTurnsRails(GameTestHelper helper) {
        ParkGameTests.wrenchCyclesAndTurnsRails(helper);
    }

    @GameTest(template = LARGE)
    public static void quarterPipePlacesWhole(GameTestHelper helper) {
        ParkGameTests.quarterPipePlacesWhole(helper);
    }

    @GameTest(template = LARGE)
    public static void pipeRideGoesUpAndComesBack(GameTestHelper helper) {
        ParkGameTests.pipeRideGoesUpAndComesBack(helper);
    }

    @GameTest(template = LARGE)
    public static void pipeTopLaunchesAndReenters(GameTestHelper helper) {
        ParkGameTests.pipeTopLaunchesAndReenters(helper);
    }

    @GameTest(template = LARGE)
    public static void serverAcceptsAPipeRide(GameTestHelper helper) {
        ParkGameTests.serverAcceptsAPipeRide(helper);
    }

    @GameTest(template = LARGE)
    public static void parkClaimsNeedTheFeature(GameTestHelper helper) {
        ParkGameTests.parkClaimsNeedTheFeature(helper);
    }

    @GameTest(template = LARGE)
    public static void lipRampLaunchesWithAssist(GameTestHelper helper) {
        ParkGameTests.lipRampLaunchesWithAssist(helper);
    }

    // ---- phase 5: tricks ----

    @GameTest(template = LARGE)
    public static void forgedAirReportsAreRejected(GameTestHelper helper) {
        TrickGameTests.forgedAirReportsAreRejected(helper);
    }

    @GameTest(template = LARGE)
    public static void combosChainBankAndCount(GameTestHelper helper) {
        TrickGameTests.combosChainBankAndCount(helper);
    }

    @GameTest(template = LARGE)
    public static void aBailLosesTheCombo(GameTestHelper helper) {
        TrickGameTests.aBailLosesTheCombo(helper);
    }

    @GameTest(template = LARGE)
    public static void grindReportsNeedARail(GameTestHelper helper) {
        TrickGameTests.grindReportsNeedARail(helper);
    }

    @GameTest(template = LARGE)
    public static void aReportBeforeItsLandingIsJudgedOnThatAir(GameTestHelper helper) {
        TrickGameTests.aReportBeforeItsLandingIsJudgedOnThatAir(helper);
    }

    @GameTest(template = LARGE)
    public static void trickAdvancementsFire(GameTestHelper helper) {
        TrickGameTests.trickAdvancementsFire(helper);
    }

    @GameTest(template = LARGE)
    public static void aSaveWritesTheDesignAndTakesTheDye(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.DesignGameTests.aSaveWritesTheDesignAndTakesTheDye(helper);
    }

    @GameTest(template = LARGE)
    public static void rejectedSavesChargeNothing(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.DesignGameTests.rejectedSavesChargeNothing(helper);
    }

    @GameTest(template = LARGE)
    public static void smithingCarriesTheDesign(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.DesignGameTests.smithingCarriesTheDesign(helper);
    }

    @GameTest(template = LARGE)
    public static void aDesignSurvivesSaveAndLoad(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.DesignGameTests.aDesignSurvivesSaveAndLoad(helper);
    }
}
