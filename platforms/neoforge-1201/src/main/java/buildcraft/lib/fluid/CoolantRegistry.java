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

import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.fuels.ICoolant;
import buildcraft.api.fuels.ICoolantManager;
import buildcraft.api.fuels.ISolidCoolant;

/**
 * The combustion-engine coolant list, installed as {@code BuildcraftFuelRegistry.coolant}. A direct port of 1.12.2's
 * enum singleton. Liquid coolants stay keyed by a {@link FluidStack} on this target (see {@link FuelRegistry}), so
 * 1.12.2's "{@code fluid == null || fluid.amount == 0}" check becomes {@link FluidStack#isEmpty()} (which is
 * exactly an amount-or-fluid emptiness test) and matching stays {@link FluidStack#isFluidEqual}. 1.12.2's
 * {@code ItemStack#isItemEqual} (item and metadata, count ignored) becomes {@link ItemStack#isSameItem} --
 * metadata no longer exists, and the item alone is what the flattening folded it into.
 */
public enum CoolantRegistry implements ICoolantManager {
    INSTANCE;

    private final List<ICoolant> coolants = new LinkedList<>();
    private final List<ISolidCoolant> solidCoolants = new LinkedList<>();

    @Override
    public ICoolant addCoolant(ICoolant coolant) {
        coolants.add(coolant);
        return coolant;
    }

    @Override
    public ISolidCoolant addSolidCoolant(ISolidCoolant solidCoolant) {
        solidCoolants.add(solidCoolant);
        return solidCoolant;
    }

    @Override
    public ICoolant addCoolant(FluidStack fluid, float degreesCoolingPerMB) {
        return addCoolant(new Coolant(fluid, degreesCoolingPerMB));
    }

    @Override
    public ISolidCoolant addSolidCoolant(ItemStack solid, FluidStack fluid, float multiplier) {
        return addSolidCoolant(new SolidCoolant(solid, fluid, multiplier));
    }

    @Override
    public Collection<ICoolant> getCoolants() {
        return coolants;
    }

    @Override
    public Collection<ISolidCoolant> getSolidCoolants() {
        return solidCoolants;
    }

    @Override
    @Nullable
    public ICoolant getCoolant(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return null;
        }
        for (ICoolant coolant : coolants) {
            if (coolant.matchesFluid(fluid)) {
                return coolant;
            }
        }
        return null;
    }

    @Override
    public float getDegreesPerMb(FluidStack fluid, float heat) {
        if (fluid == null || fluid.isEmpty()) {
            return 0;
        }
        for (ICoolant coolant : coolants) {
            float degrees = coolant.getDegreesCoolingPerMB(fluid, heat);
            if (degrees > 0) {
                return degrees;
            }
        }
        return 0;
    }

    @Override
    @Nullable
    public ISolidCoolant getSolidCoolant(ItemStack solid) {
        for (ISolidCoolant coolant : solidCoolants) {
            if (coolant.getFluidFromSolidCoolant(solid) != null) {
                return coolant;
            }
        }
        return null;
    }

    public static class Coolant implements ICoolant {
        private final FluidStack fluid;
        private final float degreesCoolingPerMB;

        public Coolant(FluidStack fluid, float degreesCoolingPerMB) {
            this.fluid = fluid;
            this.degreesCoolingPerMB = degreesCoolingPerMB;
        }

        @Override
        public boolean matchesFluid(FluidStack stack) {
            return fluid.isFluidEqual(stack);
        }

        @Override
        public float getDegreesCoolingPerMB(FluidStack stack, float heat) {
            if (matchesFluid(stack)) {
                return degreesCoolingPerMB;
            }
            return 0;
        }
    }

    private static class SolidCoolant implements ISolidCoolant {
        private final ItemStack solid;
        private final FluidStack fluid;
        private final float multiplier;

        public SolidCoolant(ItemStack solid, FluidStack fluid, float multiplier) {
            this.solid = solid;
            this.fluid = fluid;
            this.multiplier = multiplier;
        }

        /** Returns {@code null} (not an empty stack) for a non-matching item, exactly like 1.12.2 --
         * {@link CoolantRegistry#getSolidCoolant} relies on that {@code null} to tell a match apart. */
        @Override
        @Nullable
        public FluidStack getFluidFromSolidCoolant(ItemStack stack) {
            if (stack == null || stack.isEmpty() || !ItemStack.isSameItem(stack, solid)) {
                return null;
            }
            int liquidAmount = (int) (stack.getCount() * fluid.getAmount() * multiplier / solid.getCount());
            return new FluidStack(fluid.getFluid(), liquidAmount);
        }
    }
}
