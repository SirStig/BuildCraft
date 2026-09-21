/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.recipe;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.misc.StackUtil;

/** Defines an {@link ItemStack} that changes between a specified list of stacks. Useful for displaying possible
 * inputs or outputs for recipes that vary (for example a pipe colouring recipe).
 *
 * <p>Stores an {@link ItemStackKey} per option, so displaying-the-same-item-with-different-counts collapses to
 * one entry and equality/hashing ignores stack size -- unchanged from 1.12.2. */
public final class ChangingItemStack extends ChangingObject<ItemStackKey> {
    /** Creates a stack list that iterates through all of the given stacks. This does NOT check possible variants.
     *
     * @param stacks The list to iterate through. */
    public ChangingItemStack(NonNullList<ItemStack> stacks) {
        super(makeListArray(stacks));
    }

    public ChangingItemStack(@NotNull Ingredient ingredient) {
        super(makeIngredientArray(ingredient));
    }

    public ChangingItemStack(ItemStack stack) {
        super(makeStackArray(stack));
    }

    private static ItemStackKey[] makeListArray(NonNullList<ItemStack> stacks) {
        return makeStackArray(stacks.toArray(new ItemStack[0]));
    }

    /** 1.12.2 also special-cased a stack whose damage was {@code OreDictionary.WILDCARD_VALUE}, expanding it to
     * every sub-item via {@code Item.getSubItems}. Item subtypes via damage are gone (see the class javadoc on
     * {@link StackUtil}), so there is nothing left for that branch to expand -- every stack here is already
     * exactly one item. */
    private static ItemStackKey[] makeStackArray(ItemStack stack) {
        if (stack.isEmpty()) {
            return new ItemStackKey[] { ItemStackKey.EMPTY };
        }
        return new ItemStackKey[] { new ItemStackKey(stack) };
    }

    private static ItemStackKey[] makeIngredientArray(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.items().map(ItemStack::new).toArray(ItemStack[]::new);
        return makeStackArray(stacks);
    }

    private static ItemStackKey[] makeStackArray(ItemStack[] stacks) {
        if (stacks.length == 0) {
            return new ItemStackKey[] { ItemStackKey.EMPTY };
        } else {
            ItemStackKey[] arr = new ItemStackKey[stacks.length];
            for (int i = 0; i < stacks.length; i++) {
                arr[i] = new ItemStackKey(stacks[i]);
            }
            return arr;
        }
    }

    public boolean matches(ItemStack target) {
        for (ItemStackKey s : options) {
            if (StackUtil.isCraftingEquivalent(s.baseStack, target, false)) {
                return true;
            }
        }
        return false;
    }

}
