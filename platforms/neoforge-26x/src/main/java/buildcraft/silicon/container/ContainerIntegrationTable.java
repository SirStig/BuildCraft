/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;
import buildcraft.lib.gui.slot.SlotOutput;

import buildcraft.silicon.tile.TileIntegrationTable;

import buildcraft.BCSiliconRegistries;

/** Ported from 1.12.2's {@code ContainerIntegrationTable}: unchanged slot layout -- the centre slot of the 3x3
 * grid is {@link TileIntegrationTable#invTarget}, the other eight are {@link TileIntegrationTable#invToIntegrate}. */
public class ContainerIntegrationTable extends ContainerBCTile<TileIntegrationTable> {
    public ContainerIntegrationTable(MenuType<?> type, int windowId, Inventory playerInv, TileIntegrationTable tile) {
        super(type, windowId, tile);
        addFullPlayerInventory(playerInv, 109);

        int[] indexes = { 0, 1, 2, 3, 0, 4, 5, 6, 7 };

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                boolean centre = x == 1 && y == 1;
                addSlot(new SlotBase(centre ? tile.invTarget : tile.invToIntegrate, indexes[x + y * 3], 19 + x * 25, 24 + y * 25));
            }
        }

        addSlot(new SlotDisplay(i -> tile.getOutput(), 0, 101, 36));

        addSlot(new SlotOutput(tile.invResult, 0, 138, 49));
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerIntegrationTable(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(BCSiliconRegistries.INTEGRATION_TABLE_MENU.get(), windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileIntegrationTable lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileIntegrationTable tile) {
            return tile;
        }
        throw new IllegalStateException("No integration table tile at " + pos);
    }
}
