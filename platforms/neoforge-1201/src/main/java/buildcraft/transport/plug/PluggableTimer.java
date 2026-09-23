/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventStatement;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.BCTransportRegistries;
import buildcraft.transport.statements.TriggerTimer;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.plug.PluggableTimer}: a blocking plug with no behaviour of its own
 * beyond offering {@link TriggerTimer}'s three durations to a gate on the same pipe -- ported onto this port's
 * already-real {@code PipeEventStatement.AddTriggerInternal}/{@code TriggerProviderPipes} pipeline (see
 * {@code TriggerPipeSignal}/{@code TriggerProviderPipes} for the identical registration shape this batch
 * follows), no state or NBT of its own. {@code getModelRenderKey} is dropped, matching every other pluggable in
 * this batch (see {@link PluggableBlocker}'s own javadoc).
 */
public class PluggableTimer extends PipePluggable {

    private static final AABB[] BOXES = new AABB[6];

    static {
        double ll = 2 / 16.0;
        double lu = 4 / 16.0;
        double ul = 12 / 16.0;
        double uu = 14 / 16.0;

        double min = 5 / 16.0;
        double max = 11 / 16.0;

        BOXES[Direction.DOWN.get3DDataValue()] = new AABB(min, ll, min, max, lu, max);
        BOXES[Direction.UP.get3DDataValue()] = new AABB(min, ul, min, max, uu, max);
        BOXES[Direction.NORTH.get3DDataValue()] = new AABB(min, min, ll, max, max, lu);
        BOXES[Direction.SOUTH.get3DDataValue()] = new AABB(min, min, ul, max, max, uu);
        BOXES[Direction.WEST.get3DDataValue()] = new AABB(ll, min, min, lu, max, max);
        BOXES[Direction.EAST.get3DDataValue()] = new AABB(ul, min, min, uu, max, max);
    }

    public PluggableTimer(PluggableDefinition definition, IPipeHolder holder, Direction side) {
        super(definition, holder, side);
    }

    // PipePluggable

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.get3DDataValue()];
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        return new ItemStack(BCTransportRegistries.PLUG_TIMER.get());
    }

    @PipeEventHandler
    public void addInternalTriggers(PipeEventStatement.AddTriggerInternal event) {
        event.triggers.add(TriggerTimer.SHORT);
        event.triggers.add(TriggerTimer.MEDIUM);
        event.triggers.add(TriggerTimer.LONG);
    }
}
