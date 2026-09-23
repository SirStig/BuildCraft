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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.minecraftforge.fluids.FluidStack;

import buildcraft.lib.fluid.Tank;
import buildcraft.lib.gui.ContainerBCTile;

import buildcraft.energy.tile.TileEngineIron;

import buildcraft.BCEnergyRegistries;

/**
 * The Combustion Engine's menu (1.12.2's {@code ContainerEngineIron_BC8}) -- see the 26.x copy for the layout, the
 * tank-data sync and the tank-click/shift-click handling, all of which are identical here.
 */
public class ContainerEngineIron extends ContainerBCTile<TileEngineIron> {
    private static final int DATA_COUNT = 6;

    private final ContainerData tankData;

    public ContainerEngineIron(int windowId, Inventory playerInv, TileEngineIron tile) {
        this(windowId, playerInv, tile, new TankData(tile));
    }

    private ContainerEngineIron(int windowId, Inventory playerInv, TileEngineIron tile, ContainerData data) {
        super(BCEnergyRegistries.ENGINE_IRON_MENU.get(), windowId, tile);
        addFullPlayerInventory(playerInv, 95);
        checkContainerDataCount(data, DATA_COUNT);
        this.tankData = data;
        addDataSlots(data);
    }

    /** Client-side factory constructor, matching {@code IContainerFactory<T>}'s shape. */
    public ContainerEngineIron(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(windowId, playerInv, lookupTile(playerInv, extraData.readBlockPos()), new SimpleContainerData(DATA_COUNT));
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
            FluidStack fluid = tank.getFluid();
            if (index % 2 == 0) {
                return BuiltInRegistries.FLUID.getId(fluid.isEmpty() ? Fluids.EMPTY : fluid.getFluid());
            }
            return fluid.isEmpty() ? 0 : fluid.getAmount();
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
