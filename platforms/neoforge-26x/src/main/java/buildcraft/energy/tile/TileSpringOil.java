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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.tiles.IDebuggable;

import buildcraft.core.tile.ITileOilSpring;

import buildcraft.BCEnergyRegistries;

/**
 * Renamed and trimmed down from 1.12.2's {@code TileSpringOil} -- the "infinite oil source" block entity that
 * sits under an oil deposit, tracking how much a given player has pumped from it (used upstream to unlock the
 * {@code black_gold} advancement once most of the well is drained).
 *
 * <p><b>Two real cuts, both scope, not oversights.</b> First: {@link ITileOilSpring}'s own javadoc covers why
 * {@code GameProfile} became a plain {@link UUID}. Second, and new here: the per-player pump progress
 * ({@link #pumpProgress}) is <em>not</em> persisted to NBT this pass -- 1.12.2 wrote/read a
 * {@code GameProfile}-keyed NBT list every save; the advancement it drives ({@code buildcraftfactory:black_gold})
 * was never actually authored anywhere in this port (confirmed via a repo-wide search -- no
 * {@code data/buildcraft/advancement/} entry references it), and {@code TilePump#onPumpOil} does not call this
 * class yet either (see that class's own javadoc). Persisting progress toward an advancement that cannot yet
 * fire is dead weight; {@link #totalSources} (the one field a future world-gen placer would actually set and
 * that matters even with the advancement unwired) is still saved. Both cuts are cheap to reverse once the
 * factory-side wiring and the advancement JSON exist.
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
        // See this class's own javadoc: the black_gold advancement this was meant to unlock at 7/8 pumped is not
        // authored anywhere in this port yet, so there is nothing left to trigger here beyond the bookkeeping.
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        totalSources = input.getIntOr("totalSources", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("totalSources", totalSources);
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
