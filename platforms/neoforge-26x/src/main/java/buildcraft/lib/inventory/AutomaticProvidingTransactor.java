/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.inventory.IItemTransactor.IItemExtractable;

/** Provides an {@link IItemTransactor} that cannot be inserted or extracted from directly, but implements
 * {@link IItemExtractable} so as to be noticed by pipes (and other machines) as one that will auto-insert into it. */
public enum AutomaticProvidingTransactor implements IItemExtractable {
    INSTANCE;

    @Nullable
    @Override
    public ResourceStack<ItemResource> extract(@Nullable IStackFilter filter, int min, int max, TransactionContext transaction) {
        return null;
    }
}
