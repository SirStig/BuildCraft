/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.inventory;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.core.IStackFilter;

/**
 * A simple way to define something that deals with item insertion and extraction, without caring about slots.
 *
 * <p>This keeps its shape from 1.12.2, because 26.x's {@code ResourceHandler} does not cover what it does:
 * a handler inserts and extracts per slot and has no notion of "all or none across the whole inventory", nor of
 * a minimum extraction count. Pipes need both.
 *
 * <p>What changes is the vocabulary. An {@code ItemStack} argument splits into an {@link ItemResource} plus an
 * amount, matching the rest of the transfer API, and {@code boolean simulate} becomes a
 * {@link TransactionContext}: nothing is committed here, so a caller simulates by rolling its transaction back
 * and commits to keep the effect.
 *
 * <pre>{@code
 * try (Transaction transaction = Transaction.openRoot()) {
 *     int moved = transactor.insert(resource, 64, true, transaction);
 *     if (moved == 64) {
 *         transaction.commit();
 *     }
 * } // rolled back if not committed
 * }</pre>
 *
 * <p>Filtering still goes through {@link IStackFilter}, which tests an {@code ItemStack}, rather than a parallel
 * resource-shaped filter. {@link ItemResource#test} exists precisely to bridge the two, so there is no reason for
 * BuildCraft to carry a second filter type that callers would have to implement twice.
 */
public interface IItemTransactor {

    /**
     * @param resource The item to insert.
     * @param amount How many to insert. Must be positive.
     * @param allOrNone If true then either the entire amount is inserted or none of it is.
     * @param transaction The enclosing transaction. This method never commits.
     * @return How many were inserted, which is 0 if none were.
     */
    int insert(ItemResource resource, int amount, boolean allOrNone, TransactionContext transaction);

    /**
     * Extracts a number of items that match the given filter.
     *
     * @param filter The filter the extracted stack must match. Null means no filter -- any item will do.
     * @param min The minimum number of items to extract; nothing is extracted if that many cannot be.
     * @param max The maximum number of items to extract.
     * @param transaction The enclosing transaction. This method never commits.
     * @return What was extracted, or null if nothing could be.
     */
    @Nullable
    ResourceStack<ItemResource> extract(
        @Nullable IStackFilter filter,
        int min,
        int max,
        TransactionContext transaction
    );

    /** @return True if all {@code amount} of {@code resource} would be accepted. Changes nothing. */
    default boolean canFullyAccept(ItemResource resource, int amount, TransactionContext transaction) {
        try (Transaction simulation = Transaction.open(transaction)) {
            return insert(resource, amount, true, simulation) == amount;
        }
    }

    /** @return True if at least one of {@code resource} would be accepted. Changes nothing. */
    default boolean canPartiallyAccept(ItemResource resource, int amount, TransactionContext transaction) {
        try (Transaction simulation = Transaction.open(transaction)) {
            return insert(resource, amount, false, simulation) > 0;
        }
    }

    /** Tests a resource against a filter, treating a null filter as "anything". */
    static boolean matches(@Nullable IStackFilter filter, ItemResource resource) {
        return filter == null || resource.test(filter::matches);
    }

    /** A transactor that only accepts items. */
    @FunctionalInterface
    interface IItemInsertable extends IItemTransactor {
        @Nullable
        @Override
        default ResourceStack<ItemResource> extract(
            @Nullable IStackFilter filter,
            int min,
            int max,
            TransactionContext transaction
        ) {
            return null;
        }
    }

    /** A transactor that only gives items up. */
    @FunctionalInterface
    interface IItemExtractable extends IItemTransactor {
        @Override
        default int insert(ItemResource resource, int amount, boolean allOrNone, TransactionContext transaction) {
            return 0;
        }
    }
}
