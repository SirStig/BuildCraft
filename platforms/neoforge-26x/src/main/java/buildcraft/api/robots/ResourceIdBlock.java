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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;

import buildcraft.api.core.EnumPipePart;

public class ResourceIdBlock extends ResourceId {

    public BlockPos pos = new BlockPos(0, 0, 0);
    public EnumPipePart side = EnumPipePart.CENTER;

    public ResourceIdBlock() {

    }

    public ResourceIdBlock(int x, int y, int z) {
        pos = new BlockPos(x, y, z);
    }

    public ResourceIdBlock(BlockPos iIndex) {
        pos = iIndex;
    }

    public ResourceIdBlock(BlockEntity tile) {
        pos = tile.getBlockPos();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }

        ResourceIdBlock compareId = (ResourceIdBlock) obj;

        return pos.equals(compareId.pos) && side == compareId.side;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(pos.hashCode()).append(side != null ? side.ordinal() : 0).build();
    }

    @Override
    public void writeToNBT(CompoundTag nbt) {
        super.writeToNBT(nbt);

        int[] arr = new int[] { pos.getX(), pos.getY(), pos.getZ() };
        nbt.putIntArray("pos", arr);

        nbt.putString("side", side.getSerializedName());
    }

    @Override
    protected void readFromNBT(CompoundTag nbt) {
        super.readFromNBT(nbt);
        int[] arr = nbt.getIntArray("pos").orElse(new int[0]);
        pos = new BlockPos(arr[0], arr[1], arr[2]);

        side = EnumPipePart.fromName(nbt.getStringOr("side", ""));
    }
}
