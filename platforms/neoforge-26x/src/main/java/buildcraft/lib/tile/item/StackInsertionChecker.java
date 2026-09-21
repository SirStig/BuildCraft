/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Tests whether a slot will accept a resource.
 *
 * <p>Took an {@link net.minecraft.world.item.ItemStack} in 1.12.2. {@link ItemHandlerSimple} is now built on
 * NeoForge's own {@code StacksResourceHandler}, whose equivalent extension point ({@code isValid}) is keyed by
 * {@link ItemResource} rather than a stack, so this follows suit.
 */
@FunctionalInterface
public interface StackInsertionChecker {
    boolean canSet(int slot, ItemResource resource);
}
