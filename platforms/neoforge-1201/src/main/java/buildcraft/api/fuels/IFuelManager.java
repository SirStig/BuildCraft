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

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

import net.minecraft.world.level.material.Fluid;

/** Every fuel registered for combustion engines. */
public interface IFuelManager {
    <F extends IFuel> F addFuel(F fuel);

    IFuel addFuel(FluidStack fluid, long powerPerCycle, int totalBurningTime);

    default IFuel addFuel(Fluid fluid, long powerPerCycle, int totalBurningTime) {
        return addFuel(new FluidStack(fluid, FluidType.BUCKET_VOLUME), powerPerCycle, totalBurningTime);
    }

    /** @param residue The residue produced per bucket of the original fuel. */
    IDirtyFuel addDirtyFuel(FluidStack fuel, long powerPerCycle, int totalBurningTime, FluidStack residue);

    /** @param residue The residue produced per bucket of the original fuel. */
    default IDirtyFuel addDirtyFuel(Fluid fuel, long powerPerCycle, int totalBurningTime, FluidStack residue) {
        return addDirtyFuel(new FluidStack(fuel, FluidType.BUCKET_VOLUME), powerPerCycle, totalBurningTime, residue);
    }

    Collection<IFuel> getFuels();

    @Nullable
    IFuel getFuel(FluidStack fluid);

    /** A fuel that leaves something behind when burnt. */
    interface IDirtyFuel extends IFuel {
        /** @return The residue produced per bucket of original fuel. */
        FluidStack getResidue();
    }
}
