/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import buildcraft.transport.item.ItemPluggableFacade;
import buildcraft.transport.plug.FacadeInstance;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.recipe.FacadeSwapRecipe}: a one-item crafting-grid recipe that
 * toggles a facade's hollow/solid flag ({@link FacadeInstance#withSwappedIsHollow}) -- no other item may be
 * present. This is the first custom {@code RecipeType}/{@code RecipeSerializer} this port registers; see
 * PORTING.md's {@code buildcraft.transport} row for why it was previously scope-cut ("no precedent"). It turned
 * out not to need one: {@code net.minecraft.world.item.crafting.CustomRecipe} (this target's replacement for
 * 1.12.2's own {@code IRecipe} + {@code IForgeRegistryEntry}, the same base vanilla itself uses for armor-dye/
 * banner-duplicate/repair-item) already provides exactly the "one singleton recipe, no JSON-driven ingredients,
 * hidden from the recipe book" shape the original hand-rolled {@code IRecipe} implementation wanted. Since this
 * recipe carries no fields at all, {@link #MAP_CODEC} is {@link MapCodec#unit}; one {@code data/buildcraft/
 * recipe/facade_swap.json} (just {@code {"type": "buildcraft:facade_swap"}}) is enough to load it -- 1.12.2's own
 * {@code genRecipes}/{@code ChangingItemStack} recipe-book preview machinery (cycling every valid facade pair
 * through a fake ghost slot) is not reproduced: it is pure recipe-book UI polish, orthogonal to the recipe
 * actually working.
 */
public class FacadeSwapRecipe extends CustomRecipe {
    public static final MapCodec<FacadeSwapRecipe> MAP_CODEC = MapCodec.unit(FacadeSwapRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, FacadeSwapRecipe> STREAM_CODEC =
        StreamCodec.unit(new FacadeSwapRecipe());
    public static final RecipeSerializer<FacadeSwapRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack sole = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (!sole.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                sole = stack;
            }
        }
        if (!(sole.getItem() instanceof ItemPluggableFacade facadeItem)) {
            return ItemStack.EMPTY;
        }
        FacadeInstance state = ItemPluggableFacade.getStates(sole);
        return facadeItem.createItemStack(state.withSwappedIsHollow());
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }
}
