/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.api.core.EnumHandlerPriority;

/** Every stripes handler, consulted in {@link EnumHandlerPriority} order. */
public interface IStripesRegistry {
    /** Adds a handler with a priority of {@link EnumHandlerPriority#NORMAL}. */
    default void addHandler(IStripesHandlerItem handler) {
        addHandler(handler, EnumHandlerPriority.NORMAL);
    }

    void addHandler(IStripesHandlerItem handler, EnumHandlerPriority priority);

    /** Adds a handler with a priority of {@link EnumHandlerPriority#NORMAL}. */
    default void addHandler(IStripesHandlerBlock handler) {
        addHandler(handler, EnumHandlerPriority.NORMAL);
    }

    void addHandler(IStripesHandlerBlock handler, EnumHandlerPriority priority);

    /**
     * @param pos The position of the stripes pipe.
     * @return True if a handler handled the item stack, false otherwise -- in which case nothing has been done.
     */
    boolean handleItem(
        Level level,
        BlockPos pos,
        Direction direction,
        ItemStack stack,
        Player player,
        IStripesActivator activator
    );

    /** @return True if a handler broke a block, false otherwise -- in which case nothing has been done. */
    boolean handleBlock(
        Level level,
        BlockPos pos,
        Direction direction,
        Player player,
        IStripesActivator activator
    );
}
