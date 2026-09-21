/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import buildcraft.api.inventory.IFilteredItemHandler;

/**
 * Delegates every call to another handler, bridging {@link IFilteredItemHandler#getFilter} through if the
 * delegate supports it.
 *
 * <p>1.12.2 hand-wrote this against {@code IItemHandlerModifiable} (eight methods). NeoForge already ships
 * {@link DelegatingResourceHandler}, which does the same job generically for any
 * {@code ResourceHandler<Resource>}; this only has to add the filter bridge on top.
 */
public class DelegateItemHandler extends DelegatingResourceHandler<ItemResource> implements IFilteredItemHandler {

    public DelegateItemHandler(ResourceHandler<ItemResource> delegate) {
        super(delegate);
    }

    @Override
    public ItemResource getFilter(int slot) {
        ResourceHandler<ItemResource> delegate = getDelegate();
        if (delegate instanceof IFilteredItemHandler filtered) {
            return filtered.getFilter(slot);
        }
        return IFilteredItemHandler.super.getFilter(slot);
    }
}
