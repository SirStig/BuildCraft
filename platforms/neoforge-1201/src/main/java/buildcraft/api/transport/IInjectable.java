/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * Implemented by pipes that can accept items in a similar fashion to BuildCraft pipes.
 *
 * <p>This keeps the 1.12.2 signature: 1.20.1 has no transfer API, so an {@link ItemStack} still carries its own
 * count and {@code doAdd} still means what it did. The 26.x copy takes an {@code ItemResource} plus a count and
 * a {@code TransactionContext} instead.
 */
public interface IInjectable {
    /**
     * Tests whether this pipe can accept items from the given direction. There is no point calling this if you
     * are going to call {@link #injectItem} straight after.
     */
    boolean canInjectItems(Direction from);

    /**
     * Offers an {@link ItemStack} for addition to the pipe. Rejected if the pipe does not accept items from
     * that side.
     *
     * <p>This should never be called on the client side; implementors are free to throw if it is.
     *
     * @param stack The stack offered for addition. Do not manipulate this.
     * @param doAdd If false no actual addition should take place; implementors should simulate.
     * @param from The side the stack is offered from.
     * @param color The colour of the item to be added to the pipe, or null for no colour.
     * @param speed The speed of the item to be added, in blocks per tick, or {@code <= 0} for the default.
     * @return The left over stack that was not accepted.
     */
    @NotNull
    ItemStack injectItem(
        @NotNull ItemStack stack,
        boolean doAdd,
        Direction from,
        @Nullable DyeColor color,
        double speed
    );
}
