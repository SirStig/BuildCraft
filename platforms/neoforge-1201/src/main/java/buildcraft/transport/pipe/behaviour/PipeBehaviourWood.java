/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import net.minecraftforge.common.capabilities.Capability;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjCapabilityHelper;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;
import buildcraft.api.transport.pipe.PipeBehaviour;

import buildcraft.lib.inventory.filter.StackFilter;

/**
 * The wooden pipe's behaviour -- this port's first *active* pipe. It pulls items out of whatever inventory its
 * single active face ({@link PipeBehaviourDirectional}) currently points at, but ONLY when it has MJ to spend, at
 * a flat {@link #MJ_PER_ITEM} cost each. A close port of 1.12.2's own {@code PipeBehaviourWood} (confirmed by
 * re-reading that file directly for this batch's own design research, not assumed from memory of any other
 * BuildCraft version's own wooden pipe).
 *
 * <p><b>The fluid-extraction branch is dropped outright, not stubbed.</b> 1.12.2's {@code extract} tried both an
 * {@code IFlowItems} and an {@code IFlowFluid} branch; no fluid pipe of any kind is registered anywhere in this
 * port yet, so {@code pipe.getFlow() instanceof IFlowFluid} could never be true here -- an {@code instanceof}
 * that can never match is less honest than simply not writing the branch, so it (and the {@code fluidSideCheck}
 * {@code @PipeEventHandler}, which only ever mattered to a fluid-flow pipe) are both gone.
 *
 * <p><b>{@code BCTransportConfig.mjPerItem} becomes a plain local constant, not a ported config class.</b>
 * {@code BCTransportConfig} is a whole 1.12.2 Forge {@code Configuration}-file system with no equivalent anywhere
 * in this port; only the one numeric value this behaviour actually reads is kept here, at its 1.12.2 default
 * ({@code MjAPI.MJ}, i.e. a full Minecraft Joule per item). {@code mjPerMillibucket} is not needed at all, since
 * the fluid branch above is not ported.
 *
 * <p><b>Wrench-driven facing selection, {@code addActions}/{@code onActionActivate}, and
 * {@code getTextureData}/network payload sync are all dropped</b> -- see {@link PipeBehaviourDirectional}'s own
 * javadoc for the first two (inherited from there, not re-explained here), and this module's established
 * "no renderer yet" precedent for the third.
 *
 * <p><b>{@code MjCapabilityHelper} is reused directly here, exactly as originally shaped.</b> 1.20.1 still has
 * {@code ICapabilityProvider}, so {@code buildcraft.api.mj.MjCapabilityHelper} on this target is still the same
 * instance-based delegate 1.12.2 had: a machine (or, here, a pipe behaviour) holds one and forwards
 * {@code getCapability} straight to it -- see that class's own javadoc, and {@code TileEngineWood}/
 * {@code TileEngineStone}'s own MJ wiring for the identical pattern elsewhere in this port. 26.x (see that
 * platform's copy of this file) cannot do the same: its {@code MjCapabilityHelper} was restructured into a
 * static, {@code BlockEntityType}-keyed registrar that cannot apply to a per-behaviour-object capability on the
 * one shared pipe holder tile type -- see that file's own javadoc for the full account.
 */
public class PipeBehaviourWood extends PipeBehaviourDirectional implements IMjRedstoneReceiver, IDebuggable {

    /** 1.12.2's {@code BCTransportConfig.mjPerItem} default -- see this class's own javadoc. */
    private static final long MJ_PER_ITEM = MjAPI.MJ;

    private final MjCapabilityHelper mjCaps = new MjCapabilityHelper(this);

    public PipeBehaviourWood(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWood(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourWood);
    }

    @Override
    protected boolean canFaceDirection(@Nullable Direction dir) {
        return dir != null && pipe.getConnectedType(dir) == ConnectedType.TILE;
    }

    protected long extract(long power, boolean simulate) {
        if (power > 0 && pipe.getFlow() instanceof IFlowItems flow) {
            int maxItems = (int) (power / MJ_PER_ITEM);
            if (maxItems > 0) {
                int extracted = extractItems(flow, getCurrentDir(), maxItems, simulate);
                if (extracted > 0) {
                    return power - extracted * MJ_PER_ITEM;
                }
            }
        }
        return power;
    }

    protected int extractItems(IFlowItems flow, @Nullable Direction dir, int count, boolean simulate) {
        return flow.tryExtractItems(count, dir, null, StackFilter.ALL, simulate);
    }

    // IMjRedstoneReceiver

    @Override
    public boolean canConnect(@NotNull IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        final long power = 512 * MjAPI.MJ;
        return power - extract(power, true);
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        return extract(microJoules, simulate);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable Direction facing) {
        return mjCaps.getCapability(capability, facing).orElse(null);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("Facing = " + currentDir);
    }
}
