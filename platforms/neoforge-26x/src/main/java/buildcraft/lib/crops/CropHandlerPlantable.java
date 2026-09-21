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
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.crops.ICropHandler;

/**
 * The fallback handler for anything BuildCraft doesn't have a more specific handler for.
 *
 * <p>1.12.2 recognised a "generically plantable" item by {@code IPlantable}, an interface {@code BlockBush}
 * (the shared base for crops, saplings, flowers, tall grass, mushrooms, stems and nether wart) implemented, and
 * which {@code ItemSeeds} also implemented directly. {@code IPlantable} does not exist on 26.x -- it was
 * removed along with the rest of the old plant-type system. Its replacement is two-fold: block-side,
 * {@code VegetationBlock} is the new shared base for exactly that same family (confirmed against the merged
 * jar: {@code CropBlock}, {@code SaplingBlock}, {@code StemBlock}, {@code NetherWartBlock}, {@code FlowerBlock},
 * {@code TallGrassBlock}, {@code MushroomBlock} and {@code DoublePlantBlock} all extend it directly), so it is
 * the faithful modern test for "is this block one BuildCraft's default handler should plant". Soil-side,
 * {@code IBlockStateExtension#canSustainPlant} takes the candidate plant's {@link BlockState} instead of an
 * {@code IPlantable} instance and answers a {@link TriState} rather than a boolean, because it is now allowed
 * to say "I have no opinion, ask the plant" (@code DEFAULT}) instead of only yes/no. That default case is
 * resolved here against {@link BlockState#canSurvive}, the plant's own vanilla placement check.
 */
public enum CropHandlerPlantable implements ICropHandler {
    INSTANCE;

    @Override
    public boolean isSeed(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return block instanceof VegetationBlock && block != Blocks.SUGAR_CANE;
        }
        return false;
    }

    @Override
    public boolean canSustainPlant(Level level, ItemStack seed, BlockPos pos) {
        Block plantBlock = ((BlockItem) seed.getItem()).getBlock();
        BlockState plantState = plantBlock.defaultBlockState();
        BlockState soilState = level.getBlockState(pos);
        Block soilBlock = soilState.getBlock();

        TriState soilOpinion = soilState.canSustainPlant(level, pos, Direction.UP, plantState);
        boolean sustains = soilOpinion.toBoolean(plantState.canSurvive(level, pos));
        return sustains && soilBlock != plantBlock && level.getBlockState(pos.above()).isAir();
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
        } else if (block instanceof VegetationBlock) {
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
     * {@link net.minecraft.world.item.Item#useOn(UseOnContext)} well before 26.x, so this is inlined here
     * rather than through {@code BlockUtil}, which has not been ported yet (it lives in
     * {@code buildcraft.lib.misc}, outside this pass).
     */
    private static boolean useItemOnBlock(Level level, Player player, ItemStack stack, BlockPos pos, Direction direction) {
        Vec3 hitLocation = Vec3.atCenterOf(pos);
        BlockHitResult hitResult = new BlockHitResult(hitLocation, direction, pos, false);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, hitResult);
        // SUCCESS and SUCCESS_SERVER (the client/server split InteractionResult now makes) are both wrapped in
        // the same Success record, unlike 1.12.2's single EnumActionResult.SUCCESS value.
        return stack.getItem().useOn(context) instanceof InteractionResult.Success;
    }
}
