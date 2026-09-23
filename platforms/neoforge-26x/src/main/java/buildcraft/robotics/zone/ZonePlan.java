/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * A per-colour chunk-grid claim map: one {@link ZoneChunk} per touched {@link ChunkPos}, addressed by absolute
 * block coordinates. Ported near-verbatim from 1.12.2's class of the same name -- see {@link ZoneChunk}'s own
 * javadoc for why the {@code PacketBuffer} byte-buffer (de)serialisation pair is dropped; everything else
 * (the claim bookkeeping itself, and the {@link IZone} implementation used by things like a future pump/filler
 * area check) survives unchanged. {@code Vec3d} became {@link Vec3}, and {@code java.util.Random} became
 * {@link RandomSource} to match {@link IZone#getRandomBlockPos}.
 */
public class ZonePlan implements IZone {
    private final HashMap<ChunkPos, ZoneChunk> chunkMapping = new HashMap<>();

    public ZonePlan() {}

    public ZonePlan(ZonePlan old) {
        for (ChunkPos chunkPos : old.chunkMapping.keySet()) {
            chunkMapping.put(chunkPos, new ZoneChunk(old.chunkMapping.get(chunkPos)));
        }
    }

    public boolean get(int x, int z) {
        int xChunk = x >> 4;
        int zChunk = z >> 4;
        ChunkPos chunkId = new ChunkPos(xChunk, zChunk);
        ZoneChunk property;

        if (!chunkMapping.containsKey(chunkId)) {
            return false;
        } else {
            property = chunkMapping.get(chunkId);
            return property.get(x & 0xF, z & 0xF);
        }
    }

    public void set(int x, int z, boolean val) {
        int xChunk = x >> 4;
        int zChunk = z >> 4;
        ChunkPos chunkId = new ChunkPos(xChunk, zChunk);
        ZoneChunk property;

        if (!chunkMapping.containsKey(chunkId)) {
            if (val) {
                property = new ZoneChunk();
                chunkMapping.put(chunkId, property);
            } else {
                return;
            }
        } else {
            property = chunkMapping.get(chunkId);
        }

        property.set(x & 0xF, z & 0xF, val);

        if (property.isEmpty()) {
            chunkMapping.remove(chunkId);
        }
    }

    public ZonePlan getWithOffset(int offsetX, int offsetZ) {
        ZonePlan zonePlan = new ZonePlan();
        for (Map.Entry<ChunkPos, ZoneChunk> chunkEntry : chunkMapping.entrySet()) {
            for (Integer packed : chunkEntry.getValue().getAll()) {
                int lx = packed & 0xFFFF;
                int lz = (packed >> 16) & 0xFFFF;
                zonePlan.set(
                    chunkEntry.getKey().getMinBlockX() + lx + offsetX,
                    chunkEntry.getKey().getMinBlockZ() + lz + offsetZ,
                    true
                );
            }
        }
        return zonePlan;
    }

    public boolean hasChunk(ChunkPos chunkPos) {
        return chunkMapping.containsKey(chunkPos);
    }

    public Set<ChunkPos> getChunkPoses() {
        return chunkMapping.keySet();
    }

    public HashMap<ChunkPos, ZoneChunk> getChunkMapping() {
        return chunkMapping;
    }

    public void writeToNBT(CompoundTag nbt) {
        nbt.put(
            "chunkMapping",
            NBTUtilBC.writeCompoundList(
                chunkMapping.entrySet().stream()
                    .map(entry -> {
                        CompoundTag zoneChunkTag = new CompoundTag();
                        entry.getValue().writeToNBT(zoneChunkTag);
                        zoneChunkTag.putInt("chunkX", entry.getKey().x());
                        zoneChunkTag.putInt("chunkZ", entry.getKey().z());
                        return zoneChunkTag;
                    })
            )
        );
    }

    public void readFromNBT(CompoundTag nbt) {
        chunkMapping.clear();
        NBTUtilBC.readCompoundList(nbt.get("chunkMapping"))
            .forEach(zoneChunkTag -> {
                ZoneChunk chunk = new ZoneChunk();
                chunk.readFromNBT(zoneChunkTag);
                chunkMapping.put(
                    new ChunkPos(
                        zoneChunkTag.getIntOr("chunkX", 0),
                        zoneChunkTag.getIntOr("chunkZ", 0)
                    ),
                    chunk
                );
            });
    }

    @Override
    public double distanceTo(BlockPos index) {
        return Math.sqrt(distanceToSquared(index));
    }

    @Override
    public double distanceToSquared(BlockPos index) {
        double maxSqrDistance = Double.MAX_VALUE;

        for (Map.Entry<ChunkPos, ZoneChunk> e : chunkMapping.entrySet()) {
            double dx = (e.getKey().x() << 4 + 8) - index.getX();
            double dz = (e.getKey().z() << 4 + 8) - index.getZ();

            double sqrDistance = dx * dx + dz * dz;

            if (sqrDistance < maxSqrDistance) {
                maxSqrDistance = sqrDistance;
            }
        }

        return maxSqrDistance;
    }

    @Override
    public boolean contains(Vec3 point) {
        int xBlock = (int) Math.floor(point.x);
        int zBlock = (int) Math.floor(point.z);

        return get(xBlock, zBlock);
    }

    @Override
    public BlockPos getRandomBlockPos(RandomSource rand) {
        if (chunkMapping.isEmpty()) {
            return null;
        }

        int chunkId = rand.nextInt(chunkMapping.size());

        for (Map.Entry<ChunkPos, ZoneChunk> e : chunkMapping.entrySet()) {
            if (chunkId == 0) {
                BlockPos i = e.getValue().getRandomBlockPos(rand);
                int x = (e.getKey().x() << 4) + i.getX();
                int z = (e.getKey().z() << 4) + i.getZ();

                return new BlockPos(x, i.getY(), z);
            }

            chunkId--;
        }

        return null;
    }
}
