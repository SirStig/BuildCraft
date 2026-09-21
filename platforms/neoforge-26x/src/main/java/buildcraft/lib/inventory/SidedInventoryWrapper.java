/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * Adapts a {@link WorldlyContainer} (1.12.2's {@code ISidedInventory}) to a side-restricted
 * {@code IItemTransactor}, honouring {@link WorldlyContainer#canPlaceItemThroughFace} and
 * {@link WorldlyContainer#canTakeItemThroughFace}.
 *
 * <p>See {@link InventoryWrapper}: NeoForge's {@link WorldlyContainerWrapper} already does exactly what
 * 1.12.2's version of this class wrote by hand, so this only has to hand it off to
 * {@link AbstractInvItemTransactor}.
 */
public final class SidedInventoryWrapper extends AbstractInvItemTransactor {
    private final ResourceHandler<ItemResource> handler;

    public SidedInventoryWrapper(WorldlyContainer sided, Direction face) {
        this.handler = new WorldlyContainerWrapper(sided, face);
    }

    @Override
    protected ResourceHandler<ItemResource> handler() {
        return handler;
    }
}
