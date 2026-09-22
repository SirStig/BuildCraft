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
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;

/**
 * The golden pipe's behaviour: the speed-boost material. A direct port of 1.12.2's own {@code PipeBehaviourGold}
 * (32 lines): the same single static {@code @PipeEventHandler} speed tweak {@link PipeBehaviourStone}/
 * {@link PipeBehaviourQuartz} use, but ramping <em>up</em> -- a {@link #SPEED_TARGET} of {@code 0.25} (25x the
 * {@code 0.01} every other speed-modifier material in this port settles to) reached in steps of up to
 * {@link #SPEED_DELTA} ({@code 0.07}) per pipe traversed.
 *
 * <p>Extends {@link PipeBehaviour} directly, not {@link PipeBehaviourSeparate}, exactly as the real 1.12.2 class
 * does -- so a golden pipe connects to a pipe of any other material (the classic "gold accelerator segment in a
 * cobblestone line" layout), unlike stone/cobblestone/quartz, which refuse each other.
 */
public class PipeBehaviourGold extends PipeBehaviour {
    private static final double SPEED_DELTA = 0.07;
    private static final double SPEED_TARGET = 0.25;

    public PipeBehaviourGold(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourGold(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @PipeEventHandler
    public static void modifySpeed(PipeEventItem.ModifySpeed event) {
        event.modifyTo(SPEED_TARGET, SPEED_DELTA);
    }
}
