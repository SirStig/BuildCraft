/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.recipes;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** One recipe the integration table can carry out. */
public abstract class IntegrationRecipe {

    public final Identifier name;

    public IntegrationRecipe(Identifier name) {
        this.name = name;
    }

    /**
     * Determines the output of this recipe.
     *
     * @param target The stack in the middle, to integrate the components into.
     * @param toIntegrate All available stacks to integrate; not all have to be used up.
     * @return The output to produce, or an empty stack if the recipe is not valid for these inputs.
     */
    public abstract ItemStack getOutput(@NotNull ItemStack target, NonNullList<ItemStack> toIntegrate);

    /** @return The power cost in micro MJ. */
    public abstract long getRequiredMicroJoules(ItemStack output);

    public abstract IngredientStack getCenterStack();

    /**
     * @param output Only ever a stack obtained from {@link #getOutput}.
     * @return Everything consumed to produce that output -- what {@code extract} should take from the
     *         integration table's inventory. Was {@code ImmutableList<IngredientStack>} in 1.12.2;
     *         {@code IngredientStack} is a record now, so a plain immutable {@link List} says the same
     *         thing.
     */
    public abstract List<IngredientStack> getRequirements(@NotNull ItemStack output);

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return name.equals(((IntegrationRecipe) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
