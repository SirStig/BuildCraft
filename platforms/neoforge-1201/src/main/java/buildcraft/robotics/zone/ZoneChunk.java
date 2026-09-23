/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.util.BitSet;
import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * A single 16x16 chunk's worth of claimed/unclaimed columns within a {@link ZonePlan} layer.
 *
 * <p>Ported near-verbatim from 1.12.2's class of the same name -- see the 26.x copy of this class for the full
 * account of why {@code javax.vecmath.Point2i} and the {@code PacketBuffer}-based byte-buffer (de)serialisation
 * are both dropped.
 */
public class ZoneChunk {
    public BitSet property;
    private boolean fullSet = false;

    public ZoneChunk() {}

    public ZoneChunk(ZoneChunk old) {
        if (old.property != null) {
            property = BitSet.valueOf(old.property.toLongArray());
        }
    }

    public boolean get(int xChunk, int zChunk) {
        return fullSet || property != null && property.get(xChunk + zChunk * 16);
    }

    public void set(int xChunk, int zChunk, boolean value) {
        if (value) {
            if (fullSet) {
                return;
            }

            if (property == null) {
                property = new BitSet(16 * 16);
            }

            property.set(xChunk + zChunk * 16, true);

            if (property.cardinality() >= 16 * 16) {
                property = null;
                fullSet = true;
            }
        } else {
            if (fullSet) {
                property = new BitSet(16 * 16);
                property.flip(0, 16 * 16 - 1);
                fullSet = false;
            } else if (property == null) {
                // Note - ZonePlan should usually destroy such chunks
                property = new BitSet(16 * 16);
            }

            property.set(xChunk + zChunk * 16, false);
        }
    }

    /** @return Every set (xChunk, zChunk) pair, packed as {@code xChunk | (zChunk << 16)}. */
    public List<Integer> getAll() {
        ImmutableList.Builder<Integer> builder = ImmutableList.builder();
        for (int zChunk = 0; zChunk < 16; zChunk++) {
            for (int xChunk = 0; xChunk < 16; xChunk++) {
                if (get(xChunk, zChunk)) {
                    builder.add(xChunk | (zChunk << 16));
                }
            }
        }
        return builder.build();
    }

    public void writeToNBT(CompoundTag nbt) {
        nbt.putBoolean("fullSet", fullSet);

        if (property != null) {
            nbt.putByteArray("bits", property.toByteArray());
        }
    }

    public void readFromNBT(CompoundTag nbt) {
        fullSet = nbt.getBoolean("fullSet");

        if (nbt.contains("bits")) {
            property = BitSet.valueOf(nbt.getByteArray("bits"));
        }
    }

    public BlockPos getRandomBlockPos(RandomSource rand) {
        int x, z;

        if (fullSet) {
            x = rand.nextInt(16);
            z = rand.nextInt(16);
        } else {
            int bitId = rand.nextInt(property.cardinality());
            int bitPosition = property.nextSetBit(0);

            while (bitId > 0) {
                bitId--;

                bitPosition = property.nextSetBit(bitPosition + 1);
            }

            z = bitPosition / 16;
            x = bitPosition - 16 * z;
        }
        int y = rand.nextInt(255);

        return new BlockPos(x, y, z);
    }

    public boolean isEmpty() {
        return !fullSet && (property == null || property.isEmpty());
    }
}
