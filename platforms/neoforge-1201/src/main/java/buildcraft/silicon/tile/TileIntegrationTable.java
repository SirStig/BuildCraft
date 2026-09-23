/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.tile;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.api.recipes.IntegrationRecipe;

import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.recipe.IntegrationRecipeRegistry;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.silicon.container.ContainerIntegrationTable;

import buildcraft.BCSiliconRegistries;

/** Mirrors the 26.x copy of this class -- see that one's own javadoc for why this does not depend on the
 * gate/pluggable system. This target keeps 1.12.2's own {@code inv.stacks} field access rather than the manual
 * {@code NonNullList} copy 26.x's {@link ItemHandlerSimple} needs. */
public class TileIntegrationTable extends TileLaserTableBase implements MenuProvider {
    public final ItemHandlerSimple invTarget;
    public final ItemHandlerSimple invToIntegrate;
    public final ItemHandlerSimple invResult;

    @Nullable
    public IntegrationRecipe recipe;

    public TileIntegrationTable(BlockPos pos, BlockState state) {
        super(BCSiliconRegistries.INTEGRATION_TABLE_TYPE.get(), pos, state);
        invTarget = itemManager.addInvHandler("target", 1, EnumAccess.BOTH, EnumPipePart.VALUES);
        invToIntegrate = itemManager.addInvHandler("toIntegrate", 3 * 3 - 1, EnumAccess.BOTH, EnumPipePart.VALUES);
        invResult = itemManager.addInvHandler("result", 1, EnumAccess.INSERT, EnumPipePart.VALUES);
    }

    private boolean extractCenter(IngredientStack item, List<IngredientStack> items, boolean simulate) {
        ItemStack targetStack = invTarget.getStackInSlot(0);
        if (targetStack.isEmpty()) {
            return false;
        }
        if (!StackUtil.contains(item, targetStack)) {
            return false;
        }
        if (!extract(invToIntegrate, items, simulate, true)) {
            return false;
        }
        if (!simulate) {
            targetStack.shrink(item.count());
            invTarget.setStackInSlot(0, targetStack);
        }
        return true;
    }

    private boolean isSpaceEnough(ItemStack stack) {
        ItemStack output = invResult.getStackInSlot(0);
        return output.isEmpty()
            || (StackUtil.canMerge(stack, output) && stack.getCount() + output.getCount() <= stack.getMaxStackSize());
    }

    private void updateRecipe() {
        if (recipe != null) {
            ItemStack output = getOutput();
            if (!output.isEmpty() && extractCenter(recipe.getCenterStack(), recipe.getRequirements(output), true)) {
                return;
            }
        }
        recipe = IntegrationRecipeRegistry.INSTANCE.getRecipeFor(invTarget.getStackInSlot(0), invToIntegrate.stacks);
    }

    public ItemStack getOutput() {
        return recipe != null ? recipe.getOutput(invTarget.getStackInSlot(0), invToIntegrate.stacks) : ItemStack.EMPTY;
    }

    @Override
    public long getTarget() {
        ItemStack output = getOutput();
        return recipe != null && isSpaceEnough(output) ? recipe.getRequiredMicroJoules(output) : 0;
    }

    @Override
    public void serverTick() {
        super.serverTick();

        updateRecipe();

        if (getTarget() > 0 && power >= getTarget()) {
            ItemStack output = getOutput();
            extractCenter(recipe.getCenterStack(), recipe.getRequirements(output), false);
            ItemStack result = invResult.getStackInSlot(0);
            if (!result.isEmpty()) {
                result = result.copy();
                result.grow(output.getCount());
            } else {
                result = output.copy();
            }
            invResult.setStackInSlot(0, result);
            power -= getTarget();
        }

        markDirtyAndSync();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (recipe != null) {
            nbt.putString("recipe", recipe.name.toString());
        }
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        recipe = nbt.contains("recipe") ? lookupRecipe(nbt.getString("recipe")) : null;
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        super.getDebugInfo(left, right, side);
        left.add("recipe - " + recipe);
    }

    @Nullable
    private IntegrationRecipe lookupRecipe(String name) {
        return IntegrationRecipeRegistry.INSTANCE.getRecipe(new ResourceLocation(name));
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerIntegrationTable(windowId, playerInv, this);
    }
}
