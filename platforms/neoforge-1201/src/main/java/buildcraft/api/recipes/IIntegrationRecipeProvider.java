/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.recipes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Provides a way of registering complex recipes without needing to register every possible variant.
 *
 * <p>If you want the recipes to be viewable in JEI and the guide book then you will <em>also</em> need to
 * implement the BuildCraft lib class {@code IIntegrationRecipeViewable}.
 */
public interface IIntegrationRecipeProvider {
    /**
     * Gets an integration recipe for the given ingredients.
     *
     * @param target The centre item stack.
     * @param toIntegrate The stacks to try to integrate into it.
     */
    @Nullable
    IntegrationRecipe getRecipeFor(@NotNull ItemStack target, @NotNull NonNullList<ItemStack> toIntegrate);

    /** @return The recipe with that name, or null if there is none. */
    @Nullable
    IntegrationRecipe getRecipe(@NotNull ResourceLocation name);
}
