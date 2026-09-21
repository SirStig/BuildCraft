/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Implemented by pipes that can accept items in a similar fashion to BuildCraft pipes.
 *
 * <p>The signature follows {@code buildcraft.api.inventory.IItemTransactor} on this target: an {@code ItemStack}
 * splits into an {@link ItemResource} plus a count, and the {@code doAdd} boolean becomes a
 * {@link TransactionContext} that this method never commits. Everything moving items on 26.x speaks that
 * vocabulary, and a pipe that did not would need an adapter at every call site.
 */
public interface IInjectable {
    /**
     * Tests whether this pipe can accept items from the given direction. There is no point calling this if you
     * are going to call {@link #injectItem} straight after.
     */
    boolean canInjectItems(Direction from);

    /**
     * Offers items for addition to the pipe. Rejected if the pipe does not accept items from that side.
     *
     * <p>This should never be called on the client side; implementors are free to throw if it is.
     *
     * @param resource The item offered for addition.
     * @param count How many are offered.
     * @param from The side the items are offered from.
     * @param color The colour of the items to be added to the pipe, or null for no colour.
     * @param speed The speed of the items to be added, in blocks per tick, or {@code <= 0} for the default.
     * @param transaction The enclosing transaction. This method never commits.
     * @return How many were accepted, which is 0 if none were.
     */
    int injectItem(
        ItemResource resource,
        int count,
        Direction from,
        @Nullable DyeColor color,
        double speed,
        TransactionContext transaction
    );
}
