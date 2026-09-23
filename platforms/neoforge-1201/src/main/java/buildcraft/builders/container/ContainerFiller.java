/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.builders.tile.TileFiller;

import buildcraft.BCBuildersRegistries;

/** Mirrors the 26.x class of the same name -- see that one's javadoc for why the original drag-and-drop pattern
 * GUI is replaced by plain menu-button clicks here. */
public class ContainerFiller extends ContainerBCTile<TileFiller> {
    public static final int BUTTON_PATTERN = 0;
    public static final int BUTTON_PARAM_0 = 1;
    public static final int BUTTON_PARAM_1 = 2;
    public static final int BUTTON_PARAM_2 = 3;
    public static final int BUTTON_INVERT = 4;
    public static final int BUTTON_EXCAVATE = 5;
    public static final int BUTTON_ENABLED = 6;

    public ContainerFiller(MenuType<?> type, int windowId, Inventory playerInv, TileFiller tile) {
        super(type, windowId, tile);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new SlotBase(tile.invResources, x + y * 9, 8 + x * 18, 64 + y * 18));
            }
        }
        addFullPlayerInventory(playerInv, 126);
    }

    /** Client-side factory constructor; the position is the one {@code NetworkHooks.openScreen} writes. */
    public ContainerFiller(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(BCBuildersRegistries.FILLER_MENU.get(), windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileFiller lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileFiller tile) {
            return tile;
        }
        throw new IllegalStateException("No filler tile at " + pos);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        switch (buttonId) {
            case BUTTON_PATTERN -> tile.cyclePattern();
            case BUTTON_PARAM_0 -> tile.cycleParam(0);
            case BUTTON_PARAM_1 -> tile.cycleParam(1);
            case BUTTON_PARAM_2 -> tile.cycleParam(2);
            case BUTTON_INVERT -> tile.toggleInverted();
            case BUTTON_EXCAVATE -> tile.toggleExcavate();
            case BUTTON_ENABLED -> tile.toggleEnabled();
            default -> {
                return false;
            }
        }
        return true;
    }
}
