/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * Provides a convenient means of dealing with entire classes of items without having to specify each item
 * individually.
 */
@FunctionalInterface
public interface IStackFilter {

    /**
     * Check to see if a given stack matches this filter.
     *
     * @param stack The stack to test. {@code stack.isEmpty()} will always return false.
     * @return True if it does match, false otherwise.
     */
    boolean matches(@NotNull ItemStack stack);

    default IStackFilter and(IStackFilter filter) {
        IStackFilter before = this;
        return stack -> before.matches(stack) && filter.matches(stack);
    }

    /** @return Example stacks that match this filter, for display purposes. */
    default NonNullList<ItemStack> getExamples() {
        return NonNullList.withSize(0, ItemStack.EMPTY);
    }
}
