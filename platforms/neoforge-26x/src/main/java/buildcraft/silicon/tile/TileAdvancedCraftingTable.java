/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.MjAPI;

import buildcraft.lib.inventory.ItemHandlerWrapper;
import buildcraft.lib.tile.craft.WorkbenchCrafting;
import buildcraft.lib.tile.item.IAutoCraft;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.silicon.container.ContainerAdvancedCraftingTable;

import buildcraft.BCSiliconRegistries;

/**
 * Ported from 1.12.2's {@code TileAdvancedCraftingTable}: a portable, laser-powered 3x3 vanilla crafting grid.
 * {@link WorkbenchCrafting} (already ported for {@code buildcraft.factory.tile.TileAutoWorkbenchBase}) does the
 * actual recipe matching -- this class only owns the three inventories and the laser-power gate in front of
 * {@link WorkbenchCrafting#craft()}.
 *
 * <p>1.12.2's 9-slot {@code invResults} (nine separately addressable output slots, not just one) is kept as-is:
 * {@link WorkbenchCrafting} only ever needs a single {@link buildcraft.api.inventory.IItemTransactor} for its
 * result, so {@code invResults} is wrapped the same way {@code TileAutoWorkbenchBase#invResult} is (see
 * {@code buildcraft.factory.tile.TileAutoWorkbenchBase}'s own javadoc) -- {@code ItemHandlerWrapper} already spans
 * every slot of the handler it wraps, so nothing here needs to change for the 9-slot case.
 *
 * <p>1.12.2's {@code resultClient} field (populated by a hand-written {@code NET_GUI_DATA} payload) is dropped in
 * favour of this port's established idiom: {@link WorkbenchCrafting#getAssumedResult()} is read directly by a
 * {@code SlotDisplay}, which {@code AbstractContainerMenu#broadcastChanges()} already keeps in sync (see
 * {@code buildcraft.factory.container.ContainerAutoCraftItems}'s own javadoc for the precedent).
 * {@code getTarget()}'s client-side special case (returning the flat {@link #POWER_REQ} rather than actually
 * calling {@link WorkbenchCrafting#canCraft()}, which needs a server-only {@code IItemTransactor} transaction) is
 * carried over unchanged from 1.12.2 -- the GUI's progress bar only needs "is it non-zero", not perfect accuracy.
 */
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
        crafting = new WorkbenchCrafting(3, 3, this,
            invBlueprint,
            new ItemHandlerWrapper(invMaterials),
            new ItemHandlerWrapper(invResults));
    }

    @Override
    protected void onSlotChange(ItemHandlerSimple handler, int slot, ItemStack before, ItemStack after) {
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
        return new ContainerAdvancedCraftingTable(BCSiliconRegistries.ADVANCED_CRAFTING_TABLE_MENU.get(), windowId, playerInv, this);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
    }
}
