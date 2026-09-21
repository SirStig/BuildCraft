/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory.filter;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;

/** This interface is used with several of the functions in IItemTransfer to provide a convenient means of dealing with
 * entire classes of items without having to specify each item individually. */
public enum StackFilter implements IStackFilter {

    ALL {
        @Override
        public boolean matches(@NotNull ItemStack stack) {
            return true;
        }
    },
    /**
     * {@code TileEntityFurnace.getItemBurnTime(stack) > 0} is gone: furnace burn time is now the
     * {@link net.minecraft.world.item.component.CookingFuel CookingFuel} data component, and computing the
     * actual number of ticks needs a loot context to resolve its {@code ResolvableInt}, which this filter has
     * no access to and does not need -- it only ever asked "can this burn", not "for how long". Whether the
     * component is present at all already answers that: only items registered as furnace fuel carry it.
     */
    FUEL {
        @Override
        public boolean matches(@NotNull ItemStack stack) {
            return stack.has(DataComponents.COOKING_FUEL);
        }
    };

    @Override
    public abstract boolean matches(@NotNull ItemStack stack);
}
