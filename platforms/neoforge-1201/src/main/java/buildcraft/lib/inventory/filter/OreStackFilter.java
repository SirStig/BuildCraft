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
 * <p>The ore dictionary ({@code OreDictionary}, keyed by {@code String} names such as {@code "ingotIron"}) was
 * already gone from Forge by 1.20.1, replaced by the tag system 1.13 introduced -- so, unlike most of this
 * port's other divergences, this file is not 26.x-specific: both targets take a {@link TagKey} here, and the
 * only difference from the 26.x copy is that {@link ItemStack#is(TagKey)} is a direct method on this target
 * rather than needing {@code stack.typeHolder().is(tag)}.
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
        for (TagKey<Item> tag : tags) {
            if (stack.is(tag)) {
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
