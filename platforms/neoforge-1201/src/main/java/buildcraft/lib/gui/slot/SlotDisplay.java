/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.slot;

import java.util.function.IntFunction;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A read-only slot that mirrors whatever {@code getter} returns -- used to show the assumed result of a recipe
 * before it has actually been crafted. Never takeable, never insertable.
 *
 * <p>No custom network payload is needed to keep this in sync: {@code AbstractContainerMenu#broadcastChanges()}
 * already diffs every slot's {@link #getItem()} against what it last sent every tick, so a display slot whose
 * {@link #getItem()} reads a live, server-authoritative value is automatically pushed to the client whenever it
 * changes -- 1.12.2's own {@code NET_GUI_DATA} payload had to do this by hand. */
public class SlotDisplay extends Slot {
    private static final SimpleContainer EMPTY_INVENTORY = new SimpleContainer(0);
    private final IntFunction<ItemStack> getter;

    public SlotDisplay(IntFunction<ItemStack> getter, int index, int xPosition, int yPosition) {
        super(EMPTY_INVENTORY, index, xPosition, yPosition);
        this.getter = getter;
    }

    @Override
    public void onTake(Player player, @NotNull ItemStack stack) {
        // No-op: this slot never really holds anything of its own.
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public @NotNull ItemStack getItem() {
        return getter.apply(getSlotIndex()).copy();
    }

    @Override
    public void set(@NotNull ItemStack stack) {
        // No-op: this slot never really holds anything of its own.
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public @NotNull ItemStack remove(int amount) {
        return getItem();
    }

    @Override
    public int getMaxStackSize() {
        return getItem().getCount();
    }
}
