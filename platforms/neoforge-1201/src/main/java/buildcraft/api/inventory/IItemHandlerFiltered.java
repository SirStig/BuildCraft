/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.inventory;

import net.minecraft.world.item.ItemStack;

import net.minecraftforge.items.IItemHandler;

/**
 * A type of {@link IItemHandler} that has a single valid stack per slot, as specified by
 * {@link #getFilter(int)}.
 *
 * <p>The 26.x target calls this {@code IFilteredItemHandler} and extends {@code ResourceHandler<ItemResource>}:
 * {@code IItemHandler} was removed there along with the rest of the capability-based transfer API.
 *
 * <p>Any {@link IItemHandler} can implement this, even if the filter behaviour is more complex or there is no
 * real filter at all.
 */
public interface IItemHandlerFiltered extends IItemHandler {

    /**
     * @param slot The slot to test.
     * @return The filter in that slot. Will be {@link ItemStack#EMPTY} if this is not filtered to a single item
     *         -- for example if it matches a few stacks, or nothing is allowed, or a wide range is. Will be
     *         equal to {@link #getStackInSlot(int)} if the slot currently contains an item.
     */
    default ItemStack getFilter(int slot) {
        return getStackInSlot(slot);
    }
}
