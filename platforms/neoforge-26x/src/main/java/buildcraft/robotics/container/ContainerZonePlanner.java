/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.robotics.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.robotics.tile.TileZonePlanner;

import buildcraft.BCRoboticsRegistries;

/**
 * Slot layout trimmed down from 1.12.2's {@code ContainerZonePlanner} to what {@link TileZonePlanner} actually
 * still has -- see that class's own javadoc for the full account of why the map-location input/output slot pairs
 * and their progress bars are cut. What remains is the 4x4 paintbrush storage grid, unchanged in position from
 * the original.
 */
public class ContainerZonePlanner extends ContainerBCTile<TileZonePlanner> {
    public ContainerZonePlanner(MenuType<?> type, int windowId, Inventory playerInv, TileZonePlanner tile) {
        super(type, windowId, tile);

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                addSlot(new SlotBase(tile.invPaintbrushes, x * 4 + y, 8 + x * 18, 84 + y * 18));
            }
        }
        addFullPlayerInventory(playerInv, 160);
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerZonePlanner(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(BCRoboticsRegistries.ZONE_PLANNER_MENU.get(), windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileZonePlanner lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileZonePlanner tile) {
            return tile;
        }
        throw new IllegalStateException("No Zone Planner tile at " + pos);
    }
}
