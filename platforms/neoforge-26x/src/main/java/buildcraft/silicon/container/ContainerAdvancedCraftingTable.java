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
import buildcraft.lib.gui.slot.SlotPhantom;

import buildcraft.silicon.tile.TileAdvancedCraftingTable;

import buildcraft.BCSiliconRegistries;

/** Ported from 1.12.2's {@code ContainerAdvancedCraftingTable}: unchanged slot layout. The display slot at
 * (127, 33) reads {@link TileAdvancedCraftingTable#getCurrentRecipeOutput()} live -- see that tile's own javadoc
 * for why no {@code resultClient} field is needed here any more. */
public class ContainerAdvancedCraftingTable extends ContainerBCTile<TileAdvancedCraftingTable> {
    public ContainerAdvancedCraftingTable(MenuType<?> type, int windowId, Inventory playerInv, TileAdvancedCraftingTable tile) {
        super(type, windowId, tile);
        addFullPlayerInventory(playerInv, 153);

        addSlot(new SlotDisplay(i -> tile.getCurrentRecipeOutput(), 0, 127, 33));

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 5; x++) {
                addSlot(new SlotBase(tile.invMaterials, x + y * 5, 15 + x * 18, 85 + y * 18));
            }
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotOutput(tile.invResults, x + y * 3, 109 + x * 18, 85 + y * 18));
            }
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotPhantom(tile.invBlueprint, x + y * 3, 33 + x * 18, 16 + y * 18, false));
            }
        }
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerAdvancedCraftingTable(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(BCSiliconRegistries.ADVANCED_CRAFTING_TABLE_MENU.get(), windowId, playerInv,
            lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileAdvancedCraftingTable lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileAdvancedCraftingTable tile) {
            return tile;
        }
        throw new IllegalStateException("No advanced crafting table tile at " + pos);
    }
}
