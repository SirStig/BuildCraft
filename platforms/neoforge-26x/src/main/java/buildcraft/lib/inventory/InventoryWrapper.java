/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import net.minecraft.world.Container;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

import buildcraft.api.inventory.IItemTransactor;

/**
 * Adapts a vanilla {@link Container} (1.12.2's {@code IInventory}) to {@link IItemTransactor}.
 *
 * <p>1.12.2 walked the container's slots by hand. NeoForge already ships {@link VanillaContainerWrapper} to turn
 * any {@link Container} into a {@code ResourceHandler<ItemResource>} -- the same object
 * {@code VanillaContainerWrapper}'s own javadoc says to use for exactly this -- so there is no reason to
 * reimplement that here; this class now only has to hand the result to
 * {@link AbstractInvItemTransactor}.
 */
public final class InventoryWrapper extends AbstractInvItemTransactor {
    private final ResourceHandler<ItemResource> handler;

    public InventoryWrapper(Container inventory) {
        this.handler = VanillaContainerWrapper.of(inventory);
    }

    @Override
    protected ResourceHandler<ItemResource> handler() {
        return handler;
    }
}
