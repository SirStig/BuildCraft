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

import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;

/** A direct port of 1.12.2's own {@code StripesHandlerPipes}: offers an item pipe stack to
 * {@link PipeApi#extensionManager} to be laid ahead of the stripes pipe. A registered retraction pipe (the void
 * pipe) still always declines here -- see {@code PipeExtensionManager}'s own javadoc for why retraction is a
 * documented follow-up, and for what a decline here actually falls through to (not plain item ejection --
 * {@code StripesHandlerPlaceBlock} catches a declined pipe item instead, since every pipe item is also a
 * {@code BlockItem}); every other item pipe now genuinely extends. */
public class StripesHandlerPipes implements IStripesHandlerItem {

    @Override
    public boolean handle(
        Level level, BlockPos pos, Direction direction, ItemStack stack, Player player, IStripesActivator activator
    ) {
        if (!(stack.getItem() instanceof IItemPipe itemPipe)) {
            return false;
        }

        PipeDefinition pipeDefinition = itemPipe.getDefinition();
        if (pipeDefinition.flowType == PipeApi.flowItems) {
            if (PipeApi.extensionManager.requestPipeExtension(level, pos, direction, activator, stack.copy())) {
                player.getInventory().clearContent();
                return true;
            }
        }

        return false;
    }
}
