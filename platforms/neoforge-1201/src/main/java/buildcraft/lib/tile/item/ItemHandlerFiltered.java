/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile.item;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;

import net.minecraftforge.items.IItemHandlerModifiable;

import buildcraft.api.inventory.IItemHandlerFiltered;

/** An {@link ItemHandlerSimple} whose {@link IItemHandlerFiltered#getFilter} is read from another handler --
 * typically a display-only inventory the player fills in a GUI to say "this slot accepts items like these".
 * Sized to match the filter handler's slot count. */
public class ItemHandlerFiltered extends ItemHandlerSimple implements IItemHandlerFiltered {
    private final IItemHandlerModifiable filter;
    private final boolean emptyIsAnything;

    public ItemHandlerFiltered(IItemHandlerModifiable filter, boolean emptyIsAnything) {
        super(filter.getSlots());
        this.emptyIsAnything = emptyIsAnything;
        this.filter = filter;
        setChecker((slot, stack) -> {
            ItemStack inSlot = filter.getStackInSlot(slot);
            if (inSlot.isEmpty()) {
                return emptyIsAnything;
            } else {
                return buildcraft.lib.misc.StackUtil.canMerge(inSlot, stack);
            }
        });
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        if (emptyIsAnything || !getFilter(slot).isEmpty()) {
            return super.isItemValid(slot, stack);
        }
        return false;
    }

    @Override
    public ItemStack getFilter(int slot) {
        ItemStack current = getStackInSlot(slot);
        if (!current.isEmpty()) {
            return current;
        }
        return filter.getStackInSlot(slot);
    }
}
