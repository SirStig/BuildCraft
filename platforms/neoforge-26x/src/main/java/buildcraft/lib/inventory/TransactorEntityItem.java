/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.inventory.IItemTransactor.IItemExtractable;

/**
 * Lets a dropped {@link ItemEntity} be drained through {@link IItemTransactor}.
 *
 * <p>A ground item entity isn't a {@link net.neoforged.neoforge.transfer.ResourceHandler ResourceHandler}, so
 * unlike everything else in this package it has no handler to delegate the transaction bookkeeping to -- it has
 * to participate by hand. {@link SnapshotJournal} is the primitive NeoForge itself uses for exactly that (it is
 * what {@code ItemStackResourceHandler}, the class backing every single container slot, extends): call
 * {@link #updateSnapshots} right before mutating, and implement a snapshot/revert pair for what "mutating" means
 * here.
 *
 * <p>1.12.2 called {@code entity.setDead()} once the stack emptied. That isn't safely revertible -- there is no
 * "un-discard" -- so this only ever reduces the stack; {@link ItemEntity#tick()} already discards the entity
 * once {@link ItemEntity#getItem()} comes back empty, so the outcome is identical without ever taking an
 * irreversible action mid-transaction.
 */
public class TransactorEntityItem extends SnapshotJournal<ItemStack> implements IItemExtractable {

    private final ItemEntity entity;

    public TransactorEntityItem(ItemEntity entity) {
        this.entity = entity;
    }

    @Override
    protected ItemStack createSnapshot() {
        return entity.getItem().copy();
    }

    @Override
    protected void revertToSnapshot(ItemStack snapshot) {
        entity.setItem(snapshot);
    }

    @Nullable
    @Override
    public ResourceStack<ItemResource> extract(@Nullable IStackFilter filter, int min, int max, TransactionContext transaction) {
        if (entity.isRemoved()) {
            return null;
        }
        if (min < 1) {
            min = 1;
        }
        if (max < min) {
            return null;
        }
        ItemStack current = entity.getItem();
        if (current.isEmpty() || current.getCount() < min) {
            return null;
        }
        ItemResource resource = ItemResource.of(current);
        if (!IItemTransactor.matches(filter, resource)) {
            return null;
        }
        int count = Math.min(current.getCount(), max);
        updateSnapshots(transaction);
        entity.setItem(current.copyWithCount(current.getCount() - count));
        return new ResourceStack<>(resource, count);
    }

    @Override
    public String toString() {
        return entity.toString();
    }
}
