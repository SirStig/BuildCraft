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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.slot.IPhantomSlot;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.TileBC;

/**
 * Base class for every BuildCraft tile-bound menu -- the first GUI/container foundation this port has needed
 * (see {@code buildcraft.factory.tile.TileAutoWorkbenchItems}'s own javadoc for why the auto-workbench is what
 * finally forced this).
 *
 * <p>Deliberately not a port of 1.12.2's {@code ContainerBCTile}/348-line {@code ContainerBC_Neptune}. That pair
 * existed mostly to carry BuildCraft's own network layer on top of the classic container API -- an
 * {@code IdAllocator}-tagged {@code MessageContainer} payload, a {@code Widget_Neptune} registry, and a hand-built
 * phantom-slot-setting packet ({@code NET_SET_PHANTOM}/{@code NET_SET_PHANTOM_MULTI}). None of that has a reason
 * to exist any more: modern {@link AbstractContainerMenu} already diffs and pushes every slot's {@link Slot#getItem}
 * on every {@link #broadcastChanges()} tick, and {@link #addDataSlot} gives a single auto-synced {@code int} for
 * free (see {@code buildcraft.factory.container.ContainerAutoCraftItems}'s own progress field for the concrete
 * use). What is kept is the two pieces of real *behaviour* 1.12.2's classes had beyond that plumbing:
 *
 * <ul>
 * <li><b>Phantom-slot clicking</b> ({@link #clicked}). A click on an {@link IPhantomSlot} sets its content to
 *     match the cursor's item (or increments its count) without ever consuming the cursor stack -- ported from
 *     {@code ContainerBC_Neptune#slotClick}, rebuilt against this target's {@code clicked(int, int,
 *     ContainerInput, Player)} (note {@link ContainerInput} replaces the classic {@code ClickType} on this
 *     target specifically -- confirmed via {@code javap}, see PORTING.md's divergence table) which returns
 *     {@code void} rather than the old {@code ItemStack}, since the cursor stack lives in
 *     {@link #getCarried()}/{@link #setCarried} now instead of being threaded through as a return value.</li>
 * <li><b>Shift-click merging</b> ({@link #quickMoveStack}). A straight two-region merge (machine slots, then
 *     player inventory, or the reverse depending on which side the click started in), exactly
 *     {@code ContainerBC_Neptune#transferStackInSlot}'s shape -- {@link #moveItemStackTo} (the modern rename of
 *     {@code mergeItemStack}) already respects {@link Slot#mayPlace}, so a phantom/output/display slot is
 *     automatically skipped without this class needing to filter them out itself.</li>
 * </ul>
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
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
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
