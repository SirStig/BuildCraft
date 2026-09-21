/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;

import buildcraft.lib.inventory.filter.StackFilter;

/**
 * Implements {@link IItemTransactor}'s min/max/{@code allOrNone} semantics on top of any
 * {@link ResourceHandler}{@code <}{@link ItemResource}{@code >}.
 *
 * <p>1.12.2 had this class own a small array-backed inventory directly, with {@code InventoryWrapper} and
 * {@code ItemHandlerWrapper} as its only two subclasses -- one walking an {@code IInventory} slot by slot, the
 * other delegating each slot straight through to an {@code IItemHandler}. On this target {@code IItemHandler} is
 * gone, and NeoForge itself already adapts a {@code Container}/{@code WorldlyContainer} into a
 * {@code ResourceHandler<ItemResource>} ({@code VanillaContainerWrapper}, {@code WorldlyContainerWrapper}) --
 * exactly the adapter {@code InventoryWrapper} used to write by hand. So that split collapses: every subclass in
 * this package now only has to produce a {@link ResourceHandler}, however it got one, and this class does the
 * slot scanning once against that single, uniform type.
 *
 * <p>The {@code boolean simulate} parameters are gone with {@code IItemHandler}. Both operations below take the
 * enclosing {@link TransactionContext} and never commit; an all-or-nothing insert is simulated by inserting into
 * a nested {@link Transaction} and only committing that nested transaction (into the parent, which still has to
 * commit itself for anything to really change) if the full amount went in -- the same trick
 * {@code buildcraft.api.core.FluidFilters} uses on the fluid side.
 */
public abstract class AbstractInvItemTransactor implements IItemTransactor {

    /** @return The handler this transactor moves items through. */
    protected abstract ResourceHandler<ItemResource> handler();

    @Override
    public int insert(ItemResource resource, int amount, boolean allOrNone, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        if (allOrNone) {
            try (Transaction simulation = Transaction.open(transaction)) {
                int inserted = handler().insert(resource, amount, simulation);
                if (inserted == amount) {
                    simulation.commit();
                    return amount;
                }
                return 0;
            }
        }
        return handler().insert(resource, amount, transaction);
    }

    @Nullable
    @Override
    public ResourceStack<ItemResource> extract(@Nullable IStackFilter filter, int min, int max, TransactionContext transaction) {
        if (min < 1) min = 1;
        if (min > max) return null;
        if (max < 0) return null;

        IStackFilter effectiveFilter = filter == null ? StackFilter.ALL : filter;

        ResourceHandler<ItemResource> handler = handler();
        int size = handler.size();
        ItemResource found = ItemResource.EMPTY;
        int available = 0;
        int[] validSlots = new int[size];
        int validCount = 0;

        for (int slot = 0; slot < size; slot++) {
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            if (!found.isEmpty()) {
                if (!resource.equals(found)) {
                    continue;
                }
            } else if (!IItemTransactor.matches(effectiveFilter, resource)) {
                continue;
            }
            // Simulate the extraction in a nested (never-committed) transaction to confirm the handler will
            // actually give this slot up -- a slot can hold a matching resource it still refuses to release.
            try (Transaction simulation = Transaction.open(transaction)) {
                int extractable = handler.extract(slot, resource, max - available, simulation);
                if (extractable <= 0) {
                    continue;
                }
                if (found.isEmpty()) {
                    found = resource;
                }
                available += extractable;
                validSlots[validCount++] = slot;
                if (available >= max) {
                    break;
                }
            }
        }

        if (found.isEmpty() || available < min) {
            return null;
        }

        int extracted = 0;
        for (int i = 0; i < validCount; i++) {
            extracted += handler.extract(validSlots[i], found, max - extracted, transaction);
            if (extracted >= max) {
                break;
            }
        }
        return new ResourceStack<>(found, extracted);
    }
}
