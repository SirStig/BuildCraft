/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.recipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * An {@link Ingredient} plus how many of it a recipe wants.
 *
 * <p>NeoForge 26.x ships {@code net.neoforged.neoforge.common.crafting.SizedIngredient}, which is this class
 * exactly -- same two fields, plus codecs. BuildCraft keeps its own anyway, for one reason: 1.20.1 has no such
 * type, and adopting NeoForge's here would fork every file in this package that mentions it. One shared record
 * is cheaper than six forked recipe interfaces. Converting is a constructor call at the boundary.
 *
 * <p>{@code CraftingHelper.getIngredient(Object)}, which {@code of(Object)} used to call, was removed -- it
 * guessed at an ingredient from an item, a stack or an ore dictionary name, and the ore dictionary is gone.
 * Build the {@link Ingredient} with its own factories instead.
 */
public record IngredientStack(Ingredient ingredient, int count) {

    public IngredientStack(Ingredient ingredient) {
        this(ingredient, 1);
    }

    /** @return True if {@code stack} matches the ingredient and there are at least {@link #count} of it. */
    public boolean test(ItemStack stack) {
        return !stack.isEmpty() && ingredient.test(stack) && stack.getCount() >= count;
    }
}
