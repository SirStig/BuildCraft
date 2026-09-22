/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.slot;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.world.inventory.StackCopySlot;

import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * A GUI slot bound to one index of an {@link ItemHandlerSimple}.
 *
 * <p>1.12.2's {@code SlotBase} extended Forge's {@code SlotItemHandler}, wrapping an {@code IItemHandler}.
 * Neither {@code IItemHandler} nor {@code SlotItemHandler} exist on this target any more (see
 * {@link ItemHandlerSimple}'s own javadoc) -- NeoForge's replacement, {@code ResourceHandlerSlot}, wraps a
 * {@code ResourceHandler<ItemResource>} through an {@code IndexModifier}, but since every BuildCraft inventory
 * this GUI layer touches is concretely an {@link ItemHandlerSimple} (never just any {@code ResourceHandler}),
 * this extends NeoForge's own {@code StackCopySlot} directly and reads/writes the handler's own
 * {@code getStackInSlot}/{@code setStackInSlot} pair instead -- one less layer of indirection than wiring up a
 * {@code ResourceHandlerSlot} + {@code IndexModifier} would need, and consistent with this port's established
 * preference for talking to its own concrete handler types directly where a shortcut is equally correct (see
 * {@link ItemHandlerSimple}'s own javadoc, and {@code TileTank} implementing {@code ResourceHandler} itself
 * rather than being wrapped).
 *
 * <p>{@code StackCopySlot} already supplies the "no real backing {@code Container}" plumbing 1.12.2's
 * {@code SlotDisplay} hand-rolled with an {@code InventoryBasic} stand-in (see {@link SlotDisplay}) -- its
 * constructor takes only a slot index and position, and {@link #getItem()}/{@link #set(ItemStack)} route through
 * the abstract {@link #getStackCopy()}/{@link #setStackCopy(ItemStack)} pair implemented here.
 */
public class SlotBase extends StackCopySlot {
    public final int handlerIndex;
    public final ItemHandlerSimple itemHandler;

    public SlotBase(ItemHandlerSimple itemHandler, int slotIndex, int posX, int posY) {
        super(slotIndex, posX, posY);
        this.handlerIndex = slotIndex;
        this.itemHandler = itemHandler;
    }

    @Override
    protected ItemStack getStackCopy() {
        return itemHandler.getStackInSlot(handlerIndex);
    }

    @Override
    protected void setStackCopy(ItemStack stack) {
        itemHandler.setStackInSlot(handlerIndex, stack);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return itemHandler.isValid(handlerIndex, ItemResource.of(stack));
    }

    @Override
    public int getMaxStackSize() {
        ItemStack current = getItem();
        ItemResource resource = current.isEmpty() ? ItemResource.EMPTY : ItemResource.of(current);
        return Math.max(1, itemHandler.getCapacityAsInt(handlerIndex, resource));
    }
}
