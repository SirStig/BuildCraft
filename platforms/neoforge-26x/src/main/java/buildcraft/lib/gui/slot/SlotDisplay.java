/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.slot;

import java.util.function.IntFunction;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.world.inventory.StackCopySlot;

/**
 * A read-only slot that mirrors whatever {@code getter} returns -- used to show the assumed result of a recipe
 * before it has actually been crafted. Never takeable, never insertable; {@link #setStackCopy} is a no-op since
 * nothing this slot shows is ever really "in" it.
 *
 * <p>1.12.2's version stood up a throwaway {@code InventoryBasic} to satisfy the classic {@code Slot} constructor.
 * {@code StackCopySlot} (see {@link SlotBase}'s own javadoc) needs no backing {@code Container} at all, so that
 * workaround has nothing left to do here.
 *
 * <p>No custom network payload is needed to keep this in sync either: {@code AbstractContainerMenu
 * #broadcastChanges()} already diffs every slot's {@link #getItem()} against what it last sent every tick, so a
 * display slot whose {@link #getStackCopy()} reads a live, server-authoritative value is automatically pushed to
 * the client whenever it changes -- 1.12.2's own {@code NET_GUI_DATA} payload had to do this by hand.
 */
public class SlotDisplay extends StackCopySlot {
    private final IntFunction<ItemStack> getter;

    public SlotDisplay(IntFunction<ItemStack> getter, int index, int xPosition, int yPosition) {
        super(index, xPosition, yPosition);
        this.getter = getter;
    }

    @Override
    protected ItemStack getStackCopy() {
        return getter.apply(getSlotIndex()).copy();
    }

    @Override
    protected void setStackCopy(ItemStack stack) {
        // No-op: this slot never really holds anything of its own.
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return getItem().getCount();
    }
}
