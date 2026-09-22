/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.IFuelManager;

/**
 * The combustion-engine fuel list, installed as {@code BuildcraftFuelRegistry.fuel}. A direct port of 1.12.2's
 * enum singleton; the only change is the API's own: a fuel is keyed by a {@link FluidResource} (a fluid with no
 * amount) rather than a {@code FluidStack} whose amount the old javadoc said to ignore, so 1.12.2's
 * {@code FluidStack#isFluidEqual} (fluid + NBT, amount ignored) becomes plain {@link FluidResource#equals}
 * (fluid + data components).
 */
public enum FuelRegistry implements IFuelManager {
    INSTANCE;

    private final List<IFuel> fuels = new LinkedList<>();

    @Override
    public <F extends IFuel> F addFuel(F fuel) {
        fuels.add(fuel);
        return fuel;
    }

    @Override
    public IFuel addFuel(FluidResource fluid, long powerPerCycle, int totalBurningTime) {
        return addFuel(new Fuel(fluid, powerPerCycle, totalBurningTime));
    }

    @Override
    public IDirtyFuel addDirtyFuel(FluidResource fuel, long powerPerCycle, int totalBurningTime, FluidStack residue) {
        return addFuel(new DirtyFuel(fuel, powerPerCycle, totalBurningTime, residue));
    }

    @Override
    public Collection<IFuel> getFuels() {
        return fuels;
    }

    @Override
    @Nullable
    public IFuel getFuel(FluidResource fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return null;
        }
        for (IFuel fuel : fuels) {
            if (fuel.getFluid().equals(fluid)) {
                return fuel;
            }
        }
        return null;
    }

    public static class Fuel implements IFuel {
        private final FluidResource fluid;
        private final long powerPerCycle;
        private final int totalBurningTime;

        public Fuel(FluidResource fluid, long powerPerCycle, int totalBurningTime) {
            this.fluid = fluid;
            this.powerPerCycle = powerPerCycle;
            this.totalBurningTime = totalBurningTime;
        }

        @Override
        public FluidResource getFluid() {
            return fluid;
        }

        @Override
        public long getPowerPerCycle() {
            return powerPerCycle;
        }

        @Override
        public int getTotalBurningTime() {
            return totalBurningTime;
        }
    }

    public static class DirtyFuel extends Fuel implements IDirtyFuel {

        private final FluidStack residue;

        public DirtyFuel(FluidResource fluid, long powerPerCycle, int totalBurningTime, FluidStack residue) {
            super(fluid, powerPerCycle, totalBurningTime);
            this.residue = residue;
        }

        @Override
        public FluidStack getResidue() {
            return residue;
        }
    }
}
