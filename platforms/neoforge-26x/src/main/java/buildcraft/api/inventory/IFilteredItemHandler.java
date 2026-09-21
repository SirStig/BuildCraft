/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.api.inventory;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A {@link ResourceHandler} that has a single valid item per slot, as given by {@link #getFilter(int)}.
 *
 * <p>This was {@code IItemHandlerFiltered} in 1.12.2, extending {@code IItemHandler}. That interface no longer
 * exists on 26.x; the equivalent is {@code ResourceHandler<ItemResource>}, so the name follows the type.
 *
 * <p>Any item handler may implement this, even if the filter behaviour is more complex or there is no real
 * filter at all.
 */
public interface IFilteredItemHandler extends ResourceHandler<ItemResource> {

    /**
     * @param slot The slot to test.
     * @return The filter in that slot. {@link ItemResource#EMPTY} if the slot is not filtered to a single item --
     *         because it matches several, or nothing, or a wide range. Equal to {@code getResource(slot)} if the
     *         slot currently holds an item.
     */
    default ItemResource getFilter(int slot) {
        return getResource(slot);
    }
}
