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
 * The void pipe's behaviour: destroys every item that reaches its centre. A direct port of 1.12.2's own
 * {@code PipeBehaviourVoid} (62 lines): {@link #reachCenter} empties the stack in place, and
 * {@code PipeFlowItems#onItemReachCenter} already returns early the moment
 * {@code PipeEventItem.ReachCenter#getStack()} comes back empty -- the exact short-circuit the original relied on,
 * confirmed present in this port's own {@code PipeFlowItems} rather than assumed.
 *
 * <p><b>The {@code PipeEventFluid.OnMoveToCentre} handler is dropped</b>, the same call {@code PipeBehaviourWood}
 * already made for its own {@code fluidSideCheck}: no fluid pipe flow is registered anywhere in this port yet, so
 * no {@code PipeEventFluid} could ever reach this behaviour. (The original's sound-effect block inside that
 * handler was already commented out in 1.12.2 itself -- nothing is lost there either.)
 */
public class PipeBehaviourVoid extends PipeBehaviour {
    public PipeBehaviourVoid(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourVoid(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @PipeEventHandler
    public static void reachCenter(PipeEventItem.ReachCenter reachCenter) {
        reachCenter.getStack().setCount(0);
    }
}
