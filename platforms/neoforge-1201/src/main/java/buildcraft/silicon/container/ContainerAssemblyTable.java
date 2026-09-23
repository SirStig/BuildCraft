/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.container;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;

import buildcraft.silicon.tile.TileAssemblyTable;

import buildcraft.BCSiliconRegistries;

/** Mirrors the 26.x copy of this class. */
public class ContainerAssemblyTable extends ContainerBCTile<TileAssemblyTable> {
    public ContainerAssemblyTable(int windowId, Inventory playerInv, TileAssemblyTable tile) {
        super(BCSiliconRegistries.ASSEMBLY_TABLE_MENU.get(), windowId, tile);
        addFullPlayerInventory(playerInv, 123);

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotBase(tile.inv, x + y * 3, 8 + x * 18, 36 + y * 18));
            }
        }

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotDisplay(this::getDisplay, x + y * 3, 116 + x * 18, 36 + y * 18));
            }
        }
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerAssemblyTable(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileAssemblyTable lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileAssemblyTable tile) {
            return tile;
        }
        throw new IllegalStateException("No assembly table tile at " + pos);
    }

    private ItemStack getDisplay(int index) {
        return index < tile.recipesStates.size()
            ? new ArrayList<>(tile.recipesStates.keySet()).get(index).output
            : ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        tile.toggleRecipe(id);
        return true;
    }
}
