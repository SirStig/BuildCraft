/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;

/**
 * The quartz pipe's behaviour: identical shape to {@link PipeBehaviourStone} (extends
 * {@link PipeBehaviourSeparate}, adds only the {@code @PipeEventHandler} speed tweak), a direct port of
 * 1.12.2's own {@code PipeBehaviourQuartz} (31 lines). Same {@link #SPEED_TARGET} ({@code 0.01}) as every other
 * speed-modifier material, but the gentlest {@link #SPEED_DELTA} of the three new materials ({@code 0.002}) --
 * a slow, gradual ramp, unlike {@link PipeBehaviourStone}'s quicker {@code 0.008}. Unlike
 * {@link PipeBehaviourSandstone}, this class keeps its own constants {@code private}: nothing else in this
 * batch needs to reuse them, matching the real 1.12.2 source exactly.
 */
public class PipeBehaviourQuartz extends PipeBehaviourSeparate {
    private static final double SPEED_DELTA = 0.002;
    private static final double SPEED_TARGET = 0.01;

    public PipeBehaviourQuartz(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourQuartz(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @PipeEventHandler
    public static void modifySpeed(PipeEventItem.ModifySpeed event) {
        event.modifyTo(SPEED_TARGET, SPEED_DELTA);
    }
}
