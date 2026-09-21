/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Adapts an arbitrary {@code ResourceHandler<ItemResource>} -- typically one exposed by another mod's block
 * entity or item -- to {@code IItemTransactor}.
 *
 * <p>1.12.2 wrapped {@code IItemHandler}, which no longer exists; every handler on this target already speaks
 * {@code ResourceHandler<ItemResource>}, which is exactly what {@link AbstractInvItemTransactor} needs, so this
 * class keeps its name and place in the package (for anything reaching a foreign handler through a capability)
 * without any slot-by-slot logic of its own to write any more.
 */
public final class ItemHandlerWrapper extends AbstractInvItemTransactor {
    private final ResourceHandler<ItemResource> handler;

    public ItemHandlerWrapper(ResourceHandler<ItemResource> handler) {
        this.handler = handler;
    }

    @Override
    protected ResourceHandler<ItemResource> handler() {
        return handler;
    }
}
