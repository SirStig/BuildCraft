/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.ImmutableSet;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.AssemblyRecipe;
import buildcraft.api.recipes.IngredientStack;

import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.recipe.ChangingItemStack;
import buildcraft.lib.recipe.ChangingObject;
import buildcraft.lib.recipe.IRecipeViewable;

import buildcraft.transport.item.ItemPluggableFacade;
import buildcraft.transport.plug.FacadeBlockStateInfo;
import buildcraft.transport.plug.FacadeInstance;
import buildcraft.transport.plug.FacadeStateManager;

import buildcraft.BCTransportRegistries;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.recipe.FacadeAssemblyRecipes}: crafts a facade of any scanned block
 * at the Assembly Table, from three "pipe structure" items (1.12.2's {@code BCItems.Transport.PIPE_STRUCTURE},
 * which was never ported to this target -- no structure-pipe accessory exists here yet, so this falls back to
 * 1.12.2's own null-check fallback stack, cobblestone wall, unconditionally) plus one of the disguised block.
 *
 * <p><b>Registered but not necessarily reachable this round:</b> the Assembly Table ({@code TileAssemblyTable})
 * itself is real on this port, but whether anything can actually power it -- the real laser-emitter block that
 * feeds it MJ -- was still unstarted work by a concurrent batch as of this one's own start (checked: no
 * {@code TileLaser} class exists in this tree). Registering this recipe regardless costs nothing and means
 * nothing else has to remember to do it once a laser lands.
 *
 * <p>Only single ({@link buildcraft.api.facades.FacadeType#BASIC}) facades are produced -- matching 1.12.2's own
 * {@code createFacadeStack}, which never builds a phased instance either; phasing was only ever assembled by
 * hand via {@code ItemPluggableFacade#addSubItems}' creative-tab demo stack, which this port does not carry
 * over (see that class's own scope-cut note).
 */
public class FacadeAssemblyRecipes extends AssemblyRecipe implements IRecipeViewable.IRecipePowered {
    public static final FacadeAssemblyRecipes INSTANCE = new FacadeAssemblyRecipes();

    private static final int TIME_GAP = 500;
    private static final long MJ_COST = 64 * MjAPI.MJ;
    private static final ChangingObject<Long> MJ_COSTS = new ChangingObject<>(new Long[] { MJ_COST });

    private FacadeAssemblyRecipes() {
        super(Identifier.fromNamespaceAndPath("buildcraft", "facade_recipes"));
    }

    public static ItemStack createFacadeStack(FacadeBlockStateInfo info, boolean isHollow) {
        ItemStack stack = BCTransportRegistries.ITEM_PLUGGABLE_FACADE.get()
            .createItemStack(FacadeInstance.createSingle(info, isHollow));
        stack.setCount(6);
        return stack;
    }

    @Override
    public ChangingItemStack[] getRecipeInputs() {
        ChangingItemStack[] inputs = new ChangingItemStack[2];
        inputs[0] = new ChangingItemStack(baseRequirementStack());
        NonNullList<ItemStack> list = NonNullList.create();
        for (FacadeBlockStateInfo info : FacadeStateManager.validFacadeStates.values()) {
            if (info.isVisible) {
                list.add(info.requiredStack);
                list.add(info.requiredStack);
            }
        }
        inputs[1] = new ChangingItemStack(list);
        inputs[1].setTimeGap(TIME_GAP);
        return inputs;
    }

    @Override
    public ChangingItemStack getRecipeOutputs() {
        NonNullList<ItemStack> list = NonNullList.create();
        for (FacadeBlockStateInfo info : FacadeStateManager.validFacadeStates.values()) {
            if (info.isVisible) {
                list.add(createFacadeStack(info, false));
                list.add(createFacadeStack(info, true));
            }
        }
        ChangingItemStack changing = new ChangingItemStack(list);
        changing.setTimeGap(TIME_GAP);
        return changing;
    }

    @Override
    public ChangingObject<Long> getMjCost() {
        return MJ_COSTS;
    }

    @Override
    public Set<ItemStack> getOutputs(NonNullList<ItemStack> inputs) {
        if (!StackUtil.contains(baseRequirementStack(), inputs)) {
            return Collections.emptySet();
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : inputs) {
            stack = stack.copy();
            stack.setCount(1);
            List<FacadeBlockStateInfo> infos = FacadeStateManager.stackFacades.get(new ItemStackKey(stack));
            if (infos == null || infos.isEmpty()) {
                continue;
            }
            for (FacadeBlockStateInfo info : infos) {
                stacks.add(createFacadeStack(info, false));
                stacks.add(createFacadeStack(info, true));
            }
        }
        return ImmutableSet.copyOf(stacks);
    }

    private static ItemStack baseRequirementStack() {
        return new ItemStack(Blocks.COBBLESTONE_WALL, 3);
    }

    @Override
    public Set<ItemStack> getOutputPreviews() {
        return Collections.emptySet();
    }

    @Override
    public Set<IngredientStack> getInputsFor(@NotNull ItemStack output) {
        var state = ItemPluggableFacade.getStates(output).getCurrentStateForStack();
        ItemStack stateRequirement = state.stateInfo.requiredStack;
        IngredientStack ingredientType = new IngredientStack(Ingredient.of(stateRequirement.getItem()));
        IngredientStack ingredientBase = new IngredientStack(Ingredient.of(Blocks.COBBLESTONE_WALL), 3);

        return ImmutableSet.of(ingredientType, ingredientBase);
    }

    @Override
    public long getRequiredMicroJoulesFor(@NotNull ItemStack output) {
        return MJ_COST;
    }
}
