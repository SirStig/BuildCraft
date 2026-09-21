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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;

/** Designates an item that can be placed onto a pipe as a {@link PipePluggable}. */
public interface IItemPluggable {
    /**
     * Called when this item is placed onto a pipe holder. May return null if this item does not make a valid
     * pluggable. Note that if you return a non-null pluggable then it will <em>definitely</em> be added to the
     * pipe, and you are responsible for all the effects yourself, such as the sound.
     *
     * @param stack The stack that holds this item.
     * @param holder The pipe holder.
     * @param side The side that the pluggable should be placed on.
     * @return A pluggable to place onto the pipe, or null.
     */
    @Nullable
    PipePluggable onPlace(
        @NotNull ItemStack stack,
        IPipeHolder holder,
        Direction side,
        Player player,
        InteractionHand hand
    );
}
