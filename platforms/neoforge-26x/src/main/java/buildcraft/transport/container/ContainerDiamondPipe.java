/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamond;
import buildcraft.transport.tile.TilePipeHolder;

import buildcraft.BCTransportRegistries;

/**
 * The item/fluid diamond pipe's own filter-configuration menu -- a direct port of 1.12.2's own
 * {@code ContainerDiamondPipe}: 54 phantom filter slots (9 per face x 6 faces, {@link PipeBehaviourDiamond#filters}),
 * the full player inventory below at y=140. {@code onPlayerOpen}/{@code onPlayerClose} are not called any more --
 * {@code TilePipeHolder}'s own copies are no-ops (no GUI viewer tracking exists on this port; see that class's own
 * javadoc) and {@link #stillValid} already does the equivalent "is this still a valid place to be" check that
 * 1.12.2's {@code onContainerClosed} companion call existed to eventually clean up after.
 */
public class ContainerDiamondPipe extends ContainerBCTile<TilePipeHolder> {

    public ContainerDiamondPipe(MenuType<?> type, int windowId, Inventory playerInv, PipeBehaviourDiamond behaviour) {
        super(type, windowId, (TilePipeHolder) behaviour.pipe.getHolder().getPipeTile());

        ItemHandlerSimple filterInv = behaviour.filters;
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new SlotPhantom(filterInv, x + y * 9, 8 + x * 18, 18 + y * 18));
            }
        }

        addFullPlayerInventory(playerInv, 140);
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerDiamondPipe(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(BCTransportRegistries.PIPE_DIAMOND_MENU.get(), windowId, playerInv,
            lookupBehaviour(playerInv, extraData.readBlockPos()));
    }

    private static PipeBehaviourDiamond lookupBehaviour(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TilePipeHolder holder && holder.getPipe() != null
            && holder.getPipe().getBehaviour() instanceof PipeBehaviourDiamond diamond) {
            return diamond;
        }
        throw new IllegalStateException("No diamond pipe at " + pos);
    }
}
