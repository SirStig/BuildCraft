/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import buildcraft.api.core.SafeTimeTracker;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.InventoryUtil;

import buildcraft.BCFactoryRegistries;

/**
 * Digs straight down from its own position, one block at a time, powered by MJ, inserting whatever it digs up
 * into the best nearby inventory it can find -- BuildCraft's first ore-gathering machine, and the first thing in
 * this port to call {@link InventoryUtil#addToBestAcceptor}.
 *
 * <p>{@code IWorldEventListener}/{@code WorldEventListenerAdapter} (1.12.2's {@code worldEventListener}, an
 * "any block anywhere changed" hook registered through {@code World#addEventListener} in {@code validate()}) has
 * no cheap modern replacement -- confirmed via {@code javap}: {@code Level} carries no
 * {@code addListener}/{@code EventListener} method of any kind on this target any more, and neither NeoForge nor
 * vanilla exposes a global "any block changed" bus event to hook once instead (only per-position hooks like
 * {@code neighborChanged}, which would need registering on every block type in the game to reproduce the old
 * behaviour, not just once). The only thing that mechanism bought over the periodic {@link SafeTimeTracker} poll
 * already present either way was reacting slightly sooner to a block manually placed or removed in the miner's
 * own dig column; {@link #shouldCheck} and {@link #tracker} alone (a check every 256 ticks, ~12.8 seconds) cover
 * the same ground with, at most, that much extra latency. Dropped outright rather than reproduced.
 *
 * <p>Unlike {@code TileChute}/{@code TileEngineWood}, there is no {@code owner} field here: 1.12.2's
 * {@code getOwner()} fed a {@code GameProfile} into {@code BlockUtil.breakBlockAndGetDrops} purely to build a
 * {@code FakePlayer}, and this port's version of that method needs no such thing at all (see
 * {@link BlockUtil}'s own javadoc) -- there is no advancement to unlock and no fake player to attribute the
 * break to, so there is nothing left for an owner field to feed.
 */
public class TileMiningWell extends TileMiner {
    private boolean shouldCheck = true;
    private final SafeTimeTracker tracker = new SafeTimeTracker(256);

    public TileMiningWell(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.MINING_WELL_TYPE.get(), pos, state);
    }

    @Override
    protected void mine() {
        if (currentPos != null && canBreak()) {
            shouldCheck = true;
            long target = BlockUtil.computeBlockBreakPower(level, currentPos);
            progress += battery.extractPower(0, target - progress);
            if (progress >= target) {
                progress = 0;
                level.destroyBlockProgress(currentPos.hashCode(), currentPos, -1);
                BlockUtil.breakBlockAndGetDrops((ServerLevel) level, currentPos, new ItemStack(Items.DIAMOND_PICKAXE))
                    .ifPresent(stacks -> stacks.forEach(stack -> InventoryUtil.addToBestAcceptor(level, worldPosition, null, stack)));
                nextPos();
            } else if (!level.getBlockState(currentPos).isAir()) {
                level.destroyBlockProgress(currentPos.hashCode(), currentPos, (int) ((progress * 9) / target));
            }
        } else if (shouldCheck || tracker.markTimeIfDelay(level)) {
            nextPos();
            if (currentPos == null) {
                shouldCheck = false;
            }
        }
    }

    private boolean canBreak() {
        if (level.getBlockState(currentPos).isAir() || BlockUtil.isUnbreakableBlock(level, currentPos)) {
            return false;
        }
        FluidState fluid = BlockUtil.getFluidWithFlowing(level, currentPos);
        return fluid == null || fluid.getType().getFluidType().getViscosity(fluid, level, currentPos) <= 1000;
    }

    private void nextPos() {
        currentPos = worldPosition;
        while (true) {
            currentPos = currentPos.below();
            if (level.isOutsideBuildHeight(currentPos)) {
                break;
            }
            if (worldPosition.getY() - currentPos.getY() > MINING_MAX_DEPTH) {
                break;
            }
            if (canBreak()) {
                updateLength();
                return;
            } else if (!level.getBlockState(currentPos).isAir() && !level.getBlockState(currentPos).is(BCFactoryRegistries.TUBE.get())) {
                break;
            }
        }
        currentPos = null;
        updateLength();
    }

    @Override
    public void onMinerRemoved() {
        super.onMinerRemoved();
        if (currentPos != null) {
            level.destroyBlockProgress(currentPos.hashCode(), currentPos, -1);
        }
    }
}
