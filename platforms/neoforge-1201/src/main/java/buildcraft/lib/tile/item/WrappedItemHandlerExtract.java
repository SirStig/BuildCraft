/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile.item;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;

import net.minecraftforge.items.IItemHandlerModifiable;

/** An extract-only view of another handler: every insertion attempt is refused. */
public class WrappedItemHandlerExtract extends DelegateItemHandler {

    public WrappedItemHandlerExtract(IItemHandlerModifiable delegate) {
        super(delegate);
    }

    @Override
    @NotNull
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return stack;
    }
}
