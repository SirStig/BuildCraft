/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders.tile;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.item.ItemBlueprint;
import buildcraft.builders.snapshot.Blueprint;

import buildcraft.BCBuildersRegistries;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for the from/to-as-plain-block-item scope cut
 * relative to 1.12.2's {@code TileReplacer}/{@code ItemSchematicSingle}, and for why this tile has no GUI. This
 * file differs only in the usual 1.20.1 places: {@code CompoundTag}-based {@code saveAdditional}/{@code load}, and
 * the item slots' capability exposed through {@code getCapability} (matching {@code TileBuilder}'s own 1.20.1
 * precedent) rather than a {@code RegisterCapabilitiesEvent} listener.
 */
public class TileReplacer extends TileBC implements IDebuggable {

    public final ItemHandlerManager itemManager = new ItemHandlerManager((handler, slot, before, after) -> {});
    public final ItemHandlerSimple invBlueprint = itemManager.addInvHandler(
        "blueprint", 1, this::isBlueprint, EnumAccess.BOTH, EnumPipePart.VALUES);
    public final ItemHandlerSimple invFrom = itemManager.addInvHandler(
        "from", 1, this::isBlockItem, EnumAccess.BOTH, EnumPipePart.VALUES);
    public final ItemHandlerSimple invTo = itemManager.addInvHandler(
        "to", 1, this::isBlockItem, EnumAccess.BOTH, EnumPipePart.VALUES);

    public TileReplacer(BlockPos pos, BlockState state) {
        super(BCBuildersRegistries.REPLACER_TYPE.get(), pos, state);
    }

    private boolean isBlueprint(int slot, @NotNull ItemStack stack) {
        return stack.getItem() instanceof ItemBlueprint;
    }

    private boolean isBlockItem(int slot, @NotNull ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }

    /** Driven by {@code BlockReplacer}'s {@code getTicker}. */
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        ItemStack blueprintStack = invBlueprint.getStackInSlot(0);
        ItemStack fromStack = invFrom.getStackInSlot(0);
        ItemStack toStack = invTo.getStackInSlot(0);
        if (blueprintStack.isEmpty() || fromStack.isEmpty() || toStack.isEmpty()) {
            return;
        }
        if (!(fromStack.getItem() instanceof BlockItem fromItem) || !(toStack.getItem() instanceof BlockItem toItem)) {
            return;
        }
        Blueprint blueprint = Blueprint.readFromStack(blueprintStack, level);
        if (blueprint == null) {
            return;
        }
        BlockState fromState = fromItem.getBlock().defaultBlockState();
        BlockState toState = toItem.getBlock().defaultBlockState();
        for (int i = 0; i < blueprint.palette.size(); i++) {
            if (blueprint.palette.get(i).equals(fromState)) {
                blueprint.palette.set(i, toState);
            }
        }
        ItemStack updatedBlueprint = blueprintStack.copy();
        Blueprint.writeToStack(updatedBlueprint, blueprint);
        invBlueprint.setStackInSlot(0, updatedBlueprint);
        invFrom.setStackInSlot(0, ItemStack.EMPTY);
        invTo.setStackInSlot(0, ItemStack.EMPTY);
        markDirtyAndSync();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> itemCap = itemManager.getCapability(cap, side);
        if (itemCap.isPresent()) {
            return itemCap;
        }
        return super.getCapability(cap, side);
    }

    // NBT

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("itemManager", itemManager.serializeNBT());
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemManager.deserializeNBT(nbt.getCompound("itemManager"));
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("blueprint = " + !invBlueprint.getStackInSlot(0).isEmpty());
        left.add("from = " + invFrom.getStackInSlot(0));
        left.add("to = " + invTo.getStackInSlot(0));
    }
}
