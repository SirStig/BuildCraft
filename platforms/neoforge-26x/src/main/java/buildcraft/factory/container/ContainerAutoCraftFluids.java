/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
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
import buildcraft.factory.tile.TileAutoWorkbenchFluids;

import buildcraft.BCFactoryRegistries;

/**
 * Same slot-layout shape as {@link ContainerAutoCraftItems}, just over {@link TileAutoWorkbenchFluids}'s 2x2
 * blueprint grid (4 slots) instead of the items variant's 3x3 (9 slots) -- see that class's own javadoc for the
 * full layout rationale, the dropped filter-overlay icons, and the progress {@link DataSlot}. The two tanks
 * themselves are not exposed as container slots (fluid tanks aren't real inventory slots the way item slots are);
 * {@link buildcraft.factory.gui.GuiAutoCraftFluids} reads {@link #tile}'s {@code tank1}/{@code tank2} fields
 * directly instead, which already arrive on the client for free through {@link TileAutoWorkbenchFluids}'
 * inherited full-NBT {@code getUpdateTag}/{@code markDirtyAndSync} sync -- no extra {@link DataSlot} plumbing
 * needed just to mirror tank contents across the network.
 */
public class ContainerAutoCraftFluids extends ContainerBCTile<TileAutoWorkbenchFluids> {
    public final SlotBase[] materialSlots;
    private final DataSlot progress;

    public ContainerAutoCraftFluids(MenuType<?> type, int windowId, Inventory playerInv, TileAutoWorkbenchFluids tile) {
        super(type, windowId, tile);

        addSlot(new SlotOutput(tile.invResult, 0, 124, 35));
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                addSlot(new SlotPhantom(tile.invBlueprint, x + y * 2, 30 + x * 18, 17 + y * 18, false));
            }
        }
        materialSlots = new SlotBase[4];
        for (int x = 0; x < 4; x++) {
            // Hides the filter slots, but they're still synced -- exactly like ContainerAutoCraftItems.
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

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape -- see
     * {@link ContainerAutoCraftItems}'s own copy of this constructor for why. */
    public ContainerAutoCraftFluids(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(BCFactoryRegistries.AUTO_WORKBENCH_FLUIDS_MENU.get(), windowId, playerInv,
            lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileAutoWorkbenchFluids lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileAutoWorkbenchFluids tile) {
            return tile;
        }
        throw new IllegalStateException("No auto-workbench (fluids) tile at " + pos);
    }

    /** @return The current craft progress, from 0 to 1. */
    public float getProgress() {
        return (float) progress.get() / TileAutoWorkbenchBase.POWER_REQUIRED;
    }
}
