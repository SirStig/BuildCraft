/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.fuels;

import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;

/** An item that melts into a coolant fluid, such as ice. */
public interface ISolidCoolant {
    FluidStack getFluidFromSolidCoolant(ItemStack stack);
}
