/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.gui.slot.SlotPhantom;

import buildcraft.factory.tile.TileAutoWorkbenchBase;

import buildcraft.BCFactoryRegistries;

/**
 * Slot layout, unchanged from 1.12.2: 1 output slot (124, 35), the 3x3 phantom blueprint grid (30 + x*18,
 * 17 + y*18), 9 hidden-but-still-synced phantom filter slots (auto-derived by
 * {@link TileAutoWorkbenchBase#serverTick()}, off-screen at (-1000000, -1000000) the same way 1.12.2 hid them),
 * the 9 real material slots (8 + x*18, 84), 1 display slot for the assumed result (93, 27), and the full player
 * inventory at y = 115.
 *
 * <p><b>The filter-overlay icons 1.12.2 drew on the material slots ({@code ICON_FILTER_OVERLAY_SAME/DIFFERENT/
 * SIMILAR}) are not ported.</b> They were a pure rendering nicety on top of {@code GuiAutoCraftItems} -- a
 * material slot never behaves differently depending on the overlay, it is just a hint about how closely that
 * slot's item matches its filter -- and are dropped along with the rest of the polish this task's scope
 * deliberately leaves out (see also the recipe-book drop on {@code TileAutoWorkbenchBase}).
 *
 * <p>The progress bar's value is a single container {@link DataSlot} rather than 1.12.2's id-tagged
 * {@code NET_GUI_TICK} payload -- see {@link TileAutoWorkbenchBase#getPowerStoredForSync()}'s own javadoc for why
 * that's enough on this target.
 */
public class ContainerAutoCraftItems extends ContainerBCTile<TileAutoWorkbenchBase> {
    public final SlotBase[] materialSlots;
    private final DataSlot progress;

    public ContainerAutoCraftItems(MenuType<?> type, int windowId, Inventory playerInv, TileAutoWorkbenchBase tile) {
        super(type, windowId, tile);

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

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape -- reads back the
     * {@code BlockPos} {@link TileAutoWorkbenchBase#writeClientSideData} wrote on open, and looks the tile up
     * again from the client's own level. */
    public ContainerAutoCraftItems(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(BCFactoryRegistries.AUTO_WORKBENCH_ITEMS_MENU.get(), windowId, playerInv,
            lookupTile(playerInv, extraData.readBlockPos()));
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
