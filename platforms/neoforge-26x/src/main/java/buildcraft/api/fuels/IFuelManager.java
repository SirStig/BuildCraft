/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.fuels;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** Every fuel registered for combustion engines. */
public interface IFuelManager {
    <F extends IFuel> F addFuel(F fuel);

    IFuel addFuel(FluidResource fluid, long powerPerCycle, int totalBurningTime);

    default IFuel addFuel(Fluid fluid, long powerPerCycle, int totalBurningTime) {
        return addFuel(FluidResource.of(fluid), powerPerCycle, totalBurningTime);
    }

    /** @param residue The residue produced per bucket of the original fuel. */
    IDirtyFuel addDirtyFuel(FluidResource fuel, long powerPerCycle, int totalBurningTime, FluidStack residue);

    /** @param residue The residue produced per bucket of the original fuel. */
    default IDirtyFuel addDirtyFuel(Fluid fuel, long powerPerCycle, int totalBurningTime, FluidStack residue) {
        return addDirtyFuel(FluidResource.of(fuel), powerPerCycle, totalBurningTime, residue);
    }

    Collection<IFuel> getFuels();

    @Nullable
    IFuel getFuel(FluidResource fluid);

    /** A fuel that leaves something behind when burnt. */
    interface IDirtyFuel extends IFuel {
        /**
         * @return The residue produced per bucket of original fuel. This stays a {@link FluidStack} rather than a
         *         {@link FluidResource} because here the amount is the point.
         */
        FluidStack getResidue();
    }
}
