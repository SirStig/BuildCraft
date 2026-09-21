/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory.filter;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

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
    /** {@code TileEntityFurnace} was renamed {@link AbstractFurnaceBlockEntity}, whose {@code isFuel(ItemStack)}
     * is the direct successor of {@code getItemBurnTime(stack) > 0}. */
    FUEL {
        @Override
        public boolean matches(@NotNull ItemStack stack) {
            return AbstractFurnaceBlockEntity.isFuel(stack);
        }
    };

    @Override
    public abstract boolean matches(@NotNull ItemStack stack);
}
