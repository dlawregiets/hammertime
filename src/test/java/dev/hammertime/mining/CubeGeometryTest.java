package dev.hammertime.mining;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CubeGeometryTest {
    @Test
    void size3NorthProduces27UniqueOffsetsExtendingIntoWall() {
        List<CubeGeometry.Offset> offsets = CubeGeometry.offsets(3, 0, 0, -1);
        assertEquals(27, offsets.size());
        assertEquals(27, new HashSet<>(offsets).size());
        assertTrue(offsets.contains(new CubeGeometry.Offset(0, 0, 0)));
        for (CubeGeometry.Offset o : offsets) {
            assertTrue(o.z() <= 0 && o.z() >= -2, "z in [-2,0]: " + o);
            assertTrue(o.x() >= -1 && o.x() <= 1, "x in [-1,1]: " + o);
            assertTrue(o.y() >= -1 && o.y() <= 1, "y in [-1,1]: " + o);
        }
    }

    @Test
    void size5NorthSpansFiveInEachAxis() {
        List<CubeGeometry.Offset> offsets = CubeGeometry.offsets(5, 0, 0, -1);
        assertEquals(125, offsets.size());
        for (CubeGeometry.Offset o : offsets) {
            assertTrue(o.z() <= 0 && o.z() >= -4, "z in [-4,0]: " + o);
            assertTrue(o.x() >= -2 && o.x() <= 2, "x in [-2,2]: " + o);
            assertTrue(o.y() >= -2 && o.y() <= 2, "y in [-2,2]: " + o);
        }
    }

    @Test
    void verticalForwardDownStillProducesFullCube() {
        List<CubeGeometry.Offset> offsets = CubeGeometry.offsets(3, 0, -1, 0);
        assertEquals(27, offsets.size());
        assertEquals(27, new HashSet<>(offsets).size());
        for (CubeGeometry.Offset o : offsets) {
            assertTrue(o.y() <= 0 && o.y() >= -2, "y in [-2,0]: " + o);
        }
    }

    @Test
    void everyDirectionProducesSizeCubedUniqueOffsets() {
        int[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : directions) {
            for (int size : new int[]{3, 5}) {
                List<CubeGeometry.Offset> offsets = CubeGeometry.offsets(size, d[0], d[1], d[2]);
                assertEquals(size * size * size, offsets.size(),
                        "count for size " + size + " dir " + Arrays.toString(d));
                assertEquals(size * size * size, new HashSet<>(offsets).size(),
                        "uniqueness for size " + size + " dir " + Arrays.toString(d));
            }
        }
    }

    @Test
    void rejectsEvenSize() {
        assertThrows(IllegalArgumentException.class, () -> CubeGeometry.offsets(4, 0, 0, -1));
    }

    @Test
    void dominantForwardPicksTheLookAxis() {
        assertArrayEquals(new int[]{0, 0, -1}, CubeGeometry.dominantForward(0.0, 0.0, -1.0));
        assertArrayEquals(new int[]{0, -1, 0}, CubeGeometry.dominantForward(0.0, -0.8, 0.2));
        assertArrayEquals(new int[]{1, 0, 0}, CubeGeometry.dominantForward(0.9, 0.0, 0.1));
        assertArrayEquals(new int[]{0, 1, 0}, CubeGeometry.dominantForward(0.0, 1.0, 0.0));
    }
}
