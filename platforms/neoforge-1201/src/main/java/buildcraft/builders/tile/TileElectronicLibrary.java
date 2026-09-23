/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
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
 * Mirrors the 26.x class of the same name -- see that one's javadoc for the full "why" of this scope cut relative
 * to 1.12.2's network-mediated {@code TileElectronicLibrary}/{@code GlobalSavedDataSnapshots} duplicator, and for
 * why this tile has no GUI. This file differs only in the usual 1.20.1 places: {@code CompoundTag}-based
 * {@code saveAdditional}/{@code load}, and the item slots' capability exposed through {@code getCapability}
 * (matching {@code TileBuilder}'s own 1.20.1 precedent) rather than a {@code RegisterCapabilitiesEvent} listener.
 */
public class TileElectronicLibrary extends TileBC implements IDebuggable {

    public final ItemHandlerManager itemManager = new ItemHandlerManager((handler, slot, before, after) -> {});
    /** The library's standing master copy -- read from, never consumed by {@link #serverTick()}. */
    public final ItemHandlerSimple invMaster = itemManager.addInvHandler(
        "master", 1, this::isBlueprint, EnumAccess.BOTH, EnumPipePart.VALUES);
    /** Any blueprint stack fed in here is overwritten with a copy of {@link #invMaster}'s data. */
    public final ItemHandlerSimple invIn = itemManager.addInvHandler(
        "in", 1, this::isBlueprint, EnumAccess.INSERT, EnumPipePart.VALUES);
    public final ItemHandlerSimple invOut = itemManager.addInvHandler(
        "out", 1, EnumAccess.EXTRACT, EnumPipePart.VALUES);

    public TileElectronicLibrary(BlockPos pos, BlockState state) {
        super(BCBuildersRegistries.ELECTRONIC_LIBRARY_TYPE.get(), pos, state);
    }

    private boolean isBlueprint(int slot, @NotNull ItemStack stack) {
        return stack.getItem() instanceof ItemBlueprint;
    }

    /** Driven by {@code BlockElectronicLibrary}'s {@code getTicker}. */
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        ItemStack masterStack = invMaster.getStackInSlot(0);
        ItemStack inStack = invIn.getStackInSlot(0);
        if (masterStack.isEmpty() || inStack.isEmpty() || !invOut.getStackInSlot(0).isEmpty()) {
            return;
        }
        Blueprint master = Blueprint.readFromStack(masterStack, level);
        if (master == null) {
            return;
        }
        ItemStack duplicate = inStack.copy();
        duplicate.setCount(1);
        Blueprint.writeToStack(duplicate, master);
        invOut.setStackInSlot(0, duplicate);
        invIn.setStackInSlot(0, ItemStack.EMPTY);
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
        left.add("master = " + !invMaster.getStackInSlot(0).isEmpty());
        left.add("in = " + !invIn.getStackInSlot(0).isEmpty());
        left.add("out = " + !invOut.getStackInSlot(0).isEmpty());
    }
}
