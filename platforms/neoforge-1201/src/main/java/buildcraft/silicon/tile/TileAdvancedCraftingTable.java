/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.items.IItemHandlerModifiable;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.MjAPI;

import buildcraft.lib.tile.craft.WorkbenchCrafting;
import buildcraft.lib.tile.item.IAutoCraft;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.silicon.container.ContainerAdvancedCraftingTable;

import buildcraft.BCSiliconRegistries;

/** Mirrors the 26.x copy of this class -- see that one's own javadoc. On this target {@link ItemHandlerSimple}
 * already implements {@link buildcraft.api.inventory.IItemTransactor} directly (see {@code WorkbenchCrafting}'s
 * own javadoc), so {@code invMaterials}/{@code invResults} are passed straight through with no wrapper. */
public class TileAdvancedCraftingTable extends TileLaserTableBase implements IAutoCraft, MenuProvider {
    private static final long POWER_REQ = 500 * MjAPI.MJ;

    public final ItemHandlerSimple invBlueprint;
    public final ItemHandlerSimple invMaterials;
    public final ItemHandlerSimple invResults;
    private final WorkbenchCrafting crafting;

    public TileAdvancedCraftingTable(BlockPos pos, BlockState state) {
        super(BCSiliconRegistries.ADVANCED_CRAFTING_TABLE_TYPE.get(), pos, state);
        invBlueprint = itemManager.addInvHandler("blueprint", 3 * 3, EnumAccess.PHANTOM);
        invMaterials = itemManager.addInvHandler("materials", 5 * 3, EnumAccess.INSERT, EnumPipePart.VALUES);
        invResults = itemManager.addInvHandler("result", 3 * 3, EnumAccess.EXTRACT, EnumPipePart.VALUES);
        crafting = new WorkbenchCrafting(3, 3, this, invBlueprint, invMaterials, invResults);
    }

    @Override
    protected void onSlotChange(IItemHandlerModifiable handler, int slot, ItemStack before, ItemStack after) {
        super.onSlotChange(handler, slot, before, after);
        if (!ItemStack.matches(before, after)) {
            if (handler == invBlueprint) {
                crafting.onBlueprintChange();
            } else if (handler == invMaterials) {
                crafting.onMaterialsChange();
            }
        }
    }

    @Override
    public long getTarget() {
        if (level != null && level.isClientSide()) {
            return POWER_REQ;
        }
        return crafting.canCraft() ? POWER_REQ : 0;
    }

    @Override
    public void serverTick() {
        super.serverTick();

        boolean didChange = crafting.tick();
        if (crafting.canCraft() && power >= POWER_REQ) {
            if (crafting.craft()) {
                power -= POWER_REQ;
            }
        }
        if (didChange) {
            markDirtyAndSync();
        }
    }

    // IAutoCraft

    @Override
    public ItemStack getCurrentRecipeOutput() {
        return crafting.getAssumedResult();
    }

    @Override
    public ItemHandlerSimple getInvBlueprint() {
        return invBlueprint;
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerAdvancedCraftingTable(windowId, playerInv, this);
    }
}
