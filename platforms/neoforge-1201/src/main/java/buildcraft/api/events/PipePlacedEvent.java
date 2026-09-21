/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.events;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import net.minecraftforge.eventbus.api.Event;

/** Fired after a pipe has been placed. Not cancellable -- the pipe is already there. */
public class PipePlacedEvent extends Event {

    public final Player player;
    public final Item pipeType;
    public final BlockPos pos;

    public PipePlacedEvent(Player player, Item pipeType, BlockPos pos) {
        this.player = player;
        this.pipeType = pipeType;
        this.pos = pos;
    }
}
