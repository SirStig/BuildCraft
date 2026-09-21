/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.fuels;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

/** Every coolant registered for combustion engines, fluid and solid. */
public interface ICoolantManager {
    ICoolant addCoolant(ICoolant coolant);

    ICoolant addCoolant(FluidStack fluid, float degreesCoolingPerMb);

    default ICoolant addCoolant(Fluid fluid, float degreesCoolingPerMb) {
        return addCoolant(new FluidStack(fluid, FluidType.BUCKET_VOLUME), degreesCoolingPerMb);
    }

    ISolidCoolant addSolidCoolant(ISolidCoolant solidCoolant);

    ISolidCoolant addSolidCoolant(ItemStack solid, FluidStack fluid, float multiplier);

    Collection<ICoolant> getCoolants();

    Collection<ISolidCoolant> getSolidCoolants();

    @Nullable
    ICoolant getCoolant(FluidStack fluid);

    float getDegreesPerMb(FluidStack fluid, float heat);

    @Nullable
    ISolidCoolant getSolidCoolant(ItemStack solid);
}
