/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.items;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;

/** An item the player can rename in a BuildCraft GUI, such as a list or a map location. */
public interface INamedItem {
    String getName(@NotNull ItemStack stack);

    boolean setName(@NotNull ItemStack stack, String name);
}
