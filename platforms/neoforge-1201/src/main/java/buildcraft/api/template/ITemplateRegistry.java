/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.template;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.api.core.EnumHandlerPriority;

/** Every {@link ITemplateHandler}, consulted in {@link EnumHandlerPriority} order. */
public interface ITemplateRegistry {
    /** Adds a handler with a priority of {@link EnumHandlerPriority#NORMAL}. */
    default void addHandler(ITemplateHandler handler) {
        addHandler(handler, EnumHandlerPriority.NORMAL);
    }

    void addHandler(ITemplateHandler handler, EnumHandlerPriority priority);

    boolean handle(Level level, BlockPos pos, Player player, ItemStack stack);
}
