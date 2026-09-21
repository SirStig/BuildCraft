/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.recipes;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.fluids.FluidStack;

/** The heating, cooling and distillation recipes the refinery machines run. */
public interface IRefineryRecipeManager {

    IHeatableRecipe createHeatingRecipe(FluidStack in, FluidStack out, int heatFrom, int heatTo);

    default IHeatableRecipe addHeatableRecipe(FluidStack in, FluidStack out, int heatFrom, int heatTo) {
        return getHeatableRegistry().addRecipe(createHeatingRecipe(in, out, heatFrom, heatTo));
    }

    ICoolableRecipe createCoolableRecipe(FluidStack in, FluidStack out, int heatFrom, int heatTo);

    default ICoolableRecipe addCoolableRecipe(FluidStack in, FluidStack out, int heatFrom, int heatTo) {
        return getCoolableRegistry().addRecipe(createCoolableRecipe(in, out, heatFrom, heatTo));
    }

    IDistillationRecipe createDistillationRecipe(
        FluidStack in,
        FluidStack outGas,
        FluidStack outLiquid,
        long powerRequired
    );

    default IDistillationRecipe addDistillationRecipe(
        FluidStack in,
        FluidStack outGas,
        FluidStack outLiquid,
        long powerRequired
    ) {
        return getDistillationRegistry().addRecipe(createDistillationRecipe(in, outGas, outLiquid, powerRequired));
    }

    IRefineryRegistry<IHeatableRecipe> getHeatableRegistry();

    IRefineryRegistry<ICoolableRecipe> getCoolableRegistry();

    IRefineryRegistry<IDistillationRecipe> getDistillationRegistry();

    interface IRefineryRegistry<R extends IRefineryRecipe> {
        /**
         * @return An unmodifiable collection containing all of the recipes that satisfy the given predicate. All
         *         of the recipe objects are guaranteed to never be null.
         */
        Stream<R> getRecipes(Predicate<R> toReturn);

        /** @return An unmodifiable set containing all of the recipes. */
        Collection<R> getAllRecipes();

        @Nullable
        R getRecipeForInput(@Nullable FluidStack fluid);

        Collection<R> removeRecipes(Predicate<R> toRemove);

        /**
         * Adds the given recipe to the registry. Note that this will remove any existing recipes for the passed
         * recipe's {@link IRefineryRecipe#in()}.
         *
         * @return The input recipe.
         */
        R addRecipe(R recipe);
    }

    interface IRefineryRecipe {
        FluidStack in();
    }

    interface IHeatExchangerRecipe extends IRefineryRecipe {
        @Nullable
        FluidStack out();

        int heatFrom();

        int heatTo();
    }

    interface IHeatableRecipe extends IHeatExchangerRecipe {
    }

    interface ICoolableRecipe extends IHeatExchangerRecipe {
    }

    interface IDistillationRecipe extends IRefineryRecipe {
        long powerRequired();

        FluidStack outGas();

        FluidStack outLiquid();
    }
}
