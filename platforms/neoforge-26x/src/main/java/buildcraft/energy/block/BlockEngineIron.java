/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

import buildcraft.api.blocks.ICustomRotationHandler;
import buildcraft.api.transport.pipe.IItemPipe;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.misc.EntityUtil;

import buildcraft.energy.tile.TileEngineIron;

/**
 * The Combustion Engine's block -- 1.12.2's {@code IRON} variant of the shared {@code BlockEngine_BC8}, split into
 * its own block exactly like {@link BlockEngineStone} (see that class, and {@code TileEngineWood}'s javadoc, for the
 * facing property, the ticker and the full-cube shape).
 *
 * <p>Right-click is 1.12.2's {@code TileEngineIron_BC8#onActivated}, in its original order:
 * <ol>
 * <li>Any fluid container ({@code TankManager#onActivated}, i.e. {@code FluidUtilBC.onTankActivated}) is handled
 *     against all three tanks ({@link TileEngineIron#allTanks}) and always consumes the click, even when nothing
 *     could move -- 1.12.2 returned {@code true} for any item with a fluid handler. Done here with NeoForge's own
 *     {@link FluidUtil#interactWithFluidHandler}, server side only; like 1.12.2, the client just reports success
 *     for any fluid container.</li>
 * <li>A wrench (in the main hand, {@link EntityUtil#getWrenchHand}) or a pipe item returns
 *     {@link InteractionResult#PASS}, which on this target skips {@link #useWithoutItem} and goes straight to the
 *     item's own {@code useOn} (read from {@code ServerPlayerGameMode#useItemOn}), so the wrench can rotate the
 *     engine.</li>
 * <li>Anything else opens the GUI, through {@link InteractionResult#TRY_WITH_EMPTY_HAND} and
 *     {@link #useWithoutItem}.</li>
 * </ol>
 */
public class BlockEngineIron extends BlockBCTile implements ICustomRotationHandler {

    public BlockEngineIron(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineIron(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileEngineIron engine) {
                engine.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineIron engine) {
            engine.onPlacedBy(placer, stack);
        }
    }

    @Override
    protected void neighborChanged(
        BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineIron engine) {
            engine.onNeighbourBlockChanged();
        }
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof TileEngineIron engine)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (ItemAccess.forPlayerInteraction(player, hand).oneByOne().getCapability(Capabilities.Fluid.ITEM) != null) {
            if (!level.isClientSide()) {
                FluidUtil.interactWithFluidHandler(player, hand, pos, engine.allTanks, null);
            }
            return InteractionResult.SUCCESS;
        }
        if (EntityUtil.getWrenchHand(player) != null || stack.getItem() instanceof IItemPipe) {
            return InteractionResult.PASS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineIron engine) {
            player.openMenu(engine);
        }
        return InteractionResult.SUCCESS;
    }

    // ICustomRotationHandler

    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (level.getBlockEntity(pos) instanceof TileEngineIron engine) {
            return engine.attemptRotation();
        }
        return InteractionResult.FAIL;
    }
}
