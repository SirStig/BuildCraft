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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.common.IPlantable;

import buildcraft.api.crops.ICropHandler;

/**
 * The fallback handler for anything BuildCraft doesn't have a more specific handler for.
 *
 * <p>Unlike 26.x, {@code IPlantable} is still present on 1.20.1 (it was only removed later), and
 * {@code Block#canSustainPlant} still takes an {@code IPlantable} directly, so this stays close to the
 * 1.12.2 shape -- just the standard {@code IBlockState}/{@code World}/{@code EnumFacing} renames. See the
 * 26.x copy of this class for why the two targets diverge here.
 */
public enum CropHandlerPlantable implements ICropHandler {
    INSTANCE;

    @Override
    public boolean isSeed(ItemStack stack) {
        if (stack.getItem() instanceof IPlantable) {
            return true;
        }

        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof IPlantable && block != Blocks.SUGAR_CANE) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean canSustainPlant(Level level, ItemStack seed, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (seed.getItem() instanceof IPlantable plantable) {
            Block block = state.getBlock();
            return block.canSustainPlant(state, level, pos, Direction.UP, plantable) && level.getBlockState(pos.above()).isAir();
        } else {
            Block block = state.getBlock();
            IPlantable plantable = (IPlantable) ((BlockItem) seed.getItem()).getBlock();
            return block.canSustainPlant(state, level, pos, Direction.UP, plantable) && block != ((BlockItem) seed.getItem()).getBlock() && level.getBlockState(pos.above()).isAir();
        }
    }

    @Override
    public boolean plantCrop(Level level, Player player, ItemStack seed, BlockPos pos) {
        return useItemOnBlock(level, player, seed, pos, Direction.UP);
    }

    @Override
    public boolean isMature(BlockGetter blockAccess, BlockState state, BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof FlowerBlock || block instanceof TallGrassBlock || block instanceof MushroomBlock
            || block instanceof DoublePlantBlock || block == Blocks.PUMPKIN) {
            return true;
        } else if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        } else if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) == 3;
        } else if (block instanceof IPlantable) {
            if (blockAccess.getBlockState(pos.below()).getBlock() == block) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean harvestCrop(Level level, BlockPos pos, NonNullList<ItemStack> drops) {
//        if (!level.isClientSide()) {
//            BlockState state = level.getBlockState(pos);
//            if (BlockUtil.breakBlock((ServerLevel) level, pos, drops, pos)) {
//                SoundUtil.playBlockBreak(level, pos, state);
//                return true;
//            }
//        }
        return false;
    }

    /**
     * 1.12.2 routed this through {@code buildcraft.lib.misc.BlockUtil#useItemOnBlock}, which called
     * {@code Item#onItemUseFirst} then {@code Item#onItemUse}. Those two hooks were merged into the single
     * {@link net.minecraft.world.item.Item#useOn(UseOnContext)} well before 1.20.1, so this is inlined here
     * rather than through {@code BlockUtil}, which has not been ported yet (it lives in
     * {@code buildcraft.lib.misc}, outside this pass).
     */
    private static boolean useItemOnBlock(Level level, Player player, ItemStack stack, BlockPos pos, Direction direction) {
        Vec3 hitLocation = Vec3.atCenterOf(pos);
        BlockHitResult hitResult = new BlockHitResult(hitLocation, direction, pos, false);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, hitResult);
        return stack.getItem().useOn(context) == InteractionResult.SUCCESS;
    }
}
