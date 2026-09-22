/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.slot;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.tile.item.ItemHandlerSimple;

/** A slot that never actually accepts or gives up items through the normal pickup/place flow -- its content is
 * only ever changed by {@link buildcraft.lib.gui.ContainerBCTile#clicked} setting it to match the cursor's item
 * type. Used for blueprint patterns and material filters. */
public class SlotPhantom extends SlotBase implements IPhantomSlot {
    private final boolean canAdjustCount;

    public SlotPhantom(ItemHandlerSimple itemHandler, int slotIndex, int posX, int posY, boolean adjustableCount) {
        super(itemHandler, slotIndex, posX, posY);
        this.canAdjustCount = adjustableCount;
    }

    public SlotPhantom(ItemHandlerSimple itemHandler, int slotIndex, int posX, int posY) {
        this(itemHandler, slotIndex, posX, posY, true);
    }

    @Override
    public boolean canAdjustCount() {
        return canAdjustCount;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
