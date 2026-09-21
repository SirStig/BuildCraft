/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.inventory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;

/**
 * A simple way to define something that deals with item insertion and extraction, without caring about slots.
 *
 * <p>This is the 1.20.1 copy and keeps the 1.12.2 shape unchanged: stacks carry their own count, and
 * {@code boolean simulate} still means what it did. The 26.x copy splits a stack into an {@code ItemResource}
 * plus an amount and replaces {@code simulate} with a {@code Transaction}, which is why the two cannot be
 * shared.
 */
public interface IItemTransactor {
    /**
     * @param stack The stack to insert. Must not be null.
     * @param allOrNone If true then either the entire stack will be used or none of it.
     * @param simulate If true then the in-world state of this will not be changed.
     * @return The overflow stack. Will be {@link ItemStack#EMPTY} if all of it was accepted.
     */
    @NotNull
    ItemStack insert(@NotNull ItemStack stack, boolean allOrNone, boolean simulate);

    /**
     * Similar to {@link #insert(ItemStack, boolean, boolean)} but probably more efficient at inserting lots of
     * items.
     *
     * @param stacks The stacks to insert. Must not be null.
     * @param simulate If true then the in-world state of this will not be changed.
     * @return The overflow stacks. Will be an empty list if all of it was accepted.
     */
    default NonNullList<ItemStack> insert(NonNullList<ItemStack> stacks, boolean simulate) {
        NonNullList<ItemStack> leftOver = NonNullList.create();
        for (ItemStack stack : stacks) {
            ItemStack leftOverStack = insert(stack, false, simulate);
            if (!leftOverStack.isEmpty()) {
                leftOver.add(leftOverStack);
            }
        }
        return leftOver;
    }

    /**
     * Extracts a number of items that match the given filter.
     *
     * @param filter The filter that MUST be met by the extracted stack. Null means no filter -- any item will do.
     * @param min The minimum number of items to extract, or 0 if not enough items can be extracted.
     * @param max The maximum number of items to extract.
     * @param simulate If true then the in-world state of this will not be changed.
     * @return The stack that was extracted, or {@link ItemStack#EMPTY} if it could not be.
     */
    @NotNull
    ItemStack extract(@Nullable IStackFilter filter, int min, int max, boolean simulate);

    default boolean canFullyAccept(@NotNull ItemStack stack) {
        return insert(stack, true, true).isEmpty();
    }

    default boolean canPartiallyAccept(@NotNull ItemStack stack) {
        return insert(stack, false, true).getCount() < stack.getCount();
    }

    /** A transactor that only accepts items. */
    @FunctionalInterface
    interface IItemInsertable extends IItemTransactor {
        @NotNull
        @Override
        default ItemStack extract(@Nullable IStackFilter filter, int min, int max, boolean simulate) {
            return ItemStack.EMPTY;
        }
    }

    /** A transactor that only gives items up. */
    @FunctionalInterface
    interface IItemExtractable extends IItemTransactor {
        @NotNull
        @Override
        default ItemStack insert(@NotNull ItemStack stack, boolean allOrNone, boolean simulate) {
            return stack;
        }
    }
}
