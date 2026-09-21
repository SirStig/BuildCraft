/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.items;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;

/** The list item, which matches other items against the rows the player configured on it. */
public interface IList extends INamedItem {
    boolean matches(@NotNull ItemStack stackList, @NotNull ItemStack item);
}
