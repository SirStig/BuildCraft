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
 */
public class StackNbtMatcher implements StackMatchingPredicate {
    private final String[] keys;

    public StackNbtMatcher(@NotNull String... keys) {
        this.keys = keys;
    }

    @Override
    public boolean isMatching(@NotNull ItemStack base, @NotNull ItemStack comparison) {
        CompoundTag baseNBT = base.getTag();
        CompoundTag comparisonNBT = comparison.getTag();

        for (String key : keys) {
            Tag baseValue = baseNBT != null ? baseNBT.get(key) : null;
            Tag comparisonValue = comparisonNBT != null ? comparisonNBT.get(key) : null;
            if (!Objects.equals(baseValue, comparisonValue)) {
                return false;
            }
        }

        return true;
    }
}
