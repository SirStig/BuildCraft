/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.items.IList;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.api.recipes.StackDefinition;

/**
 * Provides various utils for interacting with {@link ItemStack}, and multiples.
 *
 * <p>Item subtypes via damage value ({@code Item.getHasSubtypes()}, {@code Block.getMetaFromState()}) and the
 * ore dictionary ({@code OreDictionary}) are both gone by 1.20.1 too -- they were removed going into 1.13, well
 * before either of this port's targets. See the 26.x copy of this file for the fuller explanation; the design
 * decisions are the same on both targets, only the API names differ (this target still has item NBT as a
 * {@link CompoundTag} rather than a data component, so {@link #isMatchingItem}'s NBT branch stays close to the
 * 1.12.2 original).
 */
public class StackUtil {

    /** A non-null version of {@link ItemStack#EMPTY}. */
    @NotNull
    public static final ItemStack EMPTY = ItemStack.EMPTY;

    /** Registry of additional rules for {@link #isMatchingItem}. */
    private static final Map<Item, List<StackMatchingPredicate>> matchingPredicates = new HashMap<>();

    private StackUtil() {
    }

    /**
     * Checks to see if the two input stacks are equal in all but stack size.
     *
     * <p>{@link ItemStack#isSameItemSameTags} is the 1.20.1 spelling of "same item, same NBT" -- the direct
     * successor of 1.12.2's {@code areItemsEqual}+{@code areItemStackTagsEqual} pair, which is why the two
     * calls collapse into this one call here just as they do on 26.x.
     */
    public static boolean canMerge(@NotNull ItemStack a, @NotNull ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }

    /**
     * Attempts to get an item stack that might place down the given blockstate. Obviously this isn't perfect,
     * and so cannot be relied on for anything more than simple blocks.
     */
    @NotNull
    public static ItemStack getItemStackForState(BlockState state) {
        Block b = state.getBlock();
        ItemStack stack = new ItemStack(b);
        if (stack.isEmpty()) {
            return StackUtil.EMPTY;
        }
        return stack;
    }

    /** Checks to see if the given required stack is contained fully in the given container stack. */
    public static boolean contains(@NotNull ItemStack required, @NotNull ItemStack container) {
        if (canMerge(required, container)) {
            return container.getCount() >= required.getCount();
        }
        return false;
    }

    /** Checks to see if the given required stack is contained fully in a single stack in a list. */
    public static boolean contains(@NotNull ItemStack required, Collection<ItemStack> containers) {
        for (ItemStack possible : containers) {
            if (possible == null) {
                throw new NullPointerException("Found a null itemstack in " + containers);
            }
            if (contains(required, possible)) {
                return true;
            }
        }
        return false;
    }

    /** Checks that passed stack meets stack definition requirements */
    public static boolean contains(@NotNull StackDefinition stackDefinition, @NotNull ItemStack stack) {
        return !stack.isEmpty() && stackDefinition.filter().matches(stack) && stack.getCount() >= stackDefinition.count();
    }

    /** Checks that passed stack definition acceptable for stack collection */
    public static boolean contains(@NotNull StackDefinition stackDefinition, @NotNull NonNullList<ItemStack> stacks) {
        return stacks.stream().anyMatch(stack -> contains(stackDefinition, stack));
    }

    /** Checks that passed stack meets stack definition requirements */
    public static boolean contains(@NotNull IngredientStack ingredientStack, @NotNull ItemStack stack) {
        return !stack.isEmpty() && ingredientStack.test(stack) && stack.getCount() >= ingredientStack.count();
    }

    /** Checks that passed stack definition acceptable for stack collection */
    public static boolean contains(@NotNull IngredientStack ingredientStack, @NotNull NonNullList<ItemStack> stacks) {
        return stacks.stream().anyMatch(stack -> contains(ingredientStack, stack));
    }

    /** Checks to see if the given required stacks are all contained within the collection of containers. Note
     * that this assumes that all of the required stacks are different. */
    public static boolean containsAll(Collection<ItemStack> required, Collection<ItemStack> containers) {
        for (ItemStack req : required) {
            if (req == null) {
                throw new NullPointerException("Found a null itemstack in " + containers);
            }
            if (req.isEmpty()) continue;
            if (!contains(req, containers)) {
                return false;
            }
        }
        return true;
    }

    public static CompoundTag stripNonFunctionNbt(@NotNull ItemStack from) {
        CompoundTag nbt = NBTUtilBC.getItemData(from).copy();
        if (nbt.isEmpty()) {
            return nbt;
        }
        nbt.remove("_data");
        // TODO: Remove all of the non functional stuff (name, desc, etc)
        return nbt;
    }

    public static boolean doesStackNbtMatch(@NotNull ItemStack target, @NotNull ItemStack with) {
        CompoundTag nbtTarget = stripNonFunctionNbt(target);
        CompoundTag nbtWith = stripNonFunctionNbt(with);
        return nbtTarget.equals(nbtWith);
    }

    /** @return The set of item tags {@code stack}'s item belongs to. */
    private static Set<TagKey<Item>> tagsOf(@NotNull ItemStack stack) {
        Set<TagKey<Item>> tags = new HashSet<>();
        stack.getTags().forEach(tags::add);
        return tags;
    }

    /** True if the two stacks' items share at least one item tag. The modern replacement for "do these two
     * items share an ore dictionary entry". */
    public static boolean shareAnyTag(@NotNull ItemStack a, @NotNull ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        Set<TagKey<Item>> tagsA = tagsOf(a);
        return b.getTags().anyMatch(tagsA::contains);
    }

    /** Replaces the 1.12.2 {@code OreDictionary.itemMatches(a, b, false) || OreDictionary.itemMatches(b, a, false)}
     * pair with a symmetric tag-membership check. See the 26.x copy for the full rationale. */
    public static boolean doesEitherStackMatch(@NotNull ItemStack stackA, @NotNull ItemStack stackB) {
        return ItemStack.isSameItem(stackA, stackB) || shareAnyTag(stackA, stackB);
    }

    public static boolean canStacksOrListsMerge(@NotNull ItemStack stack1, @NotNull ItemStack stack2) {
        if (stack1.isEmpty() || stack2.isEmpty()) {
            return false;
        }

        if (stack1.getItem() instanceof IList list) {
            return list.matches(stack1, stack2);
        } else if (stack2.getItem() instanceof IList list) {
            return list.matches(stack2, stack1);
        }

        return ItemStack.isSameItemSameTags(stack1, stack2);
    }

    /** This doesn't take into account stack sizes.
     *
     * @param filterOrList The exact itemstack to test, or an item that implements {@link IList} to test against.
     * @param test The stack to test for equality
     * @return True if they matched according to the above definitions, or false if theydidn't, or either was empty. */
    public static boolean matchesStackOrList(@NotNull ItemStack filterOrList, @NotNull ItemStack test) {
        if (filterOrList.isEmpty() || test.isEmpty()) {
            return false;
        }
        if (filterOrList.getItem() instanceof IList list) {
            return list.matches(filterOrList, test);
        }
        return canMerge(filterOrList, test);
    }

    /** Merges mergeSource into mergeTarget
     *
     * @param mergeSource - The stack to merge into mergeTarget, this stack is not modified
     * @param mergeTarget - The target merge, this stack is modified if doMerge is set
     * @param doMerge - To actually do the merge
     * @return The number of items that was successfully merged. */
    public static int mergeStacks(@NotNull ItemStack mergeSource, @NotNull ItemStack mergeTarget, boolean doMerge) {
        if (!canMerge(mergeSource, mergeTarget)) {
            return 0;
        }
        int mergeCount = Math.min(mergeTarget.getMaxStackSize() - mergeTarget.getCount(), mergeSource.getCount());
        if (mergeCount < 1) {
            return 0;
        }
        if (doMerge) {
            mergeTarget.setCount(mergeTarget.getCount() + mergeCount);
        }
        return mergeCount;
    }

    /* ITEM COMPARISONS */

    /** Determines whether the given ItemStack should be considered equivalent for crafting purposes.
     *
     * @param base The stack to compare to.
     * @param comparison The stack to compare.
     * @param tagMatch True to also accept a match by shared item tag (the {@code oreDictionary} parameter's
     *            replacement).
     * @return true if comparison should be considered a crafting equivalent for base. */
    public static boolean isCraftingEquivalent(@NotNull ItemStack base, @NotNull ItemStack comparison, boolean tagMatch) {
        if (isMatchingItem(base, comparison, true, false)) {
            return true;
        }
        return tagMatch && shareAnyTag(base, comparison);
    }

    public static boolean isMatchingItemOrList(final ItemStack base, final ItemStack comparison) {
        if (base.isEmpty() || comparison.isEmpty()) {
            return false;
        }

        if (base.getItem() instanceof IList list) {
            return list.matches(base, comparison);
        } else if (comparison.getItem() instanceof IList list) {
            return list.matches(comparison, base);
        }

        return isMatchingItem(base, comparison, true, true);
    }

    /** Compares item and NBT. Ignores damage entirely -- there is no longer a damage-based subtype for a
     * damage comparison to distinguish.
     *
     * @param base The stack to compare to.
     * @param comparison The stack to compare.
     * @return true if item and NBT match. */
    public static boolean isMatchingItem(final @NotNull ItemStack base, final @NotNull ItemStack comparison) {
        return isMatchingItem(base, comparison, true, true);
    }

    /** This variant also checks damage for damaged items. */
    public static boolean isEqualItem(final @NotNull ItemStack base, final @NotNull ItemStack comparison) {
        if (isMatchingItem(base, comparison, false, true)) {
            return isWildcard(base) || isWildcard(comparison) || base.getDamageValue() == comparison.getDamageValue();
        } else {
            return false;
        }
    }

    /** Compares item id, and optionally NBT.
     *
     * @param base ItemStack
     * @param comparison ItemStack
     * @param matchDamage Kept for signature compatibility with 1.12.2 callers; it is a no-op now. See the 26.x
     *            copy of this class for why.
     * @param matchNBT
     * @return true if matches */
    public static boolean isMatchingItem(
        @NotNull final ItemStack base,
        @NotNull final ItemStack comparison,
        @SuppressWarnings("unused") final boolean matchDamage,
        final boolean matchNBT
    ) {
        if (base.isEmpty() || comparison.isEmpty()) {
            return false;
        }

        if (base.getItem() != comparison.getItem()) {
            return false;
        }
        if (matchNBT) {
            CompoundTag baseTag = base.getTag();
            if (baseTag != null && !baseTag.equals(comparison.getTag())) {
                return false;
            }
        } else {
            List<StackMatchingPredicate> predicates = matchingPredicates.getOrDefault(base.getItem(), Collections.emptyList());
            for (StackMatchingPredicate predicate : predicates) {
                if (!predicate.isMatching(base, comparison)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Registers a predicate, that will be used in {@link #isMatchingItem} as an additional comparison rule.
     * If any of registered predicates will return false, then the function will also return false.
     * It can be helpful, if item stacks are clearly not the same, but have common {@link Item} instance.
     * @param forItem {@link Item} instance for which to register the rule.
     * @param predicate predicate to register.
     */
    public static void registerMatchingPredicate(@NotNull Item forItem, @NotNull StackMatchingPredicate predicate) {
        List<StackMatchingPredicate> predicates = matchingPredicates.computeIfAbsent(forItem, item -> new ArrayList<>());
        predicates.add(predicate);
    }

    /** Checks to see if the given {@link ItemStack} is considered to be a wildcard stack -- BuildCraft's own
     * sentinel damage value of -1, meaning "any damage state of this item". {@code OreDictionary.WILDCARD_VALUE}
     * is gone along with the rest of the ore dictionary; only BuildCraft's own sentinel survives. */
    public static boolean isWildcard(@NotNull ItemStack stack) {
        return isWildcard(stack.getDamageValue());
    }

    /** @param damage The damage to check
     * @return True if the damage is BuildCraft's own wildcard sentinel. */
    public static boolean isWildcard(int damage) {
        return damage == -1;
    }

    /** @return An empty, nonnull list that cannot be modified (as it cannot be expanded and it has a size of 0) */
    public static NonNullList<ItemStack> listOf() {
        return NonNullList.withSize(0, EMPTY);
    }

    /** Creates a {@link NonNullList} of {@link ItemStack}'s with the elements given in the order that they are given.
     *
     * @param stacks The stacks to put into a list
     * @return A {@link NonNullList} of all the given items. Note that the returned list of of a specified size, and
     *         cannot be expanded. */
    public static NonNullList<ItemStack> listOf(ItemStack... stacks) {
        switch (stacks.length) {
            case 0:
                return listOf();
            case 1:
                return NonNullList.withSize(1, stacks[0]);
            default:
        }
        NonNullList<ItemStack> list = NonNullList.withSize(stacks.length, EMPTY);
        for (int i = 0; i < stacks.length; i++) {
            list.set(i, stacks[i]);
        }
        return list;
    }

    /** Takes a {@link Nullable} {@link Object} and checks to make sure that it is really not null, like it is
     * everywhere else in the codebase.
     *
     * @param obj The (potentially) null object.
     * @return A non-null object, which will be the input object
     * @throws NullPointerException if the input object was actually null (Although this should never happen, this is
     *             more to catch bugs in dev.) */
    @NotNull
    public static <T> T asNonNull(@Nullable T obj) {
        if (obj == null) {
            throw new NullPointerException("Object was null!");
        }
        return obj;
    }

    @NotNull
    public static <T> T asNonNullSoft(@Nullable T obj, @NotNull T fallback) {
        if (obj == null) {
            return fallback;
        } else {
            return obj;
        }
    }

    @NotNull
    public static ItemStack asNonNullSoft(@Nullable ItemStack stack) {
        return asNonNullSoft(stack, EMPTY);
    }

    /** @return A {@link Collector} that will collect the input elements into a {@link NonNullList} */
    public static <E> Collector<E, ?, NonNullList<E>> nonNullListCollector() {
        return Collectors.toCollection(NonNullList::create);
    }

    /** Computes a hash code for the given {@link ItemStack}. This is based off of {@link ItemStack#save}, except
     * if {@link ItemStack#isEmpty()} returns true, in which case the hash will be 0. */
    public static int hash(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (!stack.hasTag()) {
            return Objects.hash(stack.getItem(), stack.getDamageValue());
        }
        return stack.save(new CompoundTag()).hashCode();
    }

    public static NonNullList<ItemStack> mergeSameItems(List<ItemStack> items) {
        NonNullList<ItemStack> stacks = NonNullList.create();
        for (ItemStack toAdd : items) {
            boolean found = false;
            for (ItemStack stack : stacks) {
                if (canMerge(stack, toAdd)) {
                    stack.grow(toAdd.getCount());
                    found = true;
                }
            }
            if (!found) {
                stacks.add(toAdd.copy());
            }
        }
        return stacks;
    }
}
