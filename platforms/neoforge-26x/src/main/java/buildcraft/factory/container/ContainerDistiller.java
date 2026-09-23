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
import net.minecraft.world.inventory.MenuType;

import buildcraft.lib.gui.ContainerBCTile;

import buildcraft.factory.tile.TileDistiller;

import buildcraft.BCFactoryRegistries;

/**
 * 1.12.2's {@code ContainerDistiller}: just the player inventory (at y 79) -- the three tanks are not slots. Their
 * contents, and the tile's {@code active} flag, reach the client through the tile's own full-NBT sync (the
 * {@link ContainerAutoCraftFluids} precedent), so the three 1.12.2 {@code WidgetFluidTank}s need no container-side
 * counterpart. <b>Not ported:</b> 1.12.2's {@code transferStackInSlot} override (shift-clicking a fluid container
 * in the player inventory emptied it into the input tank) and the widgets' click-to-fill/drain -- both relied on
 * {@code Tank#transferStackToTank}/{@code onGuiClicked}, which this port's {@code Tank} does not have (see its own
 * javadoc). Input arrives through the horizontal faces instead.
 */
public class ContainerDistiller extends ContainerBCTile<TileDistiller> {

    public ContainerDistiller(MenuType<?> type, int windowId, Inventory playerInv, TileDistiller tile) {
        super(type, windowId, tile);
        addFullPlayerInventory(playerInv, 79);
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerDistiller(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(BCFactoryRegistries.DISTILLER_MENU.get(), windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()));
    }

    private static TileDistiller lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileDistiller tile) {
            return tile;
        }
        throw new IllegalStateException("No distiller tile at " + pos);
    }
}
