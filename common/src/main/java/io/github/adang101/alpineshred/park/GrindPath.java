package io.github.adang101.alpineshred.park;

/**
 * A path a rider can grind, in world coordinates, with what it is made of.
 *
 * @param id       identity of the piece (its packed block position), so a grind can tell pieces apart
 * @param friction deceleration along the path in blocks per tick squared, wax and oxidation included
 */
public record GrindPath(long id, RailPath path, GrindMaterial material, double friction) {
}
