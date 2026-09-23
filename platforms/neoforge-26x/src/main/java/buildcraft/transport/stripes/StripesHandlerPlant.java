/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.api.crops.CropManager;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;

/** A direct port of 1.12.2's own {@code StripesHandlerPlant}: tries planting the offered seed one block below the
 * stripes pipe's open face first (matching a farmer standing above open farmland), then directly at that face. */
public enum StripesHandlerPlant implements IStripesHandlerItem {
    INSTANCE;

    @Override
    public boolean handle(
        Level level, BlockPos pos, Direction direction, ItemStack stack, Player player, IStripesActivator activator
    ) {
        return CropManager.plantCrop(level, player, stack, pos.relative(direction).below())
            || CropManager.plantCrop(level, player, stack, pos.relative(direction));
    }
}
