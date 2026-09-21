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
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
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
 * <p>This is the file the port's "ore dictionary is gone" and "block metadata is gone" notes in PORTING.md
 * land on hardest. Three 1.12.2 concepts this class leaned on do not exist any more:
 *
 * <ul>
 * <li><b>The ore dictionary</b> ({@code OreDictionary}) is replaced by tags everywhere it appears here. Where
 *     1.12.2 asked "do these two items share an ore name", this asks "do these two items share an item tag" --
 *     see {@link #shareAnyTag}.</li>
 * <li><b>Item subtypes via damage value</b> ({@code Item.getHasSubtypes()}, {@code Block.getMetaFromState()})
 *     are gone: every distinct variant is its own {@link Item} now (the four ages of a wool block did not
 *     become "one wool item with sixteen damage values" going into 1.13, they were already separate items, and
 *     that pattern is now universal). Anything that branched on "does this item have subtypes, and if so
 *     compare damage" has that branch deleted outright, not translated -- there is nothing left for it to
 *     do.</li>
 * <li><b>Item NBT</b> is a data component ({@link DataComponentPatch}) now, not a {@link CompoundTag} hanging
 *     off the stack. See {@link #isMatchingItem} for how the NBT-equality check is preserved using it.</li>
 * </ul>
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
     * Checks to see if the two input stacks are equal in all but stack size. Note that this doesn't check
     * anything to do with stack size, so if you pass in two stacks of 64 cobblestone this will return true.
     *
     * <p>1.12.2 split this into {@code areItemsEqual} (item + damage) and {@code areItemStackTagsEqual} (NBT).
     * Damage is a component now, the same as everything else that used to live in NBT, so there is no longer a
     * meaningful line between the two halves -- {@link ItemStack#isSameItemSameComponents} covers both at
     * once, which is also what vanilla's own stack-merge logic uses for exactly this question.
     */
    public static boolean canMerge(@NotNull ItemStack a, @NotNull ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }

    /**
     * Attempts to get an item stack that might place down the given blockstate. Obviously this isn't perfect,
     * and so cannot be relied on for anything more than simple blocks.
     *
     * <p>The subtype branch from 1.12.2 -- one block, many damage values, one of which this state maps to --
     * has nothing to do here: a block with state-dependent drops or variants is a block with several distinct
     * item forms now, not one item with a computed damage value, and {@code new ItemStack(block)} already
     * gets the right one because {@code state.getBlock()} already is the specific variant.
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
                // Use an explicit null check here as the collection doesn't have @NotNull applied to its type
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
        // NBTUtilBC.getItemData already hands back a detached copy on this target, but copying again here
        // costs nothing and keeps this method's own contract (never hand back something the caller could use
        // to mutate the stack) independent of that implementation detail.
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
        stack.typeHolder().tags().forEach(tags::add);
        return tags;
    }

    /** True if the two stacks' items share at least one item tag. The modern replacement for "do these two
     * items share an ore dictionary entry". */
    public static boolean shareAnyTag(@NotNull ItemStack a, @NotNull ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        Set<TagKey<Item>> tagsA = tagsOf(a);
        return b.typeHolder().tags().anyMatch(tagsA::contains);
    }

    /**
     * Replaces the 1.12.2 {@code OreDictionary.itemMatches(a, b, false) || OreDictionary.itemMatches(b, a, false)}
     * pair. Ore-dictionary matching was not perfectly symmetric in 1.12.2; tag membership is a set intersection
     * and already is, so a single symmetric check replaces both calls.
     */
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

        return ItemStack.isSameItemSameComponents(stack1, stack2);
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
     *            replacement -- see {@link #shareAnyTag}).
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

    /** Compares item and NBT. Ignores damage entirely -- see the class javadoc for why there is no longer
     * anything for a damage comparison to do.
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
     * @param matchDamage Kept for signature compatibility with 1.12.2 callers; it is a no-op now. It used to
     *            gate a damage-as-subtype comparison, and there is no longer such a thing as a damage-based
     *            subtype to compare (see the class javadoc).
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
            // The 1.12.2 check only failed the match when *base* carried a tag, and otherwise didn't care what
            // comparison's tag held -- an asymmetric "if base asked for something specific, comparison must
            // provide it" rule, not a plain equality check. getComponentsPatch() (the explicit overrides on
            // top of the item's defaults) is the modern analogue of "the extra NBT this stack was given", so
            // the same asymmetric rule is reproduced against it rather than against the full component map,
            // which would also compare every inherent default the item already has.
            DataComponentPatch basePatch = base.getComponentsPatch();
            if (!basePatch.isEmpty() && !basePatch.equals(comparison.getComponentsPatch())) {
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

    /** Checks to see if the given {@link ItemStack} is considered to be a wildcard stack -- a sentinel damage
     * value of -1 that BuildCraft's own filter/list code uses to mean "any damage state of this item". This is
     * BuildCraft's own convention, not the ore dictionary's -- {@code OreDictionary.WILDCARD_VALUE} is gone
     * along with the rest of the ore dictionary, and was a different (if related) idea: "any item registered
     * under this ore name, regardless of the damage they're registered with". Only the BuildCraft sentinel
     * survives, since there is no ore dictionary left for the other one to mean anything about. */
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

    /**
     * Computes a hash code for the given {@link ItemStack}.
     *
     * <p>1.12.2 branched on whether the stack had a tag compound at all, using {@code Objects.hash(item,
     * metadata)} when it didn't and {@code serializeNBT().hashCode()} (which itself embedded the item id) when
     * it did. {@link ItemStack#hashItemAndComponents} already covers both cases uniformly -- item identity
     * plus whatever components (the modern stand-in for both damage and NBT) are present -- so the branch is
     * gone.
     */
    public static int hash(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return ItemStack.hashItemAndComponents(stack);
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
