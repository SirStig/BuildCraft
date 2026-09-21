/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.api.core;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Filtered extraction from a fluid handler.
 *
 * <p>This replaces 1.12.2's {@code IFluidHandlerAdv}, which existed because {@code IFluidHandler.drain} could only
 * be told "this exact fluid" or "anything", with no way to say "any of these". That was solved by making tanks
 * implement an extra interface, which meant it only ever worked on BuildCraft's own tanks.
 *
 * <p>26.x's {@link ResourceHandler} is slot-indexed and exposes the resource in each slot, so the same thing can be
 * done from outside against <em>any</em> handler -- vanilla's, another mod's, or BuildCraft's. There is no longer a
 * reason for it to be an interface, so it is a utility instead.
 *
 * <p>The {@code boolean doDrain} parameter is gone with it: 26.x scopes simulation with
 * {@link Transaction} instead. {@link #extractFiltered} takes the surrounding context and does not commit, so
 * callers simulate by passing a transaction they roll back and execute by committing it:
 *
 * <pre>{@code
 * try (Transaction transaction = Transaction.openRoot()) {
 *     int drained = FluidFilters.extractFiltered(handler, filter, 1000, transaction);
 *     if (drained == 1000) {
 *         transaction.commit();
 *     }
 * } // rolled back if not committed
 * }</pre>
 */
public final class FluidFilters {

    private FluidFilters() {
    }

    /**
     * Extracts up to {@code maxAmount} of the first fluid in {@code handler} that {@code filter} accepts.
     *
     * <p>Only one fluid is drained, matching {@code IFluidHandler.drain}'s contract: extraction stops at the end of
     * the first matching slot's fluid rather than mixing two fluids into one result.
     *
     * @return The amount extracted, which is 0 if nothing matched.
     */
    public static int extractFiltered(
        ResourceHandler<FluidResource> handler,
        IFluidFilter filter,
        int maxAmount,
        TransactionContext transaction
    ) {
        if (maxAmount <= 0) {
            return 0;
        }
        FluidResource found = findExtractable(handler, filter, maxAmount, transaction);
        if (found == null) {
            return 0;
        }
        return handler.extract(found, maxAmount, transaction);
    }

    /**
     * Finds the first fluid in {@code handler} that {@code filter} accepts and that is actually extractable.
     *
     * <p>A slot can hold a fluid it will not give up, so this confirms extraction is possible by simulating one in
     * a nested transaction that is never committed.
     *
     * @return The matching resource, or null if there is none.
     */
    public static FluidResource findExtractable(
        ResourceHandler<FluidResource> handler,
        IFluidFilter filter,
        int maxAmount,
        TransactionContext transaction
    ) {
        for (int slot = 0; slot < handler.size(); slot++) {
            FluidResource resource = handler.getResource(slot);
            if (resource.isEmpty() || !filter.matches(resource)) {
                continue;
            }
            try (Transaction simulation = Transaction.open(transaction)) {
                if (handler.extract(resource, maxAmount, simulation) > 0) {
                    return resource;
                }
            }
        }
        return null;
    }
}
