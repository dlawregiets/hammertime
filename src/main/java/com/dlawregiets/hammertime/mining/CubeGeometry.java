package com.dlawregiets.hammertime.mining;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure integer geometry for the cube-mining region. Uses no Minecraft types so it is
 * trivially unit-testable.
 */
public final class CubeGeometry {
    private CubeGeometry() {
    }

    /** An integer offset relative to the target block. */
    public record Offset(int x, int y, int z) {
    }

    /**
     * Computes the offsets of a {@code size x size x size} cube.
     *
     * <p>The target block is the centre of the front face at {@code (0,0,0)}; the cube
     * extends into the wall along the forward vector for depth {@code 0..size-1}.
     *
     * @param size odd cube size (3 or 5)
     * @param fx   forward unit vector x (-1, 0, or 1)
     * @param fy   forward unit vector y
     * @param fz   forward unit vector z
     */
    public static List<Offset> offsets(int size, int fx, int fy, int fz) {
        if (size < 1 || size % 2 == 0) {
            throw new IllegalArgumentException("size must be a positive odd number: " + size);
        }
        int half = size / 2;

        // Perpendicular basis (right, up). The exact orientation is irrelevant because
        // the cube is symmetric on both perpendicular axes.
        int rx;
        int ry;
        int rz;
        int ux;
        int uy;
        int uz;
        if (fy == 0) {
            // Horizontal forward: up is world up, right is forward rotated 90 degrees.
            rx = -fz;
            ry = 0;
            rz = fx;
            ux = 0;
            uy = 1;
            uz = 0;
        } else {
            // Vertical forward: use world axes for the two perpendicular directions.
            rx = 1;
            ry = 0;
            rz = 0;
            ux = 0;
            uy = 0;
            uz = 1;
        }

        List<Offset> result = new ArrayList<>(size * size * size);
        for (int depth = 0; depth < size; depth++) {
            for (int lateral = -half; lateral <= half; lateral++) {
                for (int vertical = -half; vertical <= half; vertical++) {
                    result.add(new Offset(
                            fx * depth + rx * lateral + ux * vertical,
                            fy * depth + ry * lateral + uy * vertical,
                            fz * depth + rz * lateral + uz * vertical));
                }
            }
        }
        return result;
    }

    /**
     * Returns the dominant unit axis of a direction vector as {@code {fx, fy, fz}}.
     * Ties are resolved in order x, then y, then z.
     */
    public static int[] dominantForward(double x, double y, double z) {
        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double az = Math.abs(z);
        if (ax >= ay && ax >= az) {
            return new int[]{x >= 0 ? 1 : -1, 0, 0};
        }
        if (ay >= az) {
            return new int[]{0, y >= 0 ? 1 : -1, 0};
        }
        return new int[]{0, 0, z >= 0 ? 1 : -1};
    }
}
