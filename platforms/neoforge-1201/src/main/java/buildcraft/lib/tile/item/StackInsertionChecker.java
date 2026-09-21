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

/** Tests whether a slot will accept a stack. Unchanged from 1.12.2 -- this target keeps {@code ItemStack}-based
 * item handling. */
@FunctionalInterface
public interface StackInsertionChecker {
    boolean canSet(int slot, @NotNull ItemStack stack);
}
