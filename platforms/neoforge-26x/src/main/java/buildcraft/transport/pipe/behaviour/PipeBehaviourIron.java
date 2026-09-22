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

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeEventFluid;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;

/**
 * The iron pipe's behaviour: a one-way valve that only ever outputs through its single active face. A direct port
 * of 1.12.2's own {@code PipeBehaviourIron} (66 lines), built on {@link PipeBehaviourDirectional} -- the active
 * face is any connected side (pipe or inventory, {@link #canFaceDirection}), cycled with a wrench through
 * {@code BlockPipeHolder#attemptRotation} (see {@link PipeBehaviourDirectional}'s own javadoc for that path).
 *
 * <p>{@link #sideCheck} disallows every face except the active one (or every face, while no face is active);
 * {@link #tryBounce} then lets an item with nowhere left to go bounce back the way it came instead of being
 * dropped -- so an item entering through the active face itself (the one direction this pipe refuses to send
 * it back out of) returns to its sender rather than spilling on the ground, matching the original exactly.
 *
 * <p>The fluid pipe ({@code PIPE_IRON_FLUID}) uses this same behaviour: {@link #fluidSideCheck} is the fluid twin
 * of {@link #sideCheck} (the centre only ever pushes fluid out of the active face), and {@link #fluidInsert}
 * refuses fluid offered <em>through</em> the active face, so a neighbour on the output side cannot push back in.
 * (Both were dropped by the item batch while no fluid flow existed; restored with it.) {@code getTextureIndex} is
 * dropped, matching
 * {@code PipeBehaviourWood}'s own {@code getTextureData} drop: it is {@code @Deprecated} on this port's
 * {@code PipeBehaviour} and nothing in this port reads either method -- the active face's "filled" texture is
 * driven by the {@code active} blockstate property instead (see {@code BlockPipeHolder#ACTIVE}), for both
 * directional pipes at once.
 */
public class PipeBehaviourIron extends PipeBehaviourDirectional {
    public PipeBehaviourIron(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourIron(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @Override
    protected boolean canFaceDirection(@Nullable Direction dir) {
        return dir != null && pipe.isConnected(dir);
    }

    @PipeEventHandler
    public void sideCheck(PipeEventItem.SideCheck sideCheck) {
        if (currentDir == EnumPipePart.CENTER) {
            sideCheck.disallowAll();
        } else {
            sideCheck.disallowAllExcept(currentDir.face);
        }
    }

    @PipeEventHandler
    public static void tryBounce(PipeEventItem.TryBounce tryBounce) {
        tryBounce.canBounce = true;
    }

    @PipeEventHandler
    public void fluidSideCheck(PipeEventFluid.SideCheck sideCheck) {
        if (currentDir == EnumPipePart.CENTER) {
            sideCheck.disallowAll();
        } else {
            sideCheck.disallowAllExcept(currentDir.face);
        }
    }

    @PipeEventHandler
    public void fluidInsert(PipeEventFluid.TryInsert insert) {
        if (currentDir.face == insert.from) {
            insert.cancel();
        }
    }
}
