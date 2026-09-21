/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import buildcraft.api.inventory.IFilteredItemHandler;

/**
 * An {@link ItemHandlerSimple} whose {@link IFilteredItemHandler#getFilter} is read from another handler --
 * typically a display-only inventory the player fills in a GUI to say "this slot accepts items like these".
 * Sized to match the filter handler's slot count.
 */
public class ItemHandlerFiltered extends ItemHandlerSimple implements IFilteredItemHandler {
    private final ResourceHandler<ItemResource> filter;
    private final boolean emptyIsAnything;

    public ItemHandlerFiltered(ResourceHandler<ItemResource> filter, boolean emptyIsAnything) {
        super(filter.size());
        this.emptyIsAnything = emptyIsAnything;
        this.filter = filter;
        setChecker((slot, resource) -> {
            ItemResource inSlot = filter.getResource(slot);
            if (inSlot.isEmpty()) {
                return emptyIsAnything;
            } else {
                return inSlot.equals(resource);
            }
        });
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        if (emptyIsAnything || !getFilter(index).isEmpty()) {
            return super.getCapacity(index, resource);
        }
        return 0;
    }

    @Override
    public ItemResource getFilter(int slot) {
        ItemResource current = getResource(slot);
        if (!current.isEmpty()) {
            return current;
        }
        return filter.getResource(slot);
    }
}
