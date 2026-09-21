/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory.filter;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import buildcraft.api.core.IStackFilter;

/**
 * Tests {@code toTest} against every stack currently sitting in a handler.
 *
 * <p>{@code IItemHandler} does not exist on this target -- see the {@code buildcraft.lib.inventory} package
 * javadoc-equivalent note in {@code AbstractInvItemTransactor} -- so this walks a
 * {@code ResourceHandler<ItemResource>} directly instead. {@code getStackInSlot(slot)} becomes
 * {@code getResource(slot)} plus {@code getAmountAsInt(slot)} turned back into a stack for
 * {@link ISingleStackFilter}, which still compares {@link ItemStack}s -- an empty resource converts to
 * {@link ItemStack#EMPTY}, exactly as an empty slot did before.
 */
public class DelegatingItemHandlerFilter implements IStackFilter {
    private final ISingleStackFilter perStackFilter;
    private final ResourceHandler<ItemResource> handler;

    public DelegatingItemHandlerFilter(ISingleStackFilter perStackFilter, ResourceHandler<ItemResource> handler) {
        this.perStackFilter = perStackFilter;
        this.handler = handler;
    }

    @Override
    public boolean matches(@NotNull ItemStack stack) {
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            ItemStack current = resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(handler.getAmountAsInt(slot));
            if (perStackFilter.matches(current, stack)) {
                return true;
            }
        }
        return false;
    }
}
