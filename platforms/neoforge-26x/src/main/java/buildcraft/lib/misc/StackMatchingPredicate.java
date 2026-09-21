/*
 * Copyright (c) 2020 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface StackMatchingPredicate {
    boolean isMatching(@NotNull ItemStack base, @NotNull ItemStack comparison);
}
