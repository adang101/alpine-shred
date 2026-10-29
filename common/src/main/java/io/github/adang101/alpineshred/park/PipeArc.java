package io.github.adang101.alpineshred.park;

import java.util.ArrayList;
import java.util.List;

/**
 * Quarter pipe geometry (design doc section 11, "Pipe pieces"). A quarter pipe of radius R fills an
 * R by R square in the vertical plane along its facing: the riding surface is a quarter circle from
 * flat at {@code u = 0} (the transition) to vertical at {@code u = R} (under the coping), with height
 * {@code h(u) = R - sqrt(R^2 - u^2)}. Each block of the square that holds part of the solid under the
 * arc is one {@code quarter_pipe} block; its {@code index} counts them along the arc.
 */
public final class PipeArc {
    public static final int[] RADII = {3, 5};
    /** Collision columns per block, each one pixel wide. */
    public static final int COLUMNS = 16;

    private PipeArc() {
    }

    /** Surface height at distance {@code u} from the transition, clamped to the square. */
    public static double height(double radius, double u) {
        double c = Math.max(0, Math.min(u, radius));
        return radius - Math.sqrt(radius * radius - c * c);
    }

    /** Arc angle at {@code u}: 0 flat, pi/2 vertical. */
    public static double theta(double radius, double u) {
        return Math.asin(Math.max(0, Math.min(1, u / radius)));
    }

    /** Distance from the transition of the surface point at angle {@code theta}. */
    public static double u(double radius, double theta) {
        return radius * Math.sin(theta);
    }

    /** Surface height at angle {@code theta}. */
    public static double heightAt(double radius, double theta) {
        return radius * (1 - Math.cos(theta));
    }

    /** One block of a pipe: column {@code i} along the facing, row {@code j} up. */
    public record Cell(int i, int j) {
    }

    /** The blocks of a pipe of this radius in arc order: index 0 is the transition block. */
    public static List<Cell> cells(int radius) {
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < radius; i++) {
            double top = height(radius, i + 1);
            for (int j = 0; j < radius; j++) {
                if (top > j + 1.0e-9) out.add(new Cell(i, j));
            }
        }
        return out;
    }

    public static Cell cell(int radius, int index) {
        List<Cell> cells = cells(radius);
        return cells.get(Math.max(0, Math.min(index, cells.size() - 1)));
    }

    public static int cellCount(int radius) {
        return cells(radius).size();
    }

    /**
     * Top of collision column {@code column} (0 to 15, from the transition side) of block
     * {@code index}, in blocks above the block's base, 0 to 1. Each column takes the arc's height at
     * its low edge, so a rider resting on the arc never overlaps a column.
     */
    public static double columnTop(int radius, int index, int column) {
        Cell c = cell(radius, index);
        double u = c.i() + column / (double) COLUMNS;
        return Math.max(0, Math.min(1, height(radius, u) - c.j()));
    }
}
