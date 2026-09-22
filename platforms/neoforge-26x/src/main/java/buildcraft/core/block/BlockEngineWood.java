/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import buildcraft.api.blocks.ICustomRotationHandler;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.core.tile.TileEngineWood;

/**
 * Renamed from 1.12.2's shared, multi-variant {@code BlockEngine_BC8} -- see {@link TileEngineWood}'s javadoc for
 * why the WOOD variant becomes its own real block/item pair here rather than a metadata subtype of one shared
 * block, the same split {@code BlockSpringWater}/{@code BlockDecoration} already established for other
 * metadata-subtyped 1.12.2 blocks.
 *
 * <p>No {@link #getShape}/{@link #getCollisionShape} override: this port has no custom-rendering pipeline to feed
 * 1.12.2's {@code EnumBlockRenderType.ENTITYBLOCK_ANIMATED} model, so this uses an ordinary static block model,
 * and the default full-cube shape {@code Block} already gives every block that doesn't override it is exactly
 * what's wanted here -- see {@link TileEngineWood}'s javadoc for the fuller "block shape"/face-solidity research
 * (real, decompiled-source-confirmed: sturdiness has no per-block override point left on either target at all, so
 * reproducing 1.12.2's "only the back face is solid" would need a custom {@code VoxelShape} with no renderer to
 * justify the geometry). {@link #setPlacedBy}/{@link #neighborChanged} both call the tile directly, since
 * {@code BlockEntity} lost {@code onPlacedBy}/{@code onNeighbourBlockChanged} entirely -- the same "the block
 * calls the tile's own hook" pattern {@code BlockMarkerVolume} already established.
 */
public class BlockEngineWood extends BlockBCTile implements ICustomRotationHandler {

    public BlockEngineWood(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEngineWood(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileEngineWood engine) {
                engine.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineWood engine) {
            engine.onPlacedBy(placer, stack);
        }
    }

    @Override
    protected void neighborChanged(
        BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEngineWood engine) {
            engine.onNeighbourBlockChanged();
        }
    }

    // ICustomRotationHandler

    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (level.getBlockEntity(pos) instanceof TileEngineWood engine) {
            return engine.attemptRotation();
        }
        return InteractionResult.FAIL;
    }
}
