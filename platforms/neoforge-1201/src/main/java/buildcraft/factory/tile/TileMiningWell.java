/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.inventory.ItemTransactorCapabilities;

import buildcraft.lib.inventory.AutomaticProvidingTransactor;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.InventoryUtil;

import buildcraft.BCFactoryRegistries;

/**
 * Digs straight down from its own position, one block at a time, powered by MJ, inserting whatever it digs up
 * into the best nearby inventory it can find. Mirrors the 26.x class of the same name -- see that one's javadoc
 * for the full account of what changed from 1.12.2 (why {@code IWorldEventListener} is dropped rather than
 * replaced, and why there is no {@code owner} field here unlike {@code TileChute}/{@code TileEngineWood}). This
 * file differs only in the usual 1.20.1 places: capabilities are exposed through {@code getCapability} rather
 * than registered against the block entity type, and {@link #onMinerRemoved()} is called by
 * {@code BlockMiningWell#onRemove} rather than a {@code BlockEntity} hook (see {@code TileMiner}'s own javadoc).
 */
public class TileMiningWell extends TileMiner {
    private boolean shouldCheck = true;
    private final SafeTimeTracker tracker = new SafeTimeTracker(256);
    private final LazyOptional<IItemTransactor> transactorCap = LazyOptional.of(() -> AutomaticProvidingTransactor.INSTANCE);

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

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == ItemTransactorCapabilities.ITEM_TRANSACTOR) {
            return transactorCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        transactorCap.invalidate();
    }
}
