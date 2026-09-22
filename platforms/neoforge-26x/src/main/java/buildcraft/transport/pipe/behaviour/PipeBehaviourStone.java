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
 * The stone pipe's behaviour: no state of its own beyond {@link PipeBehaviourSeparate}, just tunes item travel
 * speed towards a fast, constant crawl via a {@code @PipeEventHandler} static method -- a direct port of
 * 1.12.2's own {@code PipeBehaviourStone} (31 lines, re-read directly for this batch rather than assumed from
 * {@link PipeBehaviourCobble}'s own shape, even though the two classes end up structurally identical). Same
 * {@link #SPEED_TARGET} as {@link PipeBehaviourCobble}/{@link PipeBehaviourQuartz} ({@code 0.01}), but a
 * {@link #SPEED_DELTA} of {@code 0.008} -- a quick ramp, faster than {@link PipeBehaviourQuartz}'s
 * {@code 0.002} crawl, gentler than {@link PipeBehaviourCobble}'s own {@code 0.02}. {@link #SPEED_DELTA}/
 * {@link #SPEED_TARGET} are package-visible (not {@code private}) so {@link PipeBehaviourSandstone} can reuse
 * them verbatim, matching the real 1.12.2 {@code PipeBehaviourSandstone} which references
 * {@code PipeBehaviourStone}'s own constants directly rather than duplicating them.
 */
public class PipeBehaviourStone extends PipeBehaviourSeparate {
    static final double SPEED_DELTA = 0.008;
    static final double SPEED_TARGET = 0.01;

    public PipeBehaviourStone(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourStone(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @PipeEventHandler
    public static void modifySpeed(PipeEventItem.ModifySpeed event) {
        event.modifyTo(SPEED_TARGET, SPEED_DELTA);
    }
}
