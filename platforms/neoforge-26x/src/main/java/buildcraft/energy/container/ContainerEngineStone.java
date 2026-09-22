/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.energy.tile.TileEngineStone;

import buildcraft.BCEnergyRegistries;

/**
 * Slot layout, unchanged from 1.12.2's {@code ContainerEngineStone_BC8}: one fuel slot at (80, 41), full player
 * inventory at y = 84. Much smaller than {@code ContainerAutoCraftItems} -- no phantom slots at all here, just
 * one real slot bound to {@link TileEngineStone#invFuel}.
 *
 * <p>The flame-level indicator's value is a single container {@link DataSlot} rather than 1.12.2's id-tagged
 * {@code deltaFuelLeft}/{@code DeltaInt} sync -- see {@link TileEngineStone#getFuelPercentForSync()}'s own
 * javadoc for why that's enough on this target, exactly like {@code ContainerAutoCraftItems}'s own progress bar.
 */
public class ContainerEngineStone extends ContainerBCTile<TileEngineStone> {
    private final DataSlot fuelPercent;

    public ContainerEngineStone(MenuType<?> type, int windowId, Inventory playerInv, TileEngineStone tile) {
        super(type, windowId, tile);

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

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape -- reads back the
     * {@code BlockPos} {@code TileEngineStone#writeClientSideData} wrote on open, and looks the tile up again
     * from the client's own level. */
    public ContainerEngineStone(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(BCEnergyRegistries.ENGINE_STONE_MENU.get(), windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
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
