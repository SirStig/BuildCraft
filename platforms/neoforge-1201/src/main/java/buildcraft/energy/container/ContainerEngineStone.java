/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.energy.tile.TileEngineStone;

import buildcraft.BCEnergyRegistries;

/**
 * Slot layout, unchanged from 1.12.2 -- see the 26.x copy of this class for the full layout description. This
 * file differs only in how the client-side menu is reconstructed: {@code NetworkHooks.openScreen} already writes
 * the tile's {@code BlockPos} for the client automatically (see {@code BlockEngineStone#use}), so the
 * {@link FriendlyByteBuf} factory constructor below just reads it straight back, with no
 * {@code writeClientSideData} override needed on the tile the way 26.x needs.
 */
public class ContainerEngineStone extends ContainerBCTile<TileEngineStone> {
    private final DataSlot fuelPercent;

    public ContainerEngineStone(int windowId, Inventory playerInv, TileEngineStone tile) {
        super(BCEnergyRegistries.ENGINE_STONE_MENU.get(), windowId, tile);

        addSlot(new SlotBase(tile.invFuel, 0, 80, 41));
        addFullPlayerInventory(playerInv, 84);

        fuelPercent = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return tile.getFuelPercentForSync();
            }

            @Override
            public void set(int value) {
                tile.setFuelPercentForSync(value);
            }
        });
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerEngineStone(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileEngineStone lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileEngineStone tile) {
            return tile;
        }
        throw new IllegalStateException("No Stirling Engine tile at " + pos);
    }

    /** @return The current fuel-remaining level, from 0 to 1. */
    public float getFuelLevel() {
        return fuelPercent.get() / 100f;
    }
}
