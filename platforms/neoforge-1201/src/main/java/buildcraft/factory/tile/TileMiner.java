/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.mj.MjEffects;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.tiles.IHasWork;
import buildcraft.api.tiles.TilesAPI;

import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC;

import buildcraft.BCFactoryRegistries;

/**
 * Base class for a machine that digs a vertical shaft, one block at a time, powered by MJ. Mirrors the 26.x class
 * of the same name -- see that one's javadoc for the full account of what changed from 1.12.2 (the dropped
 * render-only fields, the dropped {@code offset}/{@code IdAllocator}, the dropped {@code migrateOldNBT}, and why
 * {@code TilesAPI.HAS_WORK} is wired to {@code !isComplete()} rather than reproducing 1.12.2's effectively-dead
 * {@code isComplete} field read). This file differs only in the usual 1.20.1 places: NBT is still
 * {@code CompoundTag} ({@code load}/{@code saveAdditional} rather than {@code loadAdditional}/
 * {@code saveAdditional} over {@code ValueInput}/{@code ValueOutput}), and capabilities are exposed by the block
 * entity itself through {@code getCapability} rather than registered against the block entity type -- so, unlike
 * 26.x, {@link #mjReceiver} and {@code TilesAPI.HAS_WORK} are queried here rather than in
 * {@code BCFactoryRegistries}.
 *
 * <p>There is also no {@code BlockEntity#preRemoveSideEffects} hook on this target at all (26.x added it; see
 * that copy of this class) -- {@link #onMinerRemoved()} is instead called directly by
 * {@code BlockMiningWell#onRemove}, the same {@code Block}-drives-{@code BlockEntity} shape 1.12.2's own
 * {@code BlockBCTile_Neptune#breakBlock}/{@code TileBC_Neptune#onRemove()} pair used.
 */
public abstract class TileMiner extends TileBC implements IDebuggable {

    /** {@code BCCoreConfig.miningMaxDepth}'s unconfigured default (512); see the 26.x copy of this class's
     * javadoc. */
    protected static final int MINING_MAX_DEPTH = 512;

    protected int progress = 0;
    @Nullable
    protected BlockPos currentPos = null;

    private int wantedLength = 0;

    protected final MjBattery battery = new MjBattery(getBatteryCapacity());
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);
    private final LazyOptional<IMjReceiver> receiverCap = LazyOptional.of(() -> mjReceiver);
    private final LazyOptional<IMjReadable> readableCap = LazyOptional.of(() -> mjReceiver);
    private final LazyOptional<IHasWork> hasWorkCap = LazyOptional.of(() -> () -> !isComplete());

    protected TileMiner(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract void mine();

    public void serverTick() {
        MjEffects.tick(level, worldPosition, battery);
        mine();
    }

    /** Called by {@code BlockMiningWell#onRemove} while this tile (and the block underneath it) still exist,
     * right before the block is actually replaced -- see the class javadoc for why 1.20.1 reaches this from the
     * block rather than a {@code BlockEntity} hook. Clears whatever tube shaft is still standing below, so
     * removing (or replacing) the miner does not leave an orphaned column behind. */
    public void onMinerRemoved() {
        clearTubeShaft();
    }

    protected void updateLength() {
        BlockPos target = getTargetPos();
        int newY = target != null ? target.getY() : worldPosition.getY();
        int newLength = worldPosition.getY() - newY;
        if (newLength != wantedLength) {
            clearTubeShaft();
            Block tube = BCFactoryRegistries.TUBE.get();
            for (int y = worldPosition.getY() - 1; y > newY; y--) {
                BlockPos shaftPos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
                level.setBlock(shaftPos, tube.defaultBlockState(), Block.UPDATE_ALL);
            }
            wantedLength = newLength;
        }
    }

    private void clearTubeShaft() {
        Block tube = BCFactoryRegistries.TUBE.get();
        for (int y = worldPosition.getY() - 1; y > worldPosition.getY() - MINING_MAX_DEPTH; y--) {
            BlockPos shaftPos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
            if (level.getBlockState(shaftPos).is(tube)) {
                level.removeBlock(shaftPos, false);
            } else {
                break;
            }
        }
    }

    protected BlockPos getTargetPos() {
        return currentPos;
    }

    public boolean isComplete() {
        return currentPos == null;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        currentPos = nbt.contains("currentPos") ? NbtUtils.readBlockPos(nbt.getCompound("currentPos")) : null;
        wantedLength = nbt.getInt("wantedLength");
        progress = nbt.getInt("progress");
        battery.setStored(nbt.getLong("battery"));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (currentPos != null) {
            nbt.put("currentPos", NbtUtils.writeBlockPos(currentPos));
        }
        nbt.putInt("wantedLength", wantedLength);
        nbt.putInt("progress", progress);
        nbt.putLong("battery", battery.getStored());
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("current = " + currentPos);
        left.add("wantedLength = " + wantedLength);
        left.add("isComplete = " + isComplete());
        left.add("progress = " + LocaleUtil.localizeMj(progress));
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == MjCapabilities.RECEIVER) {
            return receiverCap.cast();
        }
        if (cap == MjCapabilities.READABLE) {
            return readableCap.cast();
        }
        if (cap == TilesAPI.HAS_WORK) {
            return hasWorkCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        receiverCap.invalidate();
        readableCap.invalidate();
        hasWorkCap.invalidate();
    }

    protected long getBatteryCapacity() {
        return 500 * MjAPI.MJ;
    }
}
