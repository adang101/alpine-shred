package io.github.adang101.alpineshred.forge.gametest;

import io.github.adang101.alpineshred.AlpineShred;
import io.github.adang101.alpineshred.gametest.AlpineGameTests;
import io.github.adang101.alpineshred.gametest.ParkGameTests;
import io.github.adang101.alpineshred.gametest.TrickGameTests;
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

    // ---- phase 5: tricks ----

    @GameTest(template = RideGameTests.LARGE)
    public static void forgedAirReportsAreRejected(GameTestHelper helper) {
        TrickGameTests.forgedAirReportsAreRejected(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void combosChainBankAndCount(GameTestHelper helper) {
        TrickGameTests.combosChainBankAndCount(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void aBailLosesTheCombo(GameTestHelper helper) {
        TrickGameTests.aBailLosesTheCombo(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void grindReportsNeedARail(GameTestHelper helper) {
        TrickGameTests.grindReportsNeedARail(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void aReportBeforeItsLandingIsJudgedOnThatAir(GameTestHelper helper) {
        TrickGameTests.aReportBeforeItsLandingIsJudgedOnThatAir(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void trickAdvancementsFire(GameTestHelper helper) {
        TrickGameTests.trickAdvancementsFire(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void aSaveWritesTheDesignAndTakesTheDye(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.DesignGameTests.aSaveWritesTheDesignAndTakesTheDye(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void rejectedSavesChargeNothing(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.DesignGameTests.rejectedSavesChargeNothing(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void smithingCarriesTheDesign(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.DesignGameTests.smithingCarriesTheDesign(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void aDesignSurvivesSaveAndLoad(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.DesignGameTests.aDesignSurvivesSaveAndLoad(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void templatesAreWhole(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.WorldgenGameTests.templatesAreWhole(helper);
    }

    @GameTest(template = RideGameTests.LARGE)
    public static void structuresGenerateWithinBounds(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.WorldgenGameTests.structuresGenerateWithinBounds(helper);
    }

    @GameTest(template = RideGameTests.LARGE, timeoutTicks = 600)
    public static void resortHasALinkedLift(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.WorldgenGameTests.resortHasALinkedLift(helper);
    }

    // ---- phase 8: lifts ----

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void savedDataRoundTrip(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.savedDataRoundTrip(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void travelAtFixedTicks(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.travelAtFixedTicks(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void breakingAHeadFaultsTheLine(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.breakingAHeadFaultsTheLine(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void mismatchedBlockEntityFaultsTheLine(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.mismatchedBlockEntityFaultsTheLine(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void pendingLineAdoptedOnce(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.pendingLineAdoptedOnce(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void linkerBuildsALine(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.linkerBuildsALine(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void linkerRejectsABlockedSpan(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.linkerRejectsABlockedSpan(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void everyLiftBlockDropsItself(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.everyLiftBlockDropsItself(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void chairPositionsAtFixedTicks(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.chairPositionsAtFixedTicks(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void chairsSpawnNearPlayersAndDespawnFar(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.chairsSpawnNearPlayersAndDespawnFar(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void blockedMobCannotBoard(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.blockedMobCannotBoard(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void mobBoardsAndIsPersistent(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.mobBoardsAndIsPersistent(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void seatsAreLimited(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.seatsAreLimited(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void duplicateChairKeepsTheOneWithRiders(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.duplicateChairKeepsTheOneWithRiders(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void staleChairEjectsItsRiders(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.staleChairEjectsItsRiders(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void groundSearchIsCapped(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.groundSearchIsCapped(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void clientCatchUpMatchesServer(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.clientCatchUpMatchesServer(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void syncSetTracksPlayers(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.syncSetTracksPlayers(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void redstoneDrivesTheLine(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.redstoneDrivesTheLine(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void driveKeepsItsCommandOnLoad(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.driveKeepsItsCommandOnLoad(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void stopButtonStopsTheNearestLine(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.stopButtonStopsTheNearestLine(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void speedLevelsCycle(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.speedLevelsCycle(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void linkerPassesThroughTheDrive(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.linkerPassesThroughTheDrive(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void liftCommands(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.liftCommands(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void boardAndRideToTheTop(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.boardAndRideToTheTop(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void boardingRefusedTooFastFullOrNoPass(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.boardingRefusedTooFastFullOrNoPass(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void createBridgeMatchesColumn(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.CreateGameTests.createBridgeMatchesColumn(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void configSyncCarriesLiftKeys(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.configSyncCarriesLiftKeys(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void sneakDismountRules(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.sneakDismountRules(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void leashedMobsBoard(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.leashedMobsBoard(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void petFollowsOwner(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.petFollowsOwner(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void riderSurvivesRelog(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.riderSurvivesRelog(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void riderEjectedWhenLineGone(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.riderEjectedWhenLineGone(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void staysStrappedOnChair(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.staysStrappedOnChair(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void liftSurvivesSaveReload(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.liftSurvivesSaveReload(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE)
    public static void liftManagerBudget(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.LiftGameTests.liftManagerBudget(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE, timeoutTicks = 600, batch = "kineticDrive")
    public static void kineticRotationRunsTheLine(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.KineticDriveGameTests.kineticRotationRunsTheLine(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE, timeoutTicks = 600, batch = "kineticDrive")
    public static void kineticLostRotationIsAnEmergencyStop(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.KineticDriveGameTests.kineticLostRotationIsAnEmergencyStop(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE, timeoutTicks = 600, batch = "kineticDrive")
    public static void kineticOverstressStopsTheLine(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.KineticDriveGameTests.kineticOverstressStopsTheLine(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE, timeoutTicks = 600, batch = "kineticDrive")
    public static void kineticDriveOverridesRedstone(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.KineticDriveGameTests.kineticDriveOverridesRedstone(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE, timeoutTicks = 600, batch = "kineticDrive")
    public static void kineticDriveSurvivesSaveAndReload(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.KineticDriveGameTests.kineticDriveSurvivesSaveAndReload(helper);
    }

    @GameTest(template = AlpineGameTests.TEMPLATE, timeoutTicks = 600, batch = "kineticDrive")
    public static void kineticDriveBudget(GameTestHelper helper) {
        io.github.adang101.alpineshred.gametest.KineticDriveGameTests.kineticDriveBudget(helper);
    }
}
