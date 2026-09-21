/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.items;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;

/** The item a broken tank drops its contents as. */
public interface IItemFluidShard {
    void addFluidDrops(NonNullList<ItemStack> toDrop, @Nullable FluidStack fluid);
}
