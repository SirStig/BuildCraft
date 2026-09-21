/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.crops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Teaches BuildCraft's farming machines how to plant and harvest one family of crops. */
public interface ICropHandler {

    /** @return True if the item can be planted. */
    boolean isSeed(ItemStack stack);

    /**
     * Check if the item can be planted. You can assume this is only called if {@link #isSeed} returned true.
     *
     * @return True if the item can be planted at pos.
     */
    boolean canSustainPlant(Level level, ItemStack seed, BlockPos pos);

    /**
     * Plant the item in the block. You can assume this is only called if {@link #canSustainPlant} returned true.
     *
     * @return True if the item was planted at pos.
     */
    boolean plantCrop(Level level, Player player, ItemStack seed, BlockPos pos);

    /** @return True if the block at pos is mature and can be harvested. */
    boolean isMature(BlockGetter blockAccess, BlockState state, BlockPos pos);

    /**
     * Harvest the crop. You can assume this is only called if {@link #isMature} returned true.
     *
     * @param drops A list to return the harvest's drops in.
     * @return True if the block was successfully harvested.
     */
    boolean harvestCrop(Level level, BlockPos pos, NonNullList<ItemStack> drops);
}
