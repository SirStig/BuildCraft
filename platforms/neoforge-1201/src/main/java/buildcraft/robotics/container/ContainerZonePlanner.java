/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.robotics.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.robotics.tile.TileZonePlanner;

import buildcraft.BCRoboticsRegistries;

/**
 * Slot layout trimmed down from 1.12.2's {@code ContainerZonePlanner} -- see the 26.x copy of this class for the
 * full account of the map-location-exchange scope cut. What remains is the 4x4 paintbrush storage grid.
 */
public class ContainerZonePlanner extends ContainerBCTile<TileZonePlanner> {
    public ContainerZonePlanner(int windowId, Inventory playerInv, TileZonePlanner tile) {
        super(BCRoboticsRegistries.ZONE_PLANNER_MENU.get(), windowId, tile);

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                addSlot(new SlotBase(tile.invPaintbrushes, x * 4 + y, 8 + x * 18, 84 + y * 18));
            }
        }
        addFullPlayerInventory(playerInv, 160);
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerZonePlanner(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileZonePlanner lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileZonePlanner tile) {
            return tile;
        }
        throw new IllegalStateException("No Zone Planner tile at " + pos);
    }
}
