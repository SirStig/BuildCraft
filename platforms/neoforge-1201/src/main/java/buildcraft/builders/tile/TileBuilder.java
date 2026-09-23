/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders.tile;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.item.ItemBlueprint;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.BlueprintBuilder;

import buildcraft.BCBuildersRegistries;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for the general shape and everything dropped
 * relative to 1.12.2's {@code TileBuilder}/{@code BlueprintBuilder}. This file differs only in the usual 1.20.1
 * places: {@code CompoundTag}-based {@code saveAdditional}/{@code load} rather than {@code ValueOutput}/
 * {@code ValueInput}, and MJ/item capabilities exposed through {@code getCapability} (matching
 * {@code TileQuarry}/{@code buildcraft.energy.tile.TileEngineStone}'s own 1.20.1 precedent) rather than a
 * {@code RegisterCapabilitiesEvent} listener.
 */
public class TileBuilder extends TileBC implements IDebuggable {
    private static final long BATTERY_CAPACITY = 16_000 * MjAPI.MJ;

    public final ItemHandlerManager itemManager = new ItemHandlerManager(this::onSlotChange);
    public final ItemHandlerSimple invBlueprint;
    public final ItemHandlerSimple invResources;

    private final MjBattery battery = new MjBattery(BATTERY_CAPACITY);
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);
    private final LazyOptional<IMjReceiver> receiverCap = LazyOptional.of(() -> mjReceiver);
    private final LazyOptional<IMjReadable> readableCap = LazyOptional.of(() -> mjReceiver);

    private final BlueprintBuilder builder = new BlueprintBuilder(this);

    public TileBuilder(BlockPos pos, BlockState state) {
        super(BCBuildersRegistries.BUILDER_TYPE.get(), pos, state);
        invBlueprint = itemManager.addInvHandler(
            "blueprint", 1, this::isValidBlueprint, EnumAccess.BOTH, EnumPipePart.VALUES);
        invResources = itemManager.addInvHandler("resources", 27, EnumAccess.BOTH, EnumPipePart.VALUES);
    }

    private boolean isValidBlueprint(int slot, ItemStack stack) {
        return stack.getItem() instanceof ItemBlueprint;
    }

    private void onSlotChange(IItemHandlerModifiable handler, int slot, ItemStack before, ItemStack after) {
        if (handler == invBlueprint) {
            loadBlueprint();
        }
    }

    private void loadBlueprint() {
        ItemStack stack = invBlueprint.getStackInSlot(0);
        Blueprint newBlueprint = level == null || stack.isEmpty() ? null : Blueprint.readFromStack(stack, level);
        Direction facing = getBlockState().getValue(BuildCraftProperties.BLOCK_FACING);
        BlockPos basePos = worldPosition.relative(facing.getOpposite());
        builder.setBlueprint(newBlueprint, basePos);
        markDirtyAndSync();
    }

    /** Driven by {@code BlockBuilder}'s {@code getTicker}. */
    public void serverTick() {
        if (level instanceof ServerLevel serverLevel && builder.getBlueprint() != null) {
            builder.tick(serverLevel);
            setChanged();
        }
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == MjCapabilities.RECEIVER) {
            return receiverCap.cast();
        }
        if (cap == MjCapabilities.READABLE) {
            return readableCap.cast();
        }
        LazyOptional<T> itemCap = itemManager.getCapability(cap, side);
        if (itemCap.isPresent()) {
            return itemCap;
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        receiverCap.invalidate();
        readableCap.invalidate();
    }

    // NBT

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("itemManager", itemManager.serializeNBT());
        nbt.putLong("battery", battery.getStored());
        nbt.put("builder", builder.serializeNBT());
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemManager.deserializeNBT(nbt.getCompound("itemManager"));
        battery.setStored(nbt.getLong("battery"));
        if (level != null && nbt.contains("builder")) {
            builder.deserializeNBT(nbt.getCompound("builder"), Blueprint.blockLookup(level));
        }
    }

    // Accessors used by BlueprintBuilder

    public MjBattery getBattery() {
        return battery;
    }

    public ItemHandlerSimple getInvResources() {
        return invResources;
    }

    public BlockPos getBuilderPos() {
        return worldPosition;
    }

    @Nullable
    public Blueprint getBlueprint() {
        return builder.getBlueprint();
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("leftToBreak = " + builder.leftToBreak);
        left.add("leftToPlace = " + builder.leftToPlace);
    }
}
