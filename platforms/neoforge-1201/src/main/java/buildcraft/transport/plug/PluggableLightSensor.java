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
import buildcraft.transport.statements.TriggerLightSensor;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.plug.PluggableLightSensor}: a blocking plug offering
 * {@link TriggerLightSensor}'s low/high-light triggers to a gate, sided (only the gate on the exact same face as
 * this plug can see them, ported onto this port's {@code PipeEventStatement.AddTriggerInternalSided} -- see
 * {@code PluggableTimer}'s own javadoc for the unsided sibling case). No state or NBT of its own.
 */
public class PluggableLightSensor extends PipePluggable {

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

    public PluggableLightSensor(PluggableDefinition definition, IPipeHolder holder, Direction side) {
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
        return new ItemStack(BCTransportRegistries.PLUG_LIGHT_SENSOR.get());
    }

    @PipeEventHandler
    public void addInternalTriggers(PipeEventStatement.AddTriggerInternalSided event) {
        if (event.side == this.side) {
            event.triggers.add(TriggerLightSensor.LOW);
            event.triggers.add(TriggerLightSensor.HIGH);
        }
    }
}
