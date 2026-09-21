/*
 * Copyright (c) 2020 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Predicate that compares values of specified NBT keys subset.
 *
 * <p>There is no live {@code getTagCompound()} to compare on this target -- {@link NBTUtilBC#getItemData} reads
 * the stack's {@code CUSTOM_DATA} component into a detached {@link CompoundTag} instead, which is exactly the
 * read-only comparison this class needs.
 */
public class StackNbtMatcher implements StackMatchingPredicate {
    private final String[] keys;

    public StackNbtMatcher(@NotNull String... keys) {
        this.keys = keys;
    }

    @Override
    public boolean isMatching(@NotNull ItemStack base, @NotNull ItemStack comparison) {
        CompoundTag baseNBT = NBTUtilBC.getItemData(base);
        CompoundTag comparisonNBT = NBTUtilBC.getItemData(comparison);

        for (String key : keys) {
            Tag baseValue = baseNBT.get(key);
            Tag comparisonValue = comparisonNBT.get(key);
            if (!Objects.equals(baseValue, comparisonValue)) {
                return false;
            }
        }

        return true;
    }
}
