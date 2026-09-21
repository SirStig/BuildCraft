/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.recipes;

import buildcraft.api.core.IStackFilter;

/**
 * A filter plus how many matching items a recipe wants.
 *
 * @deprecated TEMPORARY CLASS DO NOT USE! Carried over from 1.12.2 as-is; {@link IngredientStack} is the
 *             supported way to say this.
 */
@Deprecated
public record StackDefinition(IStackFilter filter, int count) {

    public StackDefinition(IStackFilter filter) {
        this(filter, 1);
    }
}
