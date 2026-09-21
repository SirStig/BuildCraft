/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.crops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.crops.CropManager;
import buildcraft.api.crops.ICropHandler;

/**
 * Sugar cane's own handler, kept separate from {@link CropHandlerPlantable} because sugar cane -- 1.12.2's
 * {@code Blocks.REEDS}, now {@link Blocks#SUGAR_CANE} -- is explicitly excluded from the generic handler.
 * See the class doc on {@link CropHandlerPlantable} for how {@code IPlantable}'s removal changed
 * {@code canSustainPlant}.
 */
public enum CropHandlerReeds implements ICropHandler {
    INSTANCE;
    public static final int MAX_HEIGHT = 3;

    @Override
    public boolean isSeed(ItemStack stack) {
        return stack.getItem() == Items.SUGAR_CANE;
    }

    @Override
    public boolean canSustainPlant(Level level, ItemStack seed, BlockPos pos) {
        BlockState soilState = level.getBlockState(pos);
        Block soilBlock = soilState.getBlock();
        BlockState caneState = Blocks.SUGAR_CANE.defaultBlockState();

        TriState soilOpinion = soilState.canSustainPlant(level, pos, Direction.UP, caneState);
        boolean sustains = soilOpinion.toBoolean(caneState.canSurvive(level, pos));
        return sustains && soilBlock != Blocks.SUGAR_CANE && level.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean plantCrop(Level level, Player player, ItemStack seed, BlockPos pos) {
        ICropHandler defaultHandler = CropManager.getDefaultHandler();
        return defaultHandler != null && defaultHandler.plantCrop(level, player, seed, pos);
    }

    @Override
    public boolean isMature(BlockGetter access, BlockState state, BlockPos pos) {
        return false;
    }

    @Override
    public boolean harvestCrop(Level level, BlockPos pos, NonNullList<ItemStack> drops) {
        return false;
    }
}
