/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.slot.IPhantomSlot;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.TileBC;

/**
 * Base class for every BuildCraft tile-bound menu -- the first GUI/container foundation this port has needed
 * (see {@code buildcraft.factory.tile.TileAutoWorkbenchItems}'s own javadoc for why the auto-workbench is what
 * finally forced this). Mirrors the 26.x class of the same name; see that one's javadoc for why 1.12.2's
 * {@code ContainerBCTile}/348-line {@code ContainerBC_Neptune} aren't ported line-for-line.
 *
 * <p>The one real difference from the 26.x copy: this target's {@link #clicked} still takes the classic
 * {@link ClickType} (confirmed via {@code javap} -- {@code ContainerInput} is a 26.x-only rename, not a general
 * modern-Minecraft rename), also still returning {@code void} rather than 1.12.2's {@code ItemStack} -- the
 * cursor stack lives in {@link #getCarried()}/{@link #setCarried} on this target too, same as 26.x.
 */
public abstract class ContainerBCTile<T extends TileBC> extends AbstractContainerMenu {
    public final T tile;

    /** The number of slots added before {@link #addFullPlayerInventory} was called -- the boundary
     * {@link #quickMoveStack} merges across. {@code -1} until that call happens. */
    private int machineSlotCount = -1;

    protected ContainerBCTile(MenuType<?> type, int windowId, T tile) {
        super(type, windowId);
        this.tile = tile;
    }

    /** Adds the player's 3x9 main inventory plus hotbar, at the standard x offset. Must be called last, after
     * every machine-owned slot has already been added, since it also records {@link #machineSlotCount}. */
    protected void addFullPlayerInventory(Inventory playerInv, int startY) {
        machineSlotCount = slots.size();
        for (int sy = 0; sy < 3; sy++) {
            for (int sx = 0; sx < 9; sx++) {
                addSlot(new Slot(playerInv, sx + sy * 9 + 9, 8 + sx * 18, startY + sy * 18));
            }
        }
        for (int sx = 0; sx < 9; sx++) {
            addSlot(new Slot(playerInv, sx, 8 + sx * 18, startY + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(tile, player);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof IPhantomSlot phantom) {
            Slot slot = slots.get(slotId);
            ItemStack carried = getCarried();
            ItemStack inSlot = slot.getItem();
            if (carried.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else if (inSlot.isEmpty() || !StackUtil.canMerge(carried, inSlot)) {
                ItemStack copy = carried.copy();
                copy.setCount(1);
                slot.set(copy);
            } else if (phantom.canAdjustCount() && inSlot.getCount() < slot.getMaxStackSize()) {
                inSlot.grow(1);
                slot.set(inSlot);
            }
            // The carried stack is never touched -- phantom slots don't consume it.
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (machineSlotCount < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack inSlot = slot.getItem();
            result = inSlot.copy();
            if (index < machineSlotCount) {
                if (!moveItemStackTo(inSlot, machineSlotCount, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(inSlot, 0, machineSlotCount, false)) {
                return ItemStack.EMPTY;
            }
            if (inSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }
}
