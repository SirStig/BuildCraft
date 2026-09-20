/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.test.lib.misc;

import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.misc.data.CompactingBitSet;
import buildcraft.lib.misc.data.DecompactingBitSet;

/**
 * Round-trip coverage for the compacting bit sets.
 *
 * <p>These classes stored their bytes in Trove's {@code TByteArrayList}, which the port swapped for
 * fastutil's {@code ByteArrayList}. The two libraries differ in how they expose the backing array
 * ({@code toArray()} vs {@code toByteArray()}), so these tests exist to prove the packed layout
 * still survives a write/read cycle unchanged.
 */
public class BitSetTester {

    @Test
    public void testSingleBitRoundTrip() {
        assertRoundTrip(1, new int[] { 1, 0, 1, 1, 0, 0, 0, 1, 1 });
    }

    @Test
    public void testAcrossByteBoundary() {
        // 3 bits per value does not divide into 8, so values straddle byte boundaries.
        assertRoundTrip(3, new int[] { 0, 7, 3, 4, 1, 6, 2, 5 });
    }

    @Test
    public void testFullByteValues() {
        assertRoundTrip(8, new int[] { 0, 255, 128, 1, 64 });
    }

    @Test
    public void testRandomValues() {
        Random random = new Random(0xBC8L);
        for (int bits = 1; bits <= 16; bits++) {
            int[] values = new int[64];
            int max = (1 << bits) - 1;
            for (int i = 0; i < values.length; i++) {
                values[i] = random.nextInt(max + 1);
            }
            assertRoundTrip(bits, values);
        }
    }

    private static void assertRoundTrip(int bits, int[] values) {
        CompactingBitSet compacting = new CompactingBitSet(bits);
        compacting.ensureCapacityValues(values.length);
        for (int value : values) {
            compacting.append(value);
        }

        byte[] packed = compacting.getBytes();
        // Every value contributes exactly `bits` bits, rounded up to whole bytes.
        int expectedBytes = (values.length * bits + 7) / 8;
        Assertions.assertEquals(expectedBytes, packed.length,
            "packed length for " + values.length + " values of " + bits + " bits");

        DecompactingBitSet decompacting = new DecompactingBitSet(bits, packed);
        for (int i = 0; i < values.length; i++) {
            Assertions.assertEquals(values[i], decompacting.next(),
                "value " + i + " at " + bits + " bits per value");
        }
    }
}
