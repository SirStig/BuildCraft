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

import net.neoforged.neoforge.capabilities.BlockCapability;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjCapabilities;
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
 * <p><b>{@code addActions}/{@code onActionActivate} and {@code getTextureData}/network payload sync stay
 * dropped</b> -- see {@link PipeBehaviourDirectional}'s own javadoc for all of them (the active face now reaches
 * the client through whole-tile NBT sync plus the {@code active} blockstate property instead, which is also what
 * renders the "filled" face {@code getTextureData} used to pick). Wrench-driven facing selection, dropped by this
 * class's own first batch, is back (as a wrench cycle) -- also inherited from {@link PipeBehaviourDirectional}.
 *
 * <p><b>{@code MjCapabilityHelper} cannot be reused here as a black box on this target, despite the real 1.12.2
 * class holding one as a field and delegating straight to it.</b> On 26.x, {@code buildcraft.api.mj.
 * MjCapabilityHelper} was restructured into a static {@code registerAll(RegisterCapabilitiesEvent,
 * BlockEntityType)} registrar (see its own javadoc) that decides which MJ interfaces to expose by an
 * {@code instanceof} check against the <em>block entity</em> itself -- and, confirmed by grepping the whole 26.x
 * source tree, it has zero real callers anywhere in this codebase, even for existing real {@code IMjReceiver}s
 * ({@code TilePowerConsumerTester} registers its own capability by hand in {@code BCCoreRegistries} instead of
 * going through it). That shape cannot apply here at all: {@code TilePipeHolder} is one shared block entity type
 * for every pipe material, so an {@code instanceof IMjReceiver} check against the <em>tile</em> would never be
 * able to tell "this particular pipe happens to be wood" from any other material sharing the same tile class.
 * Instead, this class exposes itself directly through the same generic {@code getCapability} dispatch chain the
 * item-transfer capability already established in the previous pipe batch ({@code TilePipeHolder#getCapability}
 * -> {@code Pipe#getCapability} -> here -- see {@link #getCapability} below), and
 * {@code BCTransportRegistries#registerCapabilities} registers {@link MjCapabilities#CONNECTOR}/
 * {@link MjCapabilities#RECEIVER}/{@link MjCapabilities#REDSTONE_RECEIVER} against the shared pipe holder tile
 * type the same way it already registers the vanilla-interop item capability -- see that class's own javadoc.
 * 1.20.1 (see that platform's copy of this file) genuinely can reuse {@code MjCapabilityHelper} exactly as
 * originally shaped, since that target's copy is still the instance-based {@code ICapabilityProvider} 1.12.2
 * had.
 */
public class PipeBehaviourWood extends PipeBehaviourDirectional implements IMjRedstoneReceiver, IDebuggable {

    /** 1.12.2's {@code BCTransportConfig.mjPerItem} default -- see this class's own javadoc. */
    private static final long MJ_PER_ITEM = MjAPI.MJ;

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
    @SuppressWarnings("unchecked")
    public <T> T getCapability(BlockCapability<T, Direction> capability, @Nullable Direction facing) {
        if (capability == MjCapabilities.CONNECTOR || capability == MjCapabilities.RECEIVER
            || capability == MjCapabilities.REDSTONE_RECEIVER) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("Facing = " + currentDir);
    }
}
