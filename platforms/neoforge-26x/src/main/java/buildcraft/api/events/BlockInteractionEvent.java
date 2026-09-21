/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.events;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired when a BuildCraft machine is about to interact with a block on a player's behalf. Cancel to stop it.
 *
 * <p>{@code @Cancelable} no longer exists. NeoForge decides cancellability by whether the event implements
 * {@link ICancellableEvent}, which also means {@code setCanceled} is only visible on events that actually
 * support it, rather than on every event and throwing at runtime.
 */
public class BlockInteractionEvent extends Event implements ICancellableEvent {

    public final Player player;
    public final BlockState state;

    public BlockInteractionEvent(Player player, BlockState state) {
        this.player = player;
        this.state = state;
    }
}
