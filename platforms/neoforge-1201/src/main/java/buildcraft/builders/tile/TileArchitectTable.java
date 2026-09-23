/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders.tile;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.tiles.ITileAreaProvider;

import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.snapshot.Blueprint;

import buildcraft.BCBuildersRegistries;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for the capture algorithm and everything cut
 * relative to 1.12.2's {@code TileArchitectTable}. This file differs only in the usual 1.20.1 places:
 * {@code CompoundTag}-based {@code saveAdditional}/{@code load}, and the output slot's item capability exposed
 * through {@code getCapability} (matching {@code TileBuilder}'s own 1.20.1 precedent) rather than a
 * {@code RegisterCapabilitiesEvent} listener.
 */
public class TileArchitectTable extends TileBC implements IDebuggable {

    private static final int DEFAULT_RADIUS_H = 2;
    private static final int DEFAULT_HEIGHT = 4;

    public final ItemHandlerManager itemManager = new ItemHandlerManager((handler, slot, before, after) -> {});
    /** {@code EnumPipePart.VALUES} is required here, not optional -- see the 26.x class's own javadoc on this
     * same field for why an empty {@code parts} list would leave this slot unreachable from every side. */
    public final ItemHandlerSimple invOut =
        itemManager.addInvHandler("out", 1, EnumAccess.EXTRACT, EnumPipePart.VALUES);

    public final Box box = new Box();
    private boolean scanned = false;

    public TileArchitectTable(BlockPos pos, BlockState state) {
        super(BCBuildersRegistries.ARCHITECT_TABLE_TYPE.get(), pos, state);
    }

    /** Claims the area to capture, either from a directly-adjacent {@link ITileAreaProvider} or a fixed-size
     * default box, then scans immediately -- see the 26.x class javadoc. */
    public void onPlacedBy(LivingEntity placer) {
        if (level == null || level.isClientSide()) {
            return;
        }
        Direction facing = getBlockState().getValue(BuildCraftProperties.BLOCK_FACING);
        BlockPos areaPos = worldPosition.relative(facing.getOpposite());
        BlockEntity adjacent = level.getBlockEntity(areaPos);

        if (adjacent instanceof ITileAreaProvider provider) {
            box.reset();
            box.setMin(provider.min());
            box.setMax(provider.max());
            provider.removeFromWorld();
        } else {
            switch (facing.getOpposite()) {
                case WEST -> {
                    box.setMin(worldPosition.offset(-DEFAULT_RADIUS_H - 1, 0, -DEFAULT_RADIUS_H));
                    box.setMax(worldPosition.offset(-1, DEFAULT_HEIGHT, DEFAULT_RADIUS_H));
                }
                case EAST -> {
                    box.setMin(worldPosition.offset(1, 0, -DEFAULT_RADIUS_H));
                    box.setMax(worldPosition.offset(DEFAULT_RADIUS_H + 1, DEFAULT_HEIGHT, DEFAULT_RADIUS_H));
                }
                case NORTH -> {
                    box.setMin(worldPosition.offset(-DEFAULT_RADIUS_H, 0, -DEFAULT_RADIUS_H - 1));
                    box.setMax(worldPosition.offset(DEFAULT_RADIUS_H, DEFAULT_HEIGHT, -1));
                }
                default -> {
                    box.setMin(worldPosition.offset(-DEFAULT_RADIUS_H, 0, 1));
                    box.setMax(worldPosition.offset(DEFAULT_RADIUS_H, DEFAULT_HEIGHT, DEFAULT_RADIUS_H + 1));
                }
            }
        }
        scanAndProduce();
    }

    private void scanAndProduce() {
        if (scanned || level == null || level.isClientSide() || !box.isInitialized()) {
            return;
        }
        scanned = true;
        Direction facing = getBlockState().getValue(BuildCraftProperties.BLOCK_FACING);
        Blueprint blueprint = Blueprint.capture(level, box.min(), box.size(), facing);
        ItemStack stack = new ItemStack(BCBuildersRegistries.BLUEPRINT.get());
        Blueprint.writeToStack(stack, blueprint);
        if (invOut.getStackInSlot(0).isEmpty()) {
            invOut.setStackInSlot(0, stack);
        } else {
            InventoryUtil.addToBestAcceptor(level, worldPosition, facing.getOpposite(), stack);
        }
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
        nbt.put("box", box.writeToNBT());
        nbt.putBoolean("scanned", scanned);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemManager.deserializeNBT(nbt.getCompound("itemManager"));
        box.initialize(nbt.getCompound("box"));
        scanned = nbt.getBoolean("scanned");
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("box = " + box.min() + " -> " + box.max());
        left.add("scanned = " + scanned);
    }
}
