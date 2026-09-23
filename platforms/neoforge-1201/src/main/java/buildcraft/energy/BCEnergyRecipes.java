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
import buildcraft.api.recipes.BuildcraftRecipeRegistry;
import buildcraft.api.recipes.IRefineryRecipeManager;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;

import buildcraft.lib.misc.MathUtil;
import buildcraft.lib.recipe.RefineryRecipeRegistry;

import buildcraft.energy.BCEnergyFluids.BCFluid;

/**
 * Fills the fuel and coolant registries -- the fuel/coolant half of 1.12.2's {@code BCEnergyRecipes#init}, every
 * value unchanged. Run from {@code FMLCommonSetupEvent} (1.12.2's {@code FMLInitializationEvent}, where the
 * original ran), after every registry -- fluids included -- has been populated.
 *
 * <p><b>The refinery half</b> ({@link #initRefinery}) is 1.12.2's own {@code BCModules.FACTORY.isLoaded()} block,
 * table and arithmetic unchanged: the ten {@code addDistillation} rows (inputs/outputs picked at the same heat
 * index, amounts divided by their highest common factor together with the MJ cost), {@code addHeatExchange} for
 * each of the ten fluid families (cool&lt;-&gt;hot at 10 mB, heat values from the fluid itself), water as a heatable
 * (0 -&gt; 1, consumed) and lava as a coolable (4 -&gt; 2, consumed). 1.12.2 installed
 * {@code RefineryRecipeRegistry.INSTANCE} as {@code BuildcraftRecipeRegistry.refineryRecipes} from
 * {@code BCLibRegistries}; this port has no lib-registries hook for it, so the refinery half installs it itself
 * right before filling it (the Distiller and Heat Exchanger are the only readers, and both null-check it).
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

        initRefinery();
    }

    /** 1.12.2's {@code if (BCModules.FACTORY.isLoaded()) { ... }} block of {@code init} -- see the class javadoc. */
    private static void initRefinery() {
        if (BuildcraftRecipeRegistry.refineryRecipes == null) {
            BuildcraftRecipeRegistry.refineryRecipes = RefineryRecipeRegistry.INSTANCE;
        }
        final int _oil = 8;
        final int _gas = 16;
        final int _light = 4;
        final int _dense = 2;
        final int _residue = 1;
        final int _gas_light = 10;
        final int _light_dense = 5;
        final int _dense_residue = 2;
        final int _light_dense_residue = 3;
        final int _gas_light_dense = 8;

        FluidStack[] gas_light_dense_residue = createFluidStack(BCEnergyFluids.crudeOil, _oil);
        FluidStack[] gas_light_dense = createFluidStack(BCEnergyFluids.oilDistilled, _gas_light_dense);
        FluidStack[] gas_light = createFluidStack(BCEnergyFluids.fuelMixedLight, _gas_light);
        FluidStack[] gas = createFluidStack(BCEnergyFluids.fuelGaseous, _gas);
        FluidStack[] light_dense_residue = createFluidStack(BCEnergyFluids.oilHeavy, _light_dense_residue);
        FluidStack[] light_dense = createFluidStack(BCEnergyFluids.fuelMixedHeavy, _light_dense);
        FluidStack[] light = createFluidStack(BCEnergyFluids.fuelLight, _light);
        FluidStack[] dense_residue = createFluidStack(BCEnergyFluids.oilDense, _dense_residue);
        FluidStack[] dense = createFluidStack(BCEnergyFluids.fuelDense, _dense);
        FluidStack[] residue = createFluidStack(BCEnergyFluids.oilResidue, _residue);

        addDistillation(gas_light_dense_residue, gas, light_dense_residue, 0, 32 * MjAPI.MJ);
        addDistillation(gas_light_dense_residue, gas_light, dense_residue, 1, 16 * MjAPI.MJ);
        addDistillation(gas_light_dense_residue, gas_light_dense, residue, 2, 12 * MjAPI.MJ);

        addDistillation(gas_light_dense, gas, light_dense, 0, 24 * MjAPI.MJ);
        addDistillation(gas_light_dense, gas_light, dense, 1, 16 * MjAPI.MJ);

        addDistillation(gas_light, gas, light, 0, 24 * MjAPI.MJ);

        addDistillation(light_dense_residue, light, dense_residue, 1, 16 * MjAPI.MJ);
        addDistillation(light_dense_residue, light_dense, residue, 2, 12 * MjAPI.MJ);

        addDistillation(light_dense, light, dense, 1, 16 * MjAPI.MJ);

        addDistillation(dense_residue, dense, residue, 2, 12 * MjAPI.MJ);

        addHeatExchange(BCEnergyFluids.crudeOil);
        addHeatExchange(BCEnergyFluids.oilDistilled);
        addHeatExchange(BCEnergyFluids.oilHeavy);
        addHeatExchange(BCEnergyFluids.fuelMixedLight);
        addHeatExchange(BCEnergyFluids.fuelMixedHeavy);
        addHeatExchange(BCEnergyFluids.oilDense);
        addHeatExchange(BCEnergyFluids.fuelGaseous);
        addHeatExchange(BCEnergyFluids.fuelLight);
        addHeatExchange(BCEnergyFluids.fuelDense);
        addHeatExchange(BCEnergyFluids.oilResidue);

        FluidStack water = new FluidStack(Fluids.WATER, 10);
        refinery().addHeatableRecipe(water, null, 0, 1);

        FluidStack lava = new FluidStack(Fluids.LAVA, 5);
        refinery().addCoolableRecipe(lava, null, 4, 2);
    }

    private static FluidStack[] createFluidStack(BCFluid[] fluid, int amount) {
        FluidStack[] arr = new FluidStack[fluid.length];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = new FluidStack(fluid[i].getSource().get(), amount);
        }
        return arr;
    }

    private static void addDistillation(FluidStack[] in, FluidStack[] outGas, FluidStack[] outLiquid, int heat,
        long mjCost) {
        FluidStack _in = in[heat];
        FluidStack _outGas = outGas[heat];
        FluidStack _outLiquid = outLiquid[heat];
        IDistillationRecipe existing = refinery().getDistillationRegistry().getRecipeForInput(_in);
        if (existing != null) {
            throw new IllegalStateException("Already added distillation recipe for " + _in.getFluid());
        }
        int hcf = MathUtil.findHighestCommonFactor(_in.getAmount(), _outGas.getAmount());
        hcf = MathUtil.findHighestCommonFactor(hcf, _outLiquid.getAmount());
        if (hcf > 1) {
            _in = new FluidStack(_in, _in.getAmount() / hcf);
            _outGas = new FluidStack(_outGas, _outGas.getAmount() / hcf);
            _outLiquid = new FluidStack(_outLiquid, _outLiquid.getAmount() / hcf);
            mjCost /= hcf;
        }
        refinery().addDistillationRecipe(_in, _outGas, _outLiquid, mjCost);
    }

    private static void addHeatExchange(BCFluid[] fluid) {
        for (int i = 0; i < fluid.length - 1; i++) {
            BCFluid cool = fluid[i];
            BCFluid hot = fluid[i + 1];
            FluidStack cool_f = new FluidStack(cool.getSource().get(), 10);
            FluidStack hot_f = new FluidStack(hot.getSource().get(), 10);
            int ch = cool.getHeatValue();
            int hh = hot.getHeatValue();
            refinery().addHeatableRecipe(cool_f, hot_f, ch, hh);
            refinery().addCoolableRecipe(hot_f, cool_f, hh, ch);
        }
    }

    private static IRefineryRecipeManager refinery() {
        return Objects.requireNonNull(BuildcraftRecipeRegistry.refineryRecipes);
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
