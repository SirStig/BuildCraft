/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.recipes;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * One recipe the assembly table can carry out.
 *
 * <p>1.12.2 had this implement {@code IForgeRegistryEntry<AssemblyRecipe>} so assembly recipes could live in a
 * Forge registry. That interface was removed: a registry no longer requires its entries to share a base type,
 * and registry names are held by the registry rather than by the object. The name is therefore a plain final
 * field set in the constructor, which also makes the class immutable -- {@code setRegistryName} could previously
 * be called after the recipe was in use, which would corrupt its {@code hashCode}.
 *
 * @deprecated TEMPORARY CLASS DO NOT USE! Kept as-is from 1.12.2; assembly recipes should end up as datapack
 *             recipes with a {@code RecipeSerializer}.
 */
@Deprecated
public abstract class AssemblyRecipe implements Comparable<AssemblyRecipe> {

    private final Identifier name;

    protected AssemblyRecipe(Identifier name) {
        this.name = name;
    }

    public final Identifier getName() {
        return name;
    }

    /**
     * The outputs this recipe can generate with the given inputs.
     *
     * @param inputs Current ingredients in the assembly table.
     * @return All possible outputs, or an empty set if nothing can be assembled from them.
     */
    public abstract Set<ItemStack> getOutputs(NonNullList<ItemStack> inputs);

    /** Used to determine all outputs from this recipe for recipe previews, such as the guide book or JEI. */
    public abstract Set<ItemStack> getOutputPreviews();

    /**
     * Used to determine what items to use up for the given output.
     *
     * @param output Only ever a stack obtained from {@link #getOutputs} or {@link #getOutputPreviews}.
     */
    public abstract Set<IngredientStack> getInputsFor(@NotNull ItemStack output);

    /**
     * Used to determine how much MJ is required to assemble the given output item.
     *
     * @param output Only ever a stack obtained from {@link #getOutputs} or {@link #getOutputPreviews}.
     */
    public abstract long getRequiredMicroJoulesFor(@NotNull ItemStack output);

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return name.equals(((AssemblyRecipe) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public int compareTo(AssemblyRecipe o) {
        return name.compareTo(o.name);
    }
}
