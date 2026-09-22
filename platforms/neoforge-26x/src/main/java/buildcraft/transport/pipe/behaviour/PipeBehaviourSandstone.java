/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;

/**
 * The sandstone pipe's behaviour: the one material in this batch that is not a {@link PipeBehaviourSeparate}
 * subclass -- a direct port of 1.12.2's own {@code PipeBehaviourSandstone} (44 lines), which extends
 * {@code PipeBehaviour} directly for exactly one reason: it needs to connect to <em>any other pipe</em>
 * (regardless of that pipe's own behaviour class), not just another sandstone pipe, while refusing every
 * inventory connection outright. {@link #canConnect(Direction, PipeBehaviour)} unconditionally returns
 * {@code true} (pipe-to-pipe always allowed, universally, unlike {@link PipeBehaviourSeparate}'s own
 * same-class-only rule) and {@link #canConnect(Direction, BlockEntity)} unconditionally returns {@code false}
 * (never connects to a plain tile/inventory) -- {@code TileEntity} in 1.12.2 is {@link BlockEntity} here,
 * matching {@link PipeBehaviour}'s own already-ported base method signature.
 *
 * <p>Same {@code @PipeEventHandler} speed tweak as {@link PipeBehaviourStone}, reusing that class's own
 * {@link PipeBehaviourStone#SPEED_TARGET}/{@link PipeBehaviourStone#SPEED_DELTA} constants directly rather than
 * duplicating the literals -- a direct port of the real 1.12.2 {@code PipeBehaviourSandstone}, which does the
 * same thing against the real {@code PipeBehaviourStone}.
 */
public class PipeBehaviourSandstone extends PipeBehaviour {
    public PipeBehaviourSandstone(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourSandstone(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return true;
    }

    @Override
    public boolean canConnect(Direction face, BlockEntity oTile) {
        return false;
    }

    @PipeEventHandler
    public static void modifySpeed(PipeEventItem.ModifySpeed event) {
        event.modifyTo(PipeBehaviourStone.SPEED_TARGET, PipeBehaviourStone.SPEED_DELTA);
    }
}
