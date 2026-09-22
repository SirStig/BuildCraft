/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.slot;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;

import net.minecraftforge.items.SlotItemHandler;

import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * A GUI slot bound to one index of an {@link ItemHandlerSimple}.
 *
 * <p>1.20.1 still has classic {@code IItemHandler}/{@code SlotItemHandler} (unlike 26.x -- see that platform's
 * copy of this class for the full account of why it needs a different base entirely), and this target's own
 * {@link ItemHandlerSimple} still implements {@code IItemHandlerModifiable} directly, so this keeps 1.12.2's
 * shape almost unchanged: extend {@code SlotItemHandler} and wrap the handler directly.
 */
public class SlotBase extends SlotItemHandler {
    public final int handlerIndex;
    public final ItemHandlerSimple itemHandler;

    public SlotBase(ItemHandlerSimple itemHandler, int slotIndex, int posX, int posY) {
        super(itemHandler, slotIndex, posX, posY);
        this.handlerIndex = slotIndex;
        this.itemHandler = itemHandler;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return itemHandler.isItemValid(handlerIndex, stack);
    }
}
