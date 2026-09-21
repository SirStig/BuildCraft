/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory.filter;

import java.util.Arrays;
import java.util.stream.StreamSupport;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.recipes.StackDefinition;

import buildcraft.lib.misc.StackUtil;

/**
 * Returns true if the stack's item is in any one of the given item tags.
 *
 * <p>This used to take ore dictionary names ({@code String}s such as {@code "ingotIron"}) and test
 * {@code OreDictionary.getOreIDs}. The ore dictionary is gone (see PORTING.md's "ore dictionary -> tags" note);
 * an ore name doesn't identify anything on this target any more, so there is nothing left for a {@code String}
 * constructor to look up. A {@link TagKey} is what an ore name would have been asking for, so this now takes
 * those directly rather than re-deriving a tag from a string BuildCraft never registered.
 */
public class OreStackFilter implements IStackFilter {

    private final TagKey<Item>[] tags;

    @SafeVarargs
    public OreStackFilter(TagKey<Item>... tags) {
        this.tags = tags;
    }

    @Override
    public boolean matches(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Holder<Item> holder = stack.typeHolder();
        for (TagKey<Item> tag : tags) {
            if (holder.is(tag)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public NonNullList<ItemStack> getExamples() {
        return Arrays.stream(tags)
            .flatMap(tag -> StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(tag).spliterator(), false))
            .map(Holder::value)
            .map(ItemStack::new)
            .distinct()
            .collect(StackUtil.nonNullListCollector());
    }

    @SafeVarargs
    public static StackDefinition definition(int count, TagKey<Item>... tags) {
        return new StackDefinition(new OreStackFilter(tags), count);
    }

    @SafeVarargs
    public static StackDefinition definition(TagKey<Item>... tags) {
        return definition(1, tags);
    }
}
