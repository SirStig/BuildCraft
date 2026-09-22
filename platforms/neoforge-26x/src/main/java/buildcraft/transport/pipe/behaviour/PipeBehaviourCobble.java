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
 * The cobblestone pipe's behaviour: no state of its own beyond {@link PipeBehaviourSeparate}, just tunes item
 * travel speed towards a slow, constant crawl via a {@code @PipeEventHandler} static method -- a direct port of
 * 1.12.2's own {@code PipeBehaviourCobble}, and this batch's proof that the event-dispatch mechanism
 * ({@code buildcraft.transport.pipe.PipeEventBus}) actually supports the {@code @PipeEventHandler} pattern real
 * pipe behaviours rely on, not just a stand-in for it.
 */
public class PipeBehaviourCobble extends PipeBehaviourSeparate {
    private static final double SPEED_DELTA = 0.02;
    private static final double SPEED_TARGET = 0.01;

    public PipeBehaviourCobble(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourCobble(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @PipeEventHandler
    public static void modifySpeed(PipeEventItem.ModifySpeed event) {
        event.modifyTo(SPEED_TARGET, SPEED_DELTA);
    }
}
