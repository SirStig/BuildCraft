/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.gui.slot.SlotPhantom;

import buildcraft.factory.tile.TileAutoWorkbenchBase;

import buildcraft.BCFactoryRegistries;

/**
 * Slot layout, unchanged from 1.12.2 -- see the 26.x copy of this class for the full layout description and the
 * filter-overlay-icon drop note. This file differs only in how the client-side menu is reconstructed: on 1.20.1,
 * {@code NetworkHooks.openScreen} already writes the tile's {@code BlockPos} for the client automatically (see
 * {@code BlockAutoWorkbenchItems#use}), so the {@link FriendlyByteBuf} factory constructor below just reads it
 * straight back, with no {@code writeClientSideData} override needed on the tile the way 26.x needs.
 */
public class ContainerAutoCraftItems extends ContainerBCTile<TileAutoWorkbenchBase> {
    public final SlotBase[] materialSlots;
    private final DataSlot progress;

    public ContainerAutoCraftItems(int windowId, Inventory playerInv, TileAutoWorkbenchBase tile) {
        super(BCFactoryRegistries.AUTO_WORKBENCH_ITEMS_MENU.get(), windowId, tile);

        addSlot(new SlotOutput(tile.invResult, 0, 124, 35));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotPhantom(tile.invBlueprint, x + y * 3, 30 + x * 18, 17 + y * 18, false));
            }
        }
        materialSlots = new SlotBase[9];
        for (int x = 0; x < 9; x++) {
            // Hides the filter slots, but they're still synced -- exactly like 1.12.2.
            addSlot(new SlotPhantom(tile.invMaterialFilter, x, -1_000_000, -1_000_000));
            materialSlots[x] = (SlotBase) addSlot(new SlotBase(tile.invMaterials, x, 8 + x * 18, 84));
        }
        addSlot(new SlotDisplay(i -> tile.getCurrentRecipeOutput(), 0, 93, 27));

        addFullPlayerInventory(playerInv, 115);

        progress = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return tile.getPowerStoredForSync();
            }

            @Override
            public void set(int value) {
                tile.setPowerStoredForSync(value);
            }
        });
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerAutoCraftItems(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileAutoWorkbenchBase lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileAutoWorkbenchBase tile) {
            return tile;
        }
        throw new IllegalStateException("No auto-workbench tile at " + pos);
    }

    /** @return The current craft progress, from 0 to 1. */
    public float getProgress() {
        return (float) progress.get() / TileAutoWorkbenchBase.POWER_REQUIRED;
    }
}
