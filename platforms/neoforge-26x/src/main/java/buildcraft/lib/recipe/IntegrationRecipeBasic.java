/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.recipe;

import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.api.recipes.IntegrationRecipe;

import buildcraft.lib.misc.StackUtil;

public class IntegrationRecipeBasic extends IntegrationRecipe {
    protected final long requiredMicroJoules;
    protected final IngredientStack target;
    protected final ImmutableList<IngredientStack> toIntegrate;
    protected final @NotNull ItemStack output;

    public IntegrationRecipeBasic(Identifier name, long requiredMicroJoules, IngredientStack target, List<IngredientStack> toIntegrate, @NotNull ItemStack output) {
        super(name);
        this.requiredMicroJoules = requiredMicroJoules;
        this.target = target;
        this.toIntegrate = ImmutableList.copyOf(toIntegrate);
        this.output = output;
    }

    public IntegrationRecipeBasic(String name, long requiredMicroJoules, IngredientStack target, List<IngredientStack> toIntegrate, @NotNull ItemStack output) {
        this(BuildCraftAPI.nameToResourceId(name), requiredMicroJoules, target, toIntegrate, output);
    }


    protected boolean matches(@NotNull ItemStack target, NonNullList<ItemStack> toIntegrate) {
        if (!StackUtil.contains(this.target, target)) {
            return false;
        }
        NonNullList<ItemStack> toIntegrateCopy = toIntegrate.stream().filter(stack -> !stack.isEmpty()).collect(StackUtil.nonNullListCollector());
        boolean stackMatches = this.toIntegrate.stream().allMatch((definition) -> {
            boolean matches = false;
            Iterator<ItemStack> iterator = toIntegrateCopy.iterator();
            while (iterator.hasNext()) {
                ItemStack stack = iterator.next();
                if (StackUtil.contains(definition, stack)) {
                    matches = true;
                    iterator.remove();
                    break;
                }
            }
            return matches;
        });
        return stackMatches && toIntegrateCopy.isEmpty();
    }

    @Override
    public ItemStack getOutput(@NotNull ItemStack target, NonNullList<ItemStack> toIntegrate) {
        return matches(target, toIntegrate) ? output : ItemStack.EMPTY;
    }

    @Override
    public List<IngredientStack> getRequirements(ItemStack output) {
        return toIntegrate;
    }

    @Override
    public long getRequiredMicroJoules(ItemStack output) {
        return requiredMicroJoules;
    }

    @Override
    public IngredientStack getCenterStack() {
        return target;
    }
}
