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
 * A copy-safe map key wrapping an {@link ItemStack}, comparing by item and components rather than by reference.
 *
 * <p>1.12.2's {@code equals} compared item, then item damage via {@code getMetadata()} (a subtype
 * discriminator that no longer exists -- see {@code StackUtil}'s class javadoc), then a full NBT comparison via
 * {@code serializeNBT()} (also gone). {@link ItemStack#isSameItemSameComponents} covers everything those three
 * checks did together: item identity, and every component -- which is where damage lives now too.
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
        return ItemStack.isSameItemSameComponents(baseStack, other.baseStack);
    }

    @Override
    public String toString() {
        return "[ItemStackKey " + baseStack + "]";
    }
}
