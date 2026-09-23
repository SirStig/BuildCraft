/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import net.minecraftforge.common.capabilities.Capability;

import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.BCTransportRegistries;

/** Port of 1.12.2's {@code buildcraft.transport.plug.PluggablePowerAdaptor} -- see the 26.x copy of this class
 * for the full account, including the RF-bridge scope cut. */
public class PluggablePowerAdaptor extends PipePluggable {

    private static final AABB[] BOXES = new AABB[6];

    static {
        double ll = 0 / 16.0;
        double lu = 4 / 16.0;
        double ul = 12 / 16.0;
        double uu = 16 / 16.0;

        double min = 3 / 16.0;
        double max = 13 / 16.0;

        BOXES[Direction.DOWN.get3DDataValue()] = new AABB(min, ll, min, max, lu, max);
        BOXES[Direction.UP.get3DDataValue()] = new AABB(min, ul, min, max, uu, max);
        BOXES[Direction.NORTH.get3DDataValue()] = new AABB(min, min, ll, max, max, lu);
        BOXES[Direction.SOUTH.get3DDataValue()] = new AABB(min, min, ul, max, max, uu);
        BOXES[Direction.WEST.get3DDataValue()] = new AABB(ll, min, min, lu, max, max);
        BOXES[Direction.EAST.get3DDataValue()] = new AABB(ul, min, min, uu, max, max);
    }

    private long storedMJ = 0;

    public PluggablePowerAdaptor(PluggableDefinition definition, IPipeHolder holder, Direction side) {
        super(definition, holder, side);
    }

    public PluggablePowerAdaptor(
        PluggableDefinition definition, IPipeHolder holder, Direction side, CompoundTag nbt
    ) {
        super(definition, holder, side);
        storedMJ = nbt.getLong("storedMJ");
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.putLong("storedMJ", storedMJ);
        return nbt;
    }

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
        return new ItemStack(BCTransportRegistries.PLUG_POWER_ADAPTOR.get());
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> cap) {
        if ((cap == MjCapabilities.CONNECTOR || cap == MjCapabilities.RECEIVER
            || cap == MjCapabilities.REDSTONE_RECEIVER) && holder.getPipe() != null) {
            return holder.getPipe().getBehaviour().getCapability(cap, side);
        }
        return null;
    }
}
