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

import net.neoforged.neoforge.capabilities.BlockCapability;

import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.BCTransportRegistries;

/**
 * Port of 1.12.2's {@code buildcraft.transport.plug.PluggablePowerAdaptor}: a blocking plug that keeps the
 * covered face's MJ connector/receiver capability reachable from outside, letting a machine sit flush against a
 * kinesis pipe even though the face itself is otherwise occupied.
 *
 * <p><b>Scope cut:</b> 1.12.2's automatic MJ-to-RF conversion bridge (the internal {@code IEnergyStorage}
 * wrapper built from {@code MjAPI.getRfConversion()}, buffering sub-RF remainders in {@code storedMJ}) is not
 * ported -- it depends on Forge Energy interop this batch does not touch, and is a compatibility nicety
 * orthogonal to the pluggable/wire/gate scope this batch is actually about. {@code storedMJ}'s NBT field is kept
 * (harmlessly always zero) so a save file written by a future port of that bridge round-trips cleanly.
 */
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
        storedMJ = nbt.getLongOr("storedMJ", 0L);
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
    public <T> T getCapability(BlockCapability<T, Direction> cap) {
        if ((cap == MjCapabilities.CONNECTOR || cap == MjCapabilities.RECEIVER
            || cap == MjCapabilities.REDSTONE_RECEIVER) && holder.getPipe() != null) {
            return holder.getPipe().getBehaviour().getCapability(cap, side);
        }
        return null;
    }
}
