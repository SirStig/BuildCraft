/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.tile;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import org.jetbrains.annotations.NotNull;

import buildcraft.BCCoreRegistries;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.lib.tile.TileBC;

/**
 * A machine that accepts unlimited power and reports what it received. Used to test power generation and transport.
 *
 * <p>Mirrors the 26.x class of the same name. Two things differ on 1.20.1:
 *
 * <ul>
 * <li>NBT is still {@code CompoundTag}, so the hooks are {@code load}/{@code saveAdditional} rather than
 *     {@code loadAdditional}/{@code saveAdditional} over {@code ValueInput}/{@code ValueOutput}.</li>
 * <li>Capabilities are exposed by the block entity itself, through {@code getCapability} returning a
 *     {@link LazyOptional}. 26.x inverts this -- there the capability is registered against the block entity
 *     <em>type</em> and the block entity knows nothing about it. That also means the handles have to be invalidated
 *     in {@link #invalidateCaps()}, which has no 26.x equivalent.</li>
 * </ul>
 */
public class TilePowerConsumerTester extends TileBC implements IMjReceiver {

    private final LazyOptional<IMjReceiver> receiverCap = LazyOptional.of(() -> this);
    private final LazyOptional<IMjConnector> connectorCap = LazyOptional.of(() -> this);

    private long lastReceived;
    private long nextTickReceived;
    private long lastTickReceived;
    private long totalReceived;

    public TilePowerConsumerTester(BlockPos pos, BlockState state) {
        super(BCCoreRegistries.POWER_TESTER_TYPE.get(), pos, state);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        lastReceived = nbt.getLong("last");
        nextTickReceived = nbt.getLong("nt");
        lastTickReceived = nbt.getLong("lt");
        totalReceived = nbt.getLong("total");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putLong("last", lastReceived);
        nbt.putLong("nt", nextTickReceived);
        nbt.putLong("lt", lastTickReceived);
        nbt.putLong("total", totalReceived);
    }

    /** Rolls the per-tick accumulator over. Driven by the block's ticker; was {@code ITickable.update()}. */
    public void serverTick() {
        lastTickReceived = nextTickReceived;
        nextTickReceived = 0;
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == MjCapabilities.RECEIVER) {
            return receiverCap.cast();
        }
        if (cap == MjCapabilities.CONNECTOR) {
            return connectorCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        receiverCap.invalidate();
        connectorCap.invalidate();
    }

    // IMjReceiver

    @Override
    public boolean canConnect(@NotNull IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        return 100_000 * MjAPI.MJ;
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        if (!simulate) {
            lastReceived = microJoules;
            nextTickReceived += microJoules;
            totalReceived += microJoules;
            setChanged();
        }
        return 0;
    }

    // Debug readout. The 1.12.2 IDebuggable interface lives in the api tiles package, not ported yet.

    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("");
        left.add("Last received = " + MjAPI.formatMj(lastReceived) + " MJ");
        left.add("Tick received = " + MjAPI.formatMj(lastTickReceived) + " MJ");
        left.add("Total received = " + MjAPI.formatMj(totalReceived) + " MJ");
    }

    public long getTotalReceived() {
        return totalReceived;
    }
}
