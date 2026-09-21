/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.boards;

import java.util.List;
import java.util.Random;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public abstract class RedstoneBoardNBT<T> {

    private static Random rand = new Random();

    public abstract String getID();

    public abstract void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced);

    public abstract String getDisplayName();

    public abstract IRedstoneBoard<T> create(CompoundTag nbt, T object);

    public abstract String getItemModelLocation();

    public void createBoard(CompoundTag nbt) {
        nbt.putString("id", getID());
    }

    public int getParameterNumber(CompoundTag nbt) {
        if (!nbt.contains("parameters")) {
            return 0;
        } else {
            return nbt.getList("parameters").orElseGet(ListTag::new).size();
        }
    }

    public float nextFloat(int difficulty) {
        return 1F - (float) Math.pow(rand.nextFloat(), 1F / difficulty);
    }
}
