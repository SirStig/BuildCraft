/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.ICoolantManager;
import buildcraft.api.fuels.IFuelManager;
import buildcraft.api.mj.MjAPI;

import buildcraft.energy.BCEnergyFluids.BCFluid;

/**
 * Fills the fuel and coolant registries -- the fuel/coolant half of 1.12.2's {@code BCEnergyRecipes#init}, every
 * value unchanged. Run from {@code FMLCommonSetupEvent} (1.12.2's {@code FMLInitializationEvent}, where the
 * original ran), after every registry -- fluids included -- has been populated.
 *
 * <p><b>Not ported here: the other half of 1.12.2's {@code init}</b>, the distillation and heat-exchange recipes
 * ({@code addDistillation}/{@code addHeatExchange}, plus the water/lava heatable/coolable entries), which feed
 * {@code BuildcraftRecipeRegistry.refineryRecipes} -- used only by the Distiller and the Heat Exchanger, neither of
 * which is part of this batch.
 */
public final class BCEnergyRecipes {

    private BCEnergyRecipes() {}

    private static final int TIME_BASE = 240_000; // 240_000 - multiple of 3, 5, 16, 1000

    public static void init() {
        ICoolantManager coolant = Objects.requireNonNull(BuildcraftFuelRegistry.coolant);
        coolant.addCoolant(Fluids.WATER, 0.0023f);
        coolant.addSolidCoolant(new ItemStack(Blocks.ICE), new FluidStack(Fluids.WATER, 1000), 1.5f);
        coolant.addSolidCoolant(new ItemStack(Blocks.PACKED_ICE), new FluidStack(Fluids.WATER, 1000), 2f);

        // Relative amounts of the fluid -- the amount of oil used in refining will return X amount of fluid

        // single
        final int _oil = 8;
        final int _gas = 16;
        final int _light = 4;
        final int _dense = 2;

        // double
        final int _gas_light = 10;
        final int _light_dense = 5;
        final int _dense_residue = 2;

        // triple
        final int _light_dense_residue = 3;
        final int _gas_light_dense = 8;

        addFuel(BCEnergyFluids.fuelGaseous, _gas, 8, 4);
        addFuel(BCEnergyFluids.fuelLight, _light, 6, 6);
        addFuel(BCEnergyFluids.fuelDense, _dense, 4, 12);

        addFuel(BCEnergyFluids.fuelMixedLight, _gas_light, 3, 5);
        addFuel(BCEnergyFluids.fuelMixedHeavy, _light_dense, 5, 8);
        addDirtyFuel(BCEnergyFluids.oilDense, _dense_residue, 4, 4);

        addFuel(BCEnergyFluids.oilDistilled, _gas_light_dense, 1, 5);
        addDirtyFuel(BCEnergyFluids.oilHeavy, _light_dense_residue, 2, 4);

        addDirtyFuel(BCEnergyFluids.crudeOil, _oil, 3, 4);
    }

    /** Only the cool (heat 0) variant is ever a fuel, exactly as in 1.12.2 -- hot/searing fluids are distiller
     * inputs, not engine fuel. */
    private static Fluid getFirstOrNull(BCFluid[] array) {
        if (array == null || array.length == 0) {
            return null;
        }
        return array[0].getSource().get();
    }

    private static void addFuel(BCFluid[] in, int amountDiff, int multiplier, int boostOver4) {
        Fluid fuel = getFirstOrNull(in);
        if (fuel == null) {// It may have been disabled
            return;
        }
        long powerPerCycle = multiplier * MjAPI.MJ;
        int totalTime = TIME_BASE * boostOver4 / 4 / multiplier / amountDiff;
        fuels().addFuel(fuel, powerPerCycle, totalTime);
    }

    private static void addDirtyFuel(BCFluid[] in, int amountDiff, int multiplier, int boostOver4) {
        Fluid fuel = getFirstOrNull(in);
        if (fuel == null) {// It may have been disabled
            return;
        }
        long powerPerCycle = multiplier * MjAPI.MJ;
        int totalTime = TIME_BASE * boostOver4 / 4 / multiplier / amountDiff;
        Fluid residue = getFirstOrNull(BCEnergyFluids.oilResidue);
        if (residue == null) {// residue might have been disabled
            fuels().addFuel(fuel, powerPerCycle, totalTime);
        } else {
            fuels().addDirtyFuel(fuel, powerPerCycle, totalTime, new FluidStack(residue, 1000 / amountDiff));
        }
    }

    private static IFuelManager fuels() {
        return Objects.requireNonNull(BuildcraftFuelRegistry.fuel);
    }
}
