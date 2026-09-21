/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.lists;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** Decides which items a single row of a BuildCraft list matches. */
public abstract class ListMatchHandler {
    public enum Type {
        TYPE,
        MATERIAL,
        CLASS
    }

    public abstract boolean matches(Type type, @NotNull ItemStack stack, @NotNull ItemStack target, boolean precise);

    public abstract boolean isValidSource(Type type, @NotNull ItemStack stack);

    /**
     * Get custom client examples.
     *
     * @return A list, even an empty one, if the examples satisfy this handler; null if iteration and
     *         {@link #matches} should be used instead.
     */
    @Nullable
    public NonNullList<ItemStack> getClientExamples(Type type, @NotNull ItemStack stack) {
        return null;
    }
}
