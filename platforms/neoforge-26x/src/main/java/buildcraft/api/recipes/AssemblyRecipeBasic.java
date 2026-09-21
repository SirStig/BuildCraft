/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.recipes;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.BuildCraftAPI;

/**
 * An assembly recipe with a fixed ingredient list and a single output.
 *
 * @deprecated TEMPORARY CLASS DO NOT USE!
 */
@Deprecated
public class AssemblyRecipeBasic extends AssemblyRecipe {

    private final long requiredMicroJoules;
    private final Set<IngredientStack> requiredStacks;
    private final Set<ItemStack> output;

    public AssemblyRecipeBasic(
        Identifier name,
        long requiredMicroJoules,
        Set<IngredientStack> requiredStacks,
        @NotNull ItemStack output
    ) {
        super(name);
        this.requiredMicroJoules = requiredMicroJoules;
        this.requiredStacks = Set.copyOf(requiredStacks);
        this.output = Set.of(output);
    }

    public AssemblyRecipeBasic(
        String name,
        long requiredMicroJoules,
        Set<IngredientStack> requiredStacks,
        @NotNull ItemStack output
    ) {
        this(BuildCraftAPI.nameToResourceId(name), requiredMicroJoules, requiredStacks, output);
    }

    @Override
    public Set<ItemStack> getOutputs(NonNullList<ItemStack> inputs) {
        boolean satisfied = requiredStacks.stream()
            .allMatch(definition -> inputs.stream().anyMatch(definition::test));
        return satisfied ? output : Set.of();
    }

    @Override
    public Set<ItemStack> getOutputPreviews() {
        return output;
    }

    @Override
    public Set<IngredientStack> getInputsFor(@NotNull ItemStack output) {
        return requiredStacks;
    }

    @Override
    public long getRequiredMicroJoulesFor(@NotNull ItemStack output) {
        return requiredMicroJoules;
    }
}
