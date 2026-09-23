/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.container;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.gui.ContainerBCTile;

import buildcraft.energy.tile.TileEngineIron;

import buildcraft.BCEnergyRegistries;

/**
 * Renamed from 1.12.2's {@code ContainerEngineIron_BC8}: no machine slots at all, just the player inventory at
 * y = 95 and the three tanks.
 *
 * <p>1.12.2's three {@code WidgetFluidTank}s did two jobs, both done with plain vanilla menu mechanisms here:
 * <ul>
 * <li><b>Showing the tanks.</b> Six {@link ContainerData} values -- each tank's fluid registry id and amount, in
 *     {@link TileEngineIron#getTank(int)} order -- replace the widgets' network sync; see {@code TileEngineIron}'s
 *     javadoc for why the tanks are not synced through the tile itself. {@link #getClientFluid(int)} rebuilds the
 *     stack on the client.</li>
 * <li><b>Clicking a tank</b> with an item on the cursor. The widget's {@code NET_CLICK} message becomes vanilla's
 *     menu-button packet ({@code MultiPlayerGameMode#handleInventoryButtonClick}, which ends in
 *     {@link #clickMenuButton} on the server and is followed by {@code broadcastChanges}), with the tank index as the
 *     button id.</li>
 * </ul>
 * {@link #quickMoveStack} is 1.12.2's {@code transferStackInSlot}: a shift-clicked stack is offered to the fuel,
 * coolant and residue tanks in turn (one item per click, as before), and never moved between inventory slots.
 */
public class ContainerEngineIron extends ContainerBCTile<TileEngineIron> {
    private static final int DATA_COUNT = 6;

    private final ContainerData tankData;

    public ContainerEngineIron(MenuType<?> type, int windowId, Inventory playerInv, TileEngineIron tile) {
        this(type, windowId, playerInv, tile, new TankData(tile));
    }

    private ContainerEngineIron(MenuType<?> type, int windowId, Inventory playerInv, TileEngineIron tile, ContainerData data) {
        super(type, windowId, tile);
        addFullPlayerInventory(playerInv, 95);
        checkContainerDataCount(data, DATA_COUNT);
        this.tankData = data;
        addDataSlots(data);
    }

    /** Client-side factory constructor -- see {@code ContainerEngineStone}'s. */
    public ContainerEngineIron(int windowId, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        this(BCEnergyRegistries.ENGINE_IRON_MENU.get(), windowId, playerInv,
            lookupTile(playerInv, extraData.readBlockPos()), new SimpleContainerData(DATA_COUNT));
    }

    private static TileEngineIron lookupTile(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TileEngineIron tile) {
            return tile;
        }
        throw new IllegalStateException("No Combustion Engine tile at " + pos);
    }

    /** @return The synced contents of tank {@code index} (fuel, coolant, residue). */
    public FluidStack getClientFluid(int index) {
        int amount = tankData.get(index * 2 + 1);
        Fluid fluid = BuiltInRegistries.FLUID.byId(tankData.get(index * 2));
        if (fluid == null || fluid == Fluids.EMPTY || amount <= 0) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluid, amount);
    }

    public int getTankCapacity() {
        return TileEngineIron.MAX_FLUID;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (tile.getTank(buttonId) == null) {
            return false;
        }
        tile.onGuiClicked(buttonId, player, this);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // The only slots are player slots -- try to interact with all of the tanks
        if (!player.level().isClientSide() && index >= 0 && index < slots.size()) {
            Slot slot = slots.get(index);
            ItemStack original = slot.getItem();
            if (!original.isEmpty()) {
                for (int i = 0; i < 3; i++) {
                    ItemStack after = tile.transferStackToTank(tile.getTank(i), player, original);
                    if (!ItemStack.matches(after, original)) {
                        slot.set(after);
                        broadcastChanges();
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Server side: reads the live tanks. */
    private static final class TankData implements ContainerData {
        private final TileEngineIron tile;

        TankData(TileEngineIron tile) {
            this.tile = tile;
        }

        @Override
        public int get(int index) {
            Tank tank = tile.getTank(index / 2);
            if (tank == null) {
                return 0;
            }
            if (index % 2 == 0) {
                return BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid());
            }
            return tank.getAmountAsInt(0);
        }

        @Override
        public void set(int index, int value) {
            // Server side only ever reads.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
