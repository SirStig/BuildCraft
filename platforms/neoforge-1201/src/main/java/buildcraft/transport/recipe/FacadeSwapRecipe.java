/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

import buildcraft.transport.item.ItemPluggableFacade;
import buildcraft.transport.plug.FacadeInstance;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.recipe.FacadeSwapRecipe} -- see the 26.x copy of this class for the
 * full account of why the custom {@code RecipeSerializer} PORTING.md previously flagged as "no precedent" turned
 * out to be exactly vanilla's own {@code CustomRecipe}/{@code SimpleCraftingRecipeSerializer} shape (the same
 * base {@code ArmorDyeRecipe}/{@code BannerDuplicateRecipe}/{@code RepairItemRecipe} already use). Unlike the
 * 26.x copy, this recipe still carries a real {@link ResourceLocation} id and {@link CraftingBookCategory} --
 * 1.20.1's {@code CustomRecipe} constructor takes both directly rather than assigning them through registration,
 * so {@link #SERIALIZER} is a plain {@link SimpleCraftingRecipeSerializer} rather than a codec pair.
 */
public class FacadeSwapRecipe extends CustomRecipe {
    public static final RecipeSerializer<FacadeSwapRecipe> SERIALIZER =
        new SimpleCraftingRecipeSerializer<>(FacadeSwapRecipe::new);

    public FacadeSwapRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        return !getResultForInput(inv).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registries) {
        return getResultForInput(inv);
    }

    private ItemStack getResultForInput(CraftingContainer inv) {
        ItemStack sole = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
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
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
