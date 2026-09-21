/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.recipes;

import java.util.Iterator;

/** Every integration table recipe. */
public interface IIntegrationRecipeRegistry extends IIntegrationRecipeProvider {
    void addRecipe(IntegrationRecipe recipe);

    /**
     * Gets all of the simple recipes that are registered. Note that you <em>can</em> use the returned iterator's
     * {@link Iterator#remove()} method to remove recipes from this registry.
     */
    Iterable<IntegrationRecipe> getAllRecipes();
}
