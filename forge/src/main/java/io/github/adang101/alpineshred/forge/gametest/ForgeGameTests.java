package io.github.adang101.alpineshred.forge.gametest;

import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.gametest.AlpineGameTests;
import io.github.adang101.alpineshred.gametest.ParkGameTests;
import io.github.adang101.alpineshred.gametest.RideGameTests;
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

    // ---- phase 2: riding ----

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void surfaceProfilesMatchBlocksAndTags(GameTestHelper helper) {
        RideGameTests.surfaceProfilesMatchBlocksAndTags(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void bodyFindsTheSurfaceUnderfoot(GameTestHelper helper) {
        RideGameTests.bodyFindsTheSurfaceUnderfoot(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void stepHeightAndPowderHooksFollowTheController(GameTestHelper helper) {
        RideGameTests.stepHeightAndPowderHooksFollowTheController(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void staircaseDescentNeverFlickersToAir(GameTestHelper helper) {
        RideGameTests.staircaseDescentNeverFlickersToAir(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void kickerLaunchesAndLands(GameTestHelper helper) {
        RideGameTests.kickerLaunchesAndLands(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void wallHitWipesOutAndLocksInput(GameTestHelper helper) {
        RideGameTests.wallHitWipesOutAndLocksInput(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void ridersStepUpOneBlockAtSpeedOnly(GameTestHelper helper) {
        RideGameTests.ridersStepUpOneBlockAtSpeedOnly(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void powderCarriesFastRidersOnly(GameTestHelper helper) {
        RideGameTests.powderCarriesFastRidersOnly(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void rideStateClaimsFollowTheTransitionTable(GameTestHelper helper) {
        RideGameTests.rideStateClaimsFollowTheTransitionTable(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void serverTickLeavesRidersWhereTheClientPutThem(GameTestHelper helper) {
        RideGameTests.serverTickLeavesRidersWhereTheClientPutThem(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void wipeoutDamageUsesObservedSpeedAndHelmets(GameTestHelper helper) {
        RideGameTests.wipeoutDamageUsesObservedSpeedAndHelmets(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void serverWearsWaxEdgesAndBoards(GameTestHelper helper) {
        RideGameTests.serverWearsWaxEdgesAndBoards(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void validatorCorrectsAnImpossibleRide(GameTestHelper helper) {
        RideGameTests.validatorCorrectsAnImpossibleRide(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void vanillaAcceptsARideAtTheSpeedCap(GameTestHelper helper) {
        RideGameTests.vanillaAcceptsARideAtTheSpeedCap(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void vanillaAcceptsKickerAir(GameTestHelper helper) {
        RideGameTests.vanillaAcceptsKickerAir(helper);
    }

    // ---- phase 3: feel ----

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void soundsAndParticlesAreRegistered(GameTestHelper helper) {
        AlpineGameTests.soundsAndParticlesAreRegistered(helper);
    }

    // ---- phase 4: park ----

    @GameTest(template = RideGameTests.LARGE)
    public static void railsConnectAndFollowTheGround(GameTestHelper helper) {
        ParkGameTests.railsConnectAndFollowTheGround(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void tenPieceRailChainGrindsEndToEnd(GameTestHelper helper) {
        ParkGameTests.tenPieceRailChainGrindsEndToEnd(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void serverAcceptsARailGrind(GameTestHelper helper) {
        ParkGameTests.serverAcceptsARailGrind(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void waxAndOxidationChangeRailFriction(GameTestHelper helper) {
        ParkGameTests.waxAndOxidationChangeRailFriction(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void fencesAndWallsGrind(GameTestHelper helper) {
        ParkGameTests.fencesAndWallsGrind(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void boxSlideRunsTheBox(GameTestHelper helper) {
        ParkGameTests.boxSlideRunsTheBox(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void serverAcceptsABoxSlide(GameTestHelper helper) {
        ParkGameTests.serverAcceptsABoxSlide(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void rampsStackIntoRuns(GameTestHelper helper) {
        ParkGameTests.rampsStackIntoRuns(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void wrenchCyclesAndTurnsRails(GameTestHelper helper) {
        ParkGameTests.wrenchCyclesAndTurnsRails(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void quarterPipePlacesWhole(GameTestHelper helper) {
        ParkGameTests.quarterPipePlacesWhole(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void pipeRideGoesUpAndComesBack(GameTestHelper helper) {
        ParkGameTests.pipeRideGoesUpAndComesBack(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void pipeTopLaunchesAndReenters(GameTestHelper helper) {
        ParkGameTests.pipeTopLaunchesAndReenters(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void serverAcceptsAPipeRide(GameTestHelper helper) {
        ParkGameTests.serverAcceptsAPipeRide(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void parkClaimsNeedTheFeature(GameTestHelper helper) {
        ParkGameTests.parkClaimsNeedTheFeature(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void lipRampLaunchesWithAssist(GameTestHelper helper) {
        ParkGameTests.lipRampLaunchesWithAssist(helper);
    }
}
