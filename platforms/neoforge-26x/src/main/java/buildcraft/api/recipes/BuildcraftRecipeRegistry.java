/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.recipes;

import org.jetbrains.annotations.Nullable;

/** Holds the recipe registries, which the silicon and energy modules fill in on startup. */
public final class BuildcraftRecipeRegistry {

    @Nullable
    public static IIntegrationRecipeRegistry integrationRecipes;

    @Nullable
    public static IRefineryRecipeManager refineryRecipes;

    private BuildcraftRecipeRegistry() {
    }
}
