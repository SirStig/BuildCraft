/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import buildcraft.api.blocks.ICustomRotationHandler;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.block.EngineShapes;

import buildcraft.energy.tile.TileEngineStone;

/**
 * Renamed from 1.12.2's shared, multi-variant {@code BlockEngine_BC8} -- see {@code TileEngineWood}'s javadoc
 * (which this class mirrors exactly, `core`/`energy` package split aside) for why the STONE variant becomes its
 * own real block/item pair rather than a metadata subtype of one shared block, and for the "no custom render
 * shape yet" reasoning that also applies here.
 *
 * <p>Right-click always opens {@link buildcraft.energy.container.ContainerEngineStone}'s GUI -- 1.12.2's own
 * {@code TileEngineStone_BC8#onActivated} never checked the held item either, matching
 * {@code BlockAutoWorkbenchItems}'s own precedent, including the 26.x-specific {@code useWithoutItem} override
 * (confirmed {@code protected} via {@code javap} against this target's {@code BlockBehaviour}, unlike 1.20.1's
 * still-unified {@code use} -- see the 1.20.1 copy of this class, and {@code BlockAutoWorkbenchItems}'s own
 * javadoc for the fuller account of that split).
 *
 * <p>{@link BlockStateProperties#FACING} is declared here for exactly the reason {@code BlockEngineWood} declares
 * it -- see that class's own javadoc and {@code TileEngineBase}'s "Facing visibility" entry for the full account.
 */
public class BlockEngineStone extends BlockBCTile implements ICustomRotationHandler {

    public BlockEngineStone(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING);
    }

    /** The real per-facing shape, replacing the default full-cube collision/outline this block had no override
     * for at all until now -- see {@link EngineShapes}'s own javadoc for why and how it's derived. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EngineShapes.get(state.getValue(BlockStateProperties.FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineStone(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileEngineStone engine) {
                engine.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineStone engine) {
            engine.onPlacedBy(placer, stack);
        }
    }

    @Override
    protected void neighborChanged(
        BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineStone engine) {
            engine.onNeighbourBlockChanged();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineStone engine) {
            player.openMenu(engine);
        }
        return InteractionResult.SUCCESS;
    }

    // ICustomRotationHandler

    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (level.getBlockEntity(pos) instanceof TileEngineStone engine) {
            return engine.attemptRotation();
        }
        return InteractionResult.FAIL;
    }
}
