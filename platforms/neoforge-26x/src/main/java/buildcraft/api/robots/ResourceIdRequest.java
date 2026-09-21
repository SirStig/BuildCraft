/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.robots;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.EnumPipePart;

public class ResourceIdRequest extends ResourceIdBlock {

    private int slot;

    public ResourceIdRequest() {

    }

    public ResourceIdRequest(DockingStation station, int slot) {
        pos = station.index();
        side = EnumPipePart.fromFacing(station.side());
        this.slot = slot;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!super.equals(obj)) return false;
        ResourceIdRequest compareId = (ResourceIdRequest) obj;

        return slot == compareId.slot;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(super.hashCode()).append(slot).build();
    }

    @Override
    public void writeToNBT(CompoundTag nbt) {
        super.writeToNBT(nbt);

        nbt.putInt("localId", slot);
    }

    @Override
    protected void readFromNBT(CompoundTag nbt) {
        super.readFromNBT(nbt);

        slot = nbt.getIntOr("localId", 0);
    }
}
