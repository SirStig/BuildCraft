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
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Fired when a BuildCraft machine is about to place a block on a player's behalf. Cancel to stop it. */
@Cancelable
public class BlockPlacedDownEvent extends Event {

    public final Player player;
    public final BlockState state;
    public final BlockPos pos;

    public BlockPlacedDownEvent(Player player, BlockPos pos, BlockState state) {
        this.player = player;
        this.state = state;
        this.pos = pos;
    }
}
