/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Handles a stripes pipe using an item in front of it. */
public interface IStripesHandlerItem {

    /**
     * Called to handle the given {@link ItemStack} within the world.
     *
     * <p>Note that the player's inventory will be empty, except that the target stack will be set into its
     * {@link InteractionHand#MAIN_HAND}. Any items left in the player's inventory will be returned through the
     * activator with {@link IStripesActivator#sendItem(ItemStack, Direction)}.
     *
     * @param stack The {@link ItemStack} being used.
     * @return True if this used the item, false otherwise. Note that a handler MUST NOT return false if it has
     *         changed the world in any way.
     */
    boolean handle(
        Level level,
        BlockPos pos,
        Direction direction,
        ItemStack stack,
        Player player,
        IStripesActivator activator
    );
}
