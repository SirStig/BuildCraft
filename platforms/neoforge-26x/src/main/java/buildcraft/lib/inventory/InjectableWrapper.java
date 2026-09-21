/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.transport.IInjectable;

/**
 * Adapts an {@link IInjectable} pipe to {@link IItemTransactor}.
 *
 * <p>1.12.2's {@code injectItem} returned the leftover {@code ItemStack}, so an all-or-nothing insert needed a
 * manual two-call dance: inject simulated, check the leftover was empty, then inject for real and sanity-check
 * that leftover was empty too (there was no other way to insert atomically without a real transaction). This
 * target's {@link IInjectable#injectItem} already returns the accepted amount and takes a
 * {@link TransactionContext} that never commits, so a plain nested {@link Transaction} does the same job the
 * two-call dance existed for -- see {@link AbstractInvItemTransactor} for the identical pattern.
 */
public class InjectableWrapper implements IItemTransactor {
    private final IInjectable injectable;
    private final Direction from;

    public InjectableWrapper(IInjectable injectable, Direction from) {
        this.injectable = injectable;
        this.from = from;
    }

    @Override
    public int insert(ItemResource resource, int amount, boolean allOrNone, TransactionContext transaction) {
        if (allOrNone) {
            try (Transaction simulation = Transaction.open(transaction)) {
                int accepted = injectable.injectItem(resource, amount, from, null, 0, simulation);
                if (accepted == amount) {
                    simulation.commit();
                    return amount;
                }
                return 0;
            }
        } else {
            return injectable.injectItem(resource, amount, from, null, 0, transaction);
        }
    }

    @Nullable
    @Override
    public ResourceStack<ItemResource> extract(@Nullable IStackFilter filter, int min, int max, TransactionContext transaction) {
        return null;
    }
}
