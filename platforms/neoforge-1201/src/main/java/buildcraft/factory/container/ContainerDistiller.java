/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.ContainerBCTile;

import buildcraft.factory.tile.TileDistiller;

import buildcraft.BCFactoryRegistries;

/** 1.12.2's {@code ContainerDistiller} -- see the 26.x copy of this class for what is and is not ported. */
public class ContainerDistiller extends ContainerBCTile<TileDistiller> {

    public ContainerDistiller(int windowId, Inventory playerInv, TileDistiller tile) {
        super(BCFactoryRegistries.DISTILLER_MENU.get(), windowId, tile);
        addFullPlayerInventory(playerInv, 79);
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape; the position is the one
     * {@code NetworkHooks.openScreen} writes. */
    public ContainerDistiller(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileDistiller lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileDistiller tile) {
            return tile;
        }
        throw new IllegalStateException("No distiller tile at " + pos);
    }
}
