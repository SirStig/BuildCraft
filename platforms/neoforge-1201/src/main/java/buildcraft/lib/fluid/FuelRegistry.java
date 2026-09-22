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

import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.IFuelManager;

/**
 * The combustion-engine fuel list, installed as {@code BuildcraftFuelRegistry.fuel}. A direct port of 1.12.2's
 * enum singleton. Unlike the 26.x copy (keyed by {@code FluidResource}), 1.20.1 has no amount-less fluid type, so
 * a fuel stays keyed by a {@link FluidStack} whose amount is ignored, and matching is still Forge's own
 * {@link FluidStack#isFluidEqual} (fluid + NBT, amount ignored), exactly as in 1.12.2.
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
    public IFuel addFuel(FluidStack fluid, long powerPerCycle, int totalBurningTime) {
        return addFuel(new Fuel(fluid, powerPerCycle, totalBurningTime));
    }

    @Override
    public IDirtyFuel addDirtyFuel(FluidStack fuel, long powerPerCycle, int totalBurningTime, FluidStack residue) {
        return addFuel(new DirtyFuel(fuel, powerPerCycle, totalBurningTime, residue));
    }

    @Override
    public Collection<IFuel> getFuels() {
        return fuels;
    }

    @Override
    @Nullable
    public IFuel getFuel(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return null;
        }
        for (IFuel fuel : fuels) {
            if (fuel.getFluid().isFluidEqual(fluid)) {
                return fuel;
            }
        }
        return null;
    }

    public static class Fuel implements IFuel {
        private final FluidStack fluid;
        private final long powerPerCycle;
        private final int totalBurningTime;

        public Fuel(FluidStack fluid, long powerPerCycle, int totalBurningTime) {
            this.fluid = fluid;
            this.powerPerCycle = powerPerCycle;
            this.totalBurningTime = totalBurningTime;
        }

        @Override
        public FluidStack getFluid() {
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

        public DirtyFuel(FluidStack fluid, long powerPerCycle, int totalBurningTime, FluidStack residue) {
            super(fluid, powerPerCycle, totalBurningTime);
            this.residue = residue;
        }

        @Override
        public FluidStack getResidue() {
            return residue;
        }
    }
}
