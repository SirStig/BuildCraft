/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import net.minecraft.world.item.ItemStack;

/**
 * Notified after a slot's contents changed.
 *
 * <p>1.12.2 passed the owning {@code IItemHandlerModifiable}, which is gone; {@link ItemHandlerSimple} passes
 * itself instead, which is the only implementor this ever runs against on this target.
 */
@FunctionalInterface
public interface StackChangeCallback {
    void onStackChange(ItemHandlerSimple itemHandler, int slot, ItemStack before, ItemStack after);
}
