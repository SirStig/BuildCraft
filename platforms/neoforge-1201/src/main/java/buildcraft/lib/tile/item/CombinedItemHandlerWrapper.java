/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile.item;

import net.minecraft.world.item.ItemStack;

import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import buildcraft.api.inventory.IItemHandlerFiltered;

/** Combines several {@link IItemHandlerModifiable} into one class. Extends forge's {@link CombinedInvWrapper} in
 * order to do this.
 *
 * <p>Also provides {@link IItemHandlerFiltered#getFilter(int)} if the wrapped handlers support it. Unchanged
 * from 1.12.2 -- both {@code CombinedInvWrapper} and {@code IItemHandlerModifiable} are still present on this
 * target. */
public class CombinedItemHandlerWrapper extends CombinedInvWrapper implements IItemHandlerFiltered {

    public CombinedItemHandlerWrapper(IItemHandlerModifiable... itemHandler) {
        super(itemHandler);
    }

    @Override
    public ItemStack getFilter(int slot) {
        int index = getIndexForSlot(slot);
        IItemHandlerModifiable handler = getHandlerFromIndex(index);
        slot = getSlotFromIndex(slot, index);
        if (handler instanceof IItemHandlerFiltered filtered) {
            return filtered.getFilter(slot);
        }
        return handler.getStackInSlot(slot);
    }
}
