/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.tile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.core.tile.ITileOilSpring;

import buildcraft.BCEnergyRegistries;

/**
 * Renamed and trimmed down from 1.12.2's {@code TileSpringOil} -- see the 26.x copy of this class for the full
 * account of the two scope cuts (a plain {@link UUID} in place of {@code GameProfile}, and no NBT persistence for
 * per-player pump progress towards an advancement -- {@code buildcraftfactory:black_gold} -- that is not authored
 * anywhere in this port yet).
 */
public class TileSpringOil extends BlockEntity implements IDebuggable, ITileOilSpring {

    private final Map<UUID, PlayerPumpInfo> pumpProgress = new ConcurrentHashMap<>();

    /** An approximation of the total number of oil source blocks in the oil spring this tile belongs to. Set by
     * whatever placed this spring (a world-generation feature, eventually); never by a player. */
    public int totalSources;

    public TileSpringOil(BlockPos pos, BlockState state) {
        super(BCEnergyRegistries.SPRING_OIL_TYPE.get(), pos, state);
    }

    @Override
    public void onPumpOil(UUID pumpOwner, BlockPos oilPos) {
        if (pumpOwner == null) {
            return;
        }
        PlayerPumpInfo info = pumpProgress.computeIfAbsent(pumpOwner, PlayerPumpInfo::new);
        info.lastPumpTick = level == null ? 0 : level.getGameTime();
        info.sourcesPumped++;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        totalSources = nbt.getInt("totalSources");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putInt("totalSources", totalSources);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("totalSources = " + totalSources);
        boolean added = false;
        for (Map.Entry<UUID, PlayerPumpInfo> entry : pumpProgress.entrySet()) {
            if (!added) {
                left.add("Player Progress:");
                added = true;
            }
            long ticksAgo = (level == null ? 0 : level.getGameTime()) - entry.getValue().lastPumpTick;
            left.add("  " + entry.getKey() + " = " + entry.getValue().sourcesPumped + " ( " + ticksAgo / 20 + "s )");
        }
    }

    private static final class PlayerPumpInfo {
        final UUID player;
        long lastPumpTick = -1;
        int sourcesPumped = 0;

        PlayerPumpInfo(UUID player) {
            this.player = player;
        }
    }
}
