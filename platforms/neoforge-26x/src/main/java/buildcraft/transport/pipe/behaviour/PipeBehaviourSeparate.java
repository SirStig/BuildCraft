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

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;

/**
 * The abstract "does this pipe connect to that other pipe" base every non-fancy material pipe extends -- a
 * direct port of 1.12.2's own {@code PipeBehaviourSeparate}. Two pipe behaviours of the same concrete class
 * (e.g. two cobblestone pipes) always connect to each other; two different {@code PipeBehaviourSeparate}
 * subclasses never do, matching every material's own behaviour never wanting to blend into a differently
 * material pipe run.
 */
public abstract class PipeBehaviourSeparate extends PipeBehaviour {
    public PipeBehaviourSeparate(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourSeparate(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        if (other instanceof PipeBehaviourSeparate) {
            return other.getClass() == getClass();
        } else {
            return true;
        }
    }
}
