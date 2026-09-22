/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team This Source Code Form is subject to the terms of the Mozilla
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.BCTransportRegistries;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * The single block every pipe kind shares -- see the 26.x copy of this class for the full account of the real
 * 1.12.2 one-block-many-items architecture and why this is a plain full cube.
 *
 * <p><b>Connection-shape rendering.</b> Identical to the 26.x copy of this class -- see its own "Connection-shape
 * rendering" javadoc entry for the full account: six vanilla direction booleans ({@link BlockStateProperties#NORTH}/
 * {@code SOUTH}/{@code EAST}/{@code WEST}/{@code UP}/{@code DOWN}, confirmed identical {@code BooleanProperty}
 * instances via {@code javap} against the real 1.20.1-Forge-fork universal jar) plus {@link #MATERIAL}, a new
 * {@link EnumPipeMaterial} property, both declared here and pushed onto the real placed state from
 * {@code TilePipeHolder#updateConnectionBlockState}/{@code Pipe#updateConnections}, never read by this block
 * itself.
 */
public class BlockPipeHolder extends BlockBCTile {

    /** See the 26.x copy of this class's own javadoc for {@link #MATERIAL}. */
    public static final EnumProperty<EnumPipeMaterial> MATERIAL = EnumProperty.create("material", EnumPipeMaterial.class);

    public BlockPipeHolder(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(MATERIAL, EnumPipeMaterial.COBBLESTONE)
            .setValue(BlockStateProperties.NORTH, false)
            .setValue(BlockStateProperties.SOUTH, false)
            .setValue(BlockStateProperties.EAST, false)
            .setValue(BlockStateProperties.WEST, false)
            .setValue(BlockStateProperties.UP, false)
            .setValue(BlockStateProperties.DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
            MATERIAL, BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.EAST,
            BlockStateProperties.WEST, BlockStateProperties.UP, BlockStateProperties.DOWN
        );
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TilePipeHolder(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TilePipeHolder holder) {
                holder.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TilePipeHolder holder) {
            holder.onPlacedBy(placer, stack);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TilePipeHolder holder) {
            holder.onNeighbourChanged();
        }
    }

    /** Drops the actual placed pipe material's own item, read live from the tile's own {@code Pipe} -- see the
     * 26.x copy of this class's own javadoc for the full account of the {@code pipe_holder.json} static-loot-
     * table bug this fixes and why {@code BlockBehaviour#getDrops(BlockState, LootParams.Builder)} (here
     * {@code public}, not {@code protected} as on 26.x -- confirmed by {@code javap} against the real
     * 1.20.1-Forge-fork jar) is the right hook rather than {@code playerWillDestroy}. Falls back to the static
     * loot table via {@code super.getDrops} when no {@code Pipe} is present, for the identical reason given on
     * 26.x. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof TilePipeHolder holder) {
            IPipe pipe = holder.getPipe();
            if (pipe != null) {
                PipeDefinition definition = pipe.getDefinition();
                ItemPipeHolder item = BCTransportRegistries.getItemForPipe(definition);
                if (item != null) {
                    return List.of(new ItemStack(item));
                }
            }
        }
        return super.getDrops(state, params);
    }
}
