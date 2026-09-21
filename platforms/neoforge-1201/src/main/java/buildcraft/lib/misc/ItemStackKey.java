/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import org.jetbrains.annotations.NotNull;

import net.minecraft.world.item.ItemStack;

/**
 * A copy-safe map key wrapping an {@link ItemStack}, comparing by item and NBT rather than by reference.
 *
 * <p>See the 26.x copy for why this changed from the 1.12.2 original -- item damage is no longer a subtype
 * discriminator to compare, and {@link ItemStack#isSameItemSameTags} is the 1.20.1 spelling of "same item, same
 * NBT" this target uses in its place.
 */
public final class ItemStackKey {
    public static final ItemStackKey EMPTY = new ItemStackKey(StackUtil.EMPTY);

    public final @NotNull ItemStack baseStack;
    private final int hash;

    public ItemStackKey(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            baseStack = StackUtil.EMPTY;
            hash = 0;
        } else {
            this.baseStack = stack.copy();
            this.hash = StackUtil.hash(baseStack);
        }
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        ItemStackKey other = (ItemStackKey) obj;
        if (hash != other.hash) return false;
        return ItemStack.isSameItemSameTags(baseStack, other.baseStack);
    }

    @Override
    public String toString() {
        return "[ItemStackKey " + baseStack + "]";
    }
}
