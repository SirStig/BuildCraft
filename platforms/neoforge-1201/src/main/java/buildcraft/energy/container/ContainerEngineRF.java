/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.energy.tile.TileEngineRF;

import buildcraft.BCEnergyRegistries;

/**
 * Slot layout, unchanged from 1.12.2's {@code ContainerEngineRF}: four upgrade slots at y=44 -- see the 26.x copy
 * of this class for the RF-buffer {@code DataSlot} this mirrors.
 */
public class ContainerEngineRF extends ContainerBCTile<TileEngineRF> {
    private final DataSlot rfPercent;

    public ContainerEngineRF(int windowId, Inventory playerInv, TileEngineRF tile) {
        super(BCEnergyRegistries.ENGINE_RF_MENU.get(), windowId, tile);

        for (int slot = 0; slot < 4; slot++) {
            addSlot(new SlotBase(tile.invUpgrades, slot, 62 + 18 * slot, 44));
        }
        addFullPlayerInventory(playerInv, 95);

        rfPercent = addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return tile.getRfPercentForSync();
            }

            @Override
            public void set(int value) {
                tile.setRfPercentForSync(value);
            }
        });
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerEngineRF(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileEngineRF lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileEngineRF tile) {
            return tile;
        }
        throw new IllegalStateException("No RF Engine tile at " + pos);
    }

    /** @return The current RF-buffer level, from 0 to 1. */
    public float getRfLevel() {
        return rfPercent.get() / 100f;
    }
}
