/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.template;

import org.jetbrains.annotations.Nullable;

/** Holds the template registry, which the builders module fills in on startup. */
public final class TemplateApi {

    @Nullable
    public static ITemplateRegistry templateRegistry;

    private TemplateApi() {
    }
}
