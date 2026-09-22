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
import buildcraft.api.transport.pipe.IPipe.ConnectedType;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventFluid;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;

/**
 * The clay pipe's behaviour: at a junction, always prefers handing an item to an adjacent inventory over passing
 * it on down another pipe. A direct port of 1.12.2's own {@code PipeBehaviourClay} (53 lines):
 * {@link #orderSides} bumps every {@link ConnectedType#TILE} face's priority in the
 * {@code PipeEventItem.SideCheck} ordering, so {@code SideCheck#getOrder()} returns the inventory faces as the
 * first (highest-priority) group and {@code PipeFlowItems} only ever falls back to a pipe face once none are left
 * (e.g. every inventory refused the item and it bounced).
 *
 * <p>The {@code PipeEventFluid.SideCheck} overload of {@link #orderSides} does the same for the clay fluid pipe
 * ({@code PIPE_CLAY_FLUID}): the centre fills tank faces before pipe faces. (Dropped by the item batch while no
 * fluid flow existed; restored with it.) Extends {@link PipeBehaviour} directly, not {@link PipeBehaviourSeparate}, matching the original.
 */
public class PipeBehaviourClay extends PipeBehaviour {
    public PipeBehaviourClay(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourClay(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
    }

    @PipeEventHandler
    public void orderSides(PipeEventItem.SideCheck ordering) {
        for (Direction face : Direction.values()) {
            ConnectedType type = pipe.getConnectedType(face);
            if (type == ConnectedType.TILE) {
                /* We only really need to increase the priority, but using a larger number (100) means that it doesn't
                 * matter what plugs are attached (e.g. filters) and this will always prefer to go into inventories
                 * above the correct filters. (Although note that the filters still matter) */
                ordering.increasePriority(face, 100);
            }
        }
    }

    @PipeEventHandler
    public void orderSides(PipeEventFluid.SideCheck ordering) {
        for (Direction face : Direction.values()) {
            ConnectedType type = pipe.getConnectedType(face);
            if (type == ConnectedType.TILE) {
                // Same reasoning as the item overload above.
                ordering.increasePriority(face, 100);
            }
        }
    }
}
