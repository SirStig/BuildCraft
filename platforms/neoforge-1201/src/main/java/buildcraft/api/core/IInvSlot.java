/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import net.minecraft.world.item.ItemStack;

/** A single slot of some inventory, addressed independently of the inventory itself. */
public interface IInvSlot {
    /** @return The slot number within the underlying inventory. */
    int getIndex();

    boolean canPutStackInSlot(ItemStack stack);

    boolean canTakeStackFromSlot(ItemStack stack);

    boolean isItemValidForSlot(ItemStack stack);

    ItemStack decreaseStackInSlot(int amount);

    ItemStack getStackInSlot();

    void setStackInSlot(ItemStack stack);
}
