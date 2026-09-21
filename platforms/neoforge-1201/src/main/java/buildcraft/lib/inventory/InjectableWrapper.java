/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.transport.IInjectable;

import buildcraft.lib.misc.StackUtil;

public class InjectableWrapper implements IItemTransactor {
    private final IInjectable injectable;
    private final Direction from;

    public InjectableWrapper(IInjectable injectable, Direction facing) {
        this.injectable = injectable;
        this.from = facing;
    }

    @NotNull
    @Override
    public ItemStack insert(@NotNull ItemStack stack, boolean allOrNone, boolean simulate) {
        if (allOrNone) {
            stack = stack.copy();
            ItemStack leftOver = injectable.injectItem(stack, false, from, null, 0);
            if (leftOver.isEmpty()) {
                ItemStack reallyLeftOver = injectable.injectItem(stack, !simulate, from, null, 0);
                // sanity check: it really helps debugging
                if (!reallyLeftOver.isEmpty()) {
                    throw new IllegalStateException("Found an invalid IInjectable instance! (leftOver = "//
                        + leftOver + ", reallyLeftOver = " + reallyLeftOver + ", " + injectable.getClass() + ")");
                } else {
                    return StackUtil.EMPTY;
                }
            } else {
                return stack;
            }
        } else {
            return injectable.injectItem(stack, !simulate, from, null, 0);
        }
    }

    // insert(NonNullList<ItemStack>, boolean) is left to IItemTransactor's default, which does exactly what
    // ItemTransactorHelper.insertAllBypass did in 1.12.2 -- no reason to duplicate it here.

    @NotNull
    @Override
    public ItemStack extract(IStackFilter filter, int min, int max, boolean simulate) {
        return StackUtil.EMPTY;
    }
}
