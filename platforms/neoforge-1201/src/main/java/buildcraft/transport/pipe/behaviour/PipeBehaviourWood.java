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
import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjCapabilityHelper;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.IFlowFluid;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventFluid;
import buildcraft.api.transport.pipe.PipeEventHandler;

import buildcraft.lib.inventory.filter.StackFilter;

/**
 * The wooden pipe's behaviour -- this port's first *active* pipe. It pulls items out of whatever inventory its
 * single active face ({@link PipeBehaviourDirectional}) currently points at, but ONLY when it has MJ to spend, at
 * a flat {@link #MJ_PER_ITEM} cost each. A close port of 1.12.2's own {@code PipeBehaviourWood} (confirmed by
 * re-reading that file directly for this batch's own design research, not assumed from memory of any other
 * BuildCraft version's own wooden pipe).
 *
 * <p><b>The fluid-extraction branch is back</b> now that the wooden fluid pipe ({@code PIPE_WOOD_FLUID}) exists:
 * with an {@code IFlowFluid} flow, {@link #extract} pulls up to one millibucket per {@link #MJ_PER_MILLIBUCKET}
 * of power from the active face's tank via {@code IFlowFluid#tryExtractFluid}, and {@link #fluidSideCheck} stops
 * the flow pushing fluid back out through that same face -- both straight from 1.12.2, which the item batch had
 * dropped while no fluid flow existed.
 *
 * <p><b>{@code BCTransportConfig.mjPerItem} becomes a plain local constant, not a ported config class.</b>
 * {@code BCTransportConfig} is a whole 1.12.2 Forge {@code Configuration}-file system with no equivalent anywhere
 * in this port; only the one numeric value this behaviour actually reads is kept here, at its 1.12.2 default
 * ({@code MjAPI.MJ}, i.e. a full Minecraft Joule per item), and likewise {@code mjPerMillibucket} as
 * {@link #MJ_PER_MILLIBUCKET} (its default, 1000 micro-MJ -- a thousandth of an MJ per millibucket).
 *
 * <p><b>{@code addActions}/{@code onActionActivate} and {@code getTextureData}/network payload sync stay
 * dropped</b> -- see {@link PipeBehaviourDirectional}'s own javadoc for all of them (the active face now reaches
 * the client through whole-tile NBT sync plus the {@code active} blockstate property instead, which is also what
 * renders the "filled" face {@code getTextureData} used to pick). Wrench-driven facing selection, dropped by this
 * class's own first batch, is back (as a wrench cycle) -- also inherited from {@link PipeBehaviourDirectional}.
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

    /** 1.12.2's {@code BCTransportConfig.mjPerMillibucket} default -- see this class's own javadoc. */
    private static final long MJ_PER_MILLIBUCKET = 1_000;

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

    @PipeEventHandler
    public void fluidSideCheck(PipeEventFluid.SideCheck sideCheck) {
        if (currentDir.face != null) {
            sideCheck.disallow(currentDir.face);
        }
    }

    protected long extract(long power, boolean simulate) {
        if (power > 0) {
            if (pipe.getFlow() instanceof IFlowItems flow) {
                int maxItems = (int) (power / MJ_PER_ITEM);
                if (maxItems > 0) {
                    int extracted = extractItems(flow, getCurrentDir(), maxItems, simulate);
                    if (extracted > 0) {
                        return power - extracted * MJ_PER_ITEM;
                    }
                }
            } else if (pipe.getFlow() instanceof IFlowFluid flow) {
                int maxMillibuckets = (int) Math.min(Integer.MAX_VALUE, power / MJ_PER_MILLIBUCKET);
                if (maxMillibuckets > 0) {
                    int extracted = extractFluid(flow, getCurrentDir(), maxMillibuckets, simulate);
                    if (extracted > 0) {
                        return power - extracted * MJ_PER_MILLIBUCKET;
                    }
                }
            }
        }
        return power;
    }

    protected int extractItems(IFlowItems flow, @Nullable Direction dir, int count, boolean simulate) {
        return flow.tryExtractItems(count, dir, null, StackFilter.ALL, simulate);
    }

    /** 1.12.2 returned the extracted {@code FluidStack}; only its amount was ever used. */
    protected int extractFluid(IFlowFluid flow, @Nullable Direction dir, int millibuckets, boolean simulate) {
        if (dir == null) {
            return 0;
        }
        FluidStack extracted = flow.tryExtractFluid(millibuckets, dir, null, simulate);
        return extracted == null ? 0 : extracted.getAmount();
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
