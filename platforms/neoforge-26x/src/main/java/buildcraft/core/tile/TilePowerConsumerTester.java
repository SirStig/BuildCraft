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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.BCCoreRegistries;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.tile.TileBC;

/**
 * A machine that accepts unlimited power and reports what it received. Used to test power generation and transport.
 *
 * <p>Ported from 1.12.2. The shape of the class is the same; what changed is the plumbing around it:
 *
 * <ul>
 * <li>NBT is read and written through {@code ValueInput}/{@code ValueOutput} instead of {@code NBTTagCompound}, and
 *     the hooks are named {@code loadAdditional}/{@code saveAdditional}.</li>
 * <li>{@code ITickable.update()} is gone. Ticking is driven by the block's {@code getTicker}, which is why
 *     {@link #serverTick} is a plain method rather than an override.</li>
 * <li>The capability is no longer attached by the block entity holding a {@code MjCapabilityHelper} provider; it is
 *     registered against the block entity <em>type</em> in {@code RegisterCapabilitiesEvent}. That is why this class
 *     simply implements {@link IMjReceiver} and nothing here wires it up.</li>
 * </ul>
 */
public class TilePowerConsumerTester extends TileBC implements IMjReceiver {

    private long lastReceived;
    private long nextTickReceived;
    private long lastTickReceived;
    private long totalReceived;

    public TilePowerConsumerTester(BlockPos pos, BlockState state) {
        // BlockEntityType.BlockEntitySupplier only passes position and state, so the type is looked up here.
        super(BCCoreRegistries.POWER_TESTER_TYPE.get(), pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lastReceived = input.getLongOr("last", 0);
        nextTickReceived = input.getLongOr("nt", 0);
        lastTickReceived = input.getLongOr("lt", 0);
        totalReceived = input.getLongOr("total", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("last", lastReceived);
        output.putLong("nt", nextTickReceived);
        output.putLong("lt", lastTickReceived);
        output.putLong("total", totalReceived);
    }

    /** Rolls the per-tick accumulator over. Driven by the block's ticker; was {@code ITickable.update()}. */
    public void serverTick() {
        lastTickReceived = nextTickReceived;
        nextTickReceived = 0;
    }

    // IMjReceiver

    @Override
    public boolean canConnect(IMjConnector other) {
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

    // Debug readout, shown by the wrench/debugger. The 1.12.2 IDebuggable interface lives in the api tiles package,
    // which is not ported yet, so for now this is a plain method.

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
