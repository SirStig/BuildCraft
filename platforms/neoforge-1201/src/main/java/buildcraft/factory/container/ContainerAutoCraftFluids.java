/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
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
import buildcraft.factory.tile.TileAutoWorkbenchFluids;

import buildcraft.BCFactoryRegistries;

/**
 * Mirrors the 26.x copy of this class -- see that one's own javadoc for the full layout rationale and why the
 * two tanks aren't exposed as container slots. This file differs only in the usual 1.20.1 places: the
 * {@link FriendlyByteBuf} client-side factory constructor reads the {@code BlockPos} {@code NetworkHooks.openScreen}
 * already wrote automatically (see {@code BlockAutoWorkbenchFluids#use}), exactly like
 * {@link ContainerAutoCraftItems}'s own copy of this constructor.
 */
public class ContainerAutoCraftFluids extends ContainerBCTile<TileAutoWorkbenchFluids> {
    public final SlotBase[] materialSlots;
    private final DataSlot progress;

    public ContainerAutoCraftFluids(int windowId, Inventory playerInv, TileAutoWorkbenchFluids tile) {
        super(BCFactoryRegistries.AUTO_WORKBENCH_FLUIDS_MENU.get(), windowId, tile);

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

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerAutoCraftFluids(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
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
