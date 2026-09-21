/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.recipes.AssemblyRecipe;

public class AssemblyRecipeRegistry  {
    public static final Map<Identifier, AssemblyRecipe> REGISTRY = new HashMap<>();

    public static void register(AssemblyRecipe recipe) {
        REGISTRY.put(recipe.getName(), recipe);
    }


    @NotNull
    public static List<AssemblyRecipe> getRecipesFor(@NotNull NonNullList<ItemStack> possibleIn) {
        List<AssemblyRecipe> all = new ArrayList<>();
        for (AssemblyRecipe ar : REGISTRY.values()) {
            if (!ar.getOutputs(possibleIn).isEmpty()) {
                all.add(ar);
            }
        }
        return all;
    }
}
