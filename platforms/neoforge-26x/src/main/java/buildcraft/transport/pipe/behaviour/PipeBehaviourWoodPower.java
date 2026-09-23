/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;

/**
 * The wooden kinesis pipe's behaviour -- the one power-pipe material (alongside {@code diamond_wood_power}, not
 * ported this batch, see {@code BCTransportRegistries}' own scope notes) that accepts power directly from an
 * engine or other MJ source. Every other power-pipe material shares an ordinary item/fluid-pipe behaviour
 * (Cobble/Stone/Sandstone/Quartz/Gold), exactly as 1.12.2's own {@code BCTransportPipes#preInit} reuses them --
 * this is the only one that needs its own class, and only because of {@link #canConnect(Direction, PipeBehaviour)}
 * below.
 *
 * <p>A close port of 1.12.2's own {@code PipeBehaviourWoodPower} (58 lines): {@link #canConnect} is unchanged
 * (two wooden kinesis pipes never connect directly to each other -- forcing a kinesis network to route through a
 * more expensive material past the first wooden segment, the same deliberate game-design restriction
 * {@code PipeBehaviourWood} already applies to the wooden item pipe). {@link #getTextureIndex} is ported too, even
 * though nothing in this port's rendering pipeline calls it yet (confirmed by grepping the whole 26.x source tree:
 * {@code getTextureIndex}/{@code getTextureData} have zero callers outside {@code PipeBehaviour} itself) -- kept
 * for parity and so a future renderer has the right per-face "filled" logic to read, exactly like every other
 * currently-uncalled {@code getTextureIndex} override already in this batch.
 */
public class PipeBehaviourWoodPower extends PipeBehaviour {

    public PipeBehaviourWoodPower(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWoodPower(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourWoodPower);
    }

    @Override
    public int getTextureIndex(@Nullable Direction face) {
        if (face == null) {
            return 0;
        }
        if (pipe.getConnectedPipe(face) != null) {
            return 0;
        }
        BlockEntity tile = pipe.getConnectedTile(face);
        if (tile == null) {
            return 0;
        }
        IMjReceiver recv = pipe.getHolder().getPipeLevel().getCapability(MjCapabilities.RECEIVER, tile.getBlockPos(), face.getOpposite());
        return recv == null ? 1 : recv.canReceive() ? 0 : 1;
    }
}
