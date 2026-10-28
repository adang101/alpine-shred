package io.github.adang101.alpineshred.gear;

import io.github.adang101.alpineshred.gear.GearWear.Accumulator;
import io.github.adang101.alpineshred.gear.GearWear.Contact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GearWearTest {

    @Test
    void edgeGripMultiplierFollowsTheDocFormula() {
        assertEquals(1.0, GearWear.edgeGripMultiplier(100), 1e-12);
        assertEquals(0.85, GearWear.edgeGripMultiplier(0), 1e-12);
        assertEquals(0.925, GearWear.edgeGripMultiplier(50), 1e-12);
        assertEquals(1.0, GearWear.edgeGripMultiplier(250), 1e-12);
        assertEquals(0.85, GearWear.edgeGripMultiplier(-4), 1e-12);
    }

    @Test
    void snowWearIsOneDamagePer128Blocks() {
        Accumulator acc = new Accumulator();
        int damage = 0;
        for (int i = 0; i < 1280; i++) damage += acc.travel(0.5, Contact.SNOW, false, false, 1.0).durabilityDamage();
        assertEquals(5, damage);
    }

    @Test
    void grindAndScrapeWearFaster() {
        Accumulator grind = new Accumulator();
        Accumulator scrape = new Accumulator();
        int g = 0, s = 0;
        for (int i = 0; i < 64; i++) {
            g += grind.travel(1.0, Contact.GRIND, false, false, 1.0).durabilityDamage();
            s += scrape.travel(1.0, Contact.SCRAPE, false, false, 1.0).durabilityDamage();
        }
        assertEquals(4, g);
        assertEquals(16, s);
    }

    @Test
    void partialProgressCarriesAcrossSurfaceChanges() {
        Accumulator acc = new Accumulator();
        // Half a snow unit (64 blocks), then half a scrape unit (2 blocks) = one damage total.
        int damage = acc.travel(64, Contact.SNOW, false, false, 1.0).durabilityDamage();
        damage += acc.travel(2, Contact.SCRAPE, false, false, 1.0).durabilityDamage();
        assertEquals(1, damage);
    }

    @Test
    void edgeDropsOnePer40CarvingBlocksOnSnowOnly() {
        Accumulator acc = new Accumulator();
        int edge = 0;
        for (int i = 0; i < 400; i++) edge += acc.travel(1.0, Contact.SNOW, true, false, 1.0).edgeLoss();
        assertEquals(10, edge);
        assertEquals(0, acc.travel(100, Contact.GRIND, true, false, 1.0).edgeLoss());
    }

    @Test
    void waxCountsDownPerBlockOnSnow() {
        Accumulator acc = new Accumulator();
        int wax = 0;
        for (int i = 0; i < 100; i++) wax += acc.travel(0.3, Contact.SNOW, false, true, 1.0).waxUsed();
        assertEquals(30, wax);
        assertEquals(0, acc.travel(10, Contact.SCRAPE, false, true, 1.0).waxUsed());
    }

    @Test
    void durabilityMultiplierScalesAndCanDisableWear() {
        Accumulator doubled = new Accumulator();
        assertEquals(2, doubled.travel(128, Contact.SNOW, false, false, 2.0).durabilityDamage());
        Accumulator off = new Accumulator();
        assertEquals(0, off.travel(10_000, Contact.SNOW, false, false, 0.0).durabilityDamage());
        assertEquals(3, Accumulator.bailDamage(1.0));
        assertEquals(0, Accumulator.bailDamage(0.0));
        assertEquals(1, Accumulator.bailDamage(0.1));
    }

    @Test
    void airborneTravelCausesNoWear() {
        assertEquals(GearWear.Accumulator.Wear.NONE, new Accumulator().travel(50, Contact.AIR, true, true, 1.0));
    }
}
