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

import buildcraft.lib.tile.item.ItemHandlerSimple;

/** A slot that only ever gives items up -- a player can take from it, but never place anything into it. */
public class SlotOutput extends SlotBase {

    public SlotOutput(ItemHandlerSimple handler, int slotIndex, int posX, int posY) {
        super(handler, slotIndex, posX, posY);
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack itemstack) {
        return false;
    }
}
