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
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import buildcraft.api.blocks.ICustomRotationHandler;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.BCTransportRegistries;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDirectional;
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
 * itself. {@link #ACTIVE} (the directional pipes' "filled" face) follows the same pattern.
 *
 * <p><b>Wrench rotation</b> ({@link ICustomRotationHandler}): see {@link #attemptRotation}.
 */
public class BlockPipeHolder extends BlockBCTile implements ICustomRotationHandler {

    /** See the 26.x copy of this class's own javadoc for {@link #MATERIAL}. */
    public static final EnumProperty<EnumPipeMaterial> MATERIAL = EnumProperty.create("material", EnumPipeMaterial.class);

    /** See the 26.x copy of this class's own javadoc for {@link #ACTIVE}. */
    public static final EnumProperty<EnumPipeActiveFace> ACTIVE = EnumProperty.create("active", EnumPipeActiveFace.class);

    public BlockPipeHolder(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(MATERIAL, EnumPipeMaterial.COBBLESTONE)
            .setValue(ACTIVE, EnumPipeActiveFace.NONE)
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
            MATERIAL, ACTIVE, BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.EAST,
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

    // ICustomRotationHandler

    /**
     * Cycles a directional pipe's (wood, iron) active face with a wrench -- see the 26.x copy of this method for
     * the full account. The dispatch chain is the same on this target, confirmed against the real 1.20.1-Forge
     * sources rather than assumed: {@code ServerPlayerGameMode#useItemOn} tries {@code BlockState#use} first,
     * which this block leaves at {@code BlockBehaviour}'s default {@code PASS}, so the wrench's own
     * {@code ItemWrench#useOn} runs next and calls {@code CustomRotationHelper#attemptRotateBlock}, whose
     * {@code instanceof ICustomRotationHandler} check lands here. {@link InteractionResult} is a plain enum on this
     * target, and this target's {@code ItemWrench} tests {@code == InteractionResult.SUCCESS} exactly, so the
     * plain {@code SUCCESS} constant (not {@code sidedSuccess}/{@code CONSUME}) is what gets returned.
     */
    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        if (!(level.getBlockEntity(pos) instanceof TilePipeHolder holder)) {
            return InteractionResult.PASS;
        }
        IPipe pipe = holder.getPipe();
        if (pipe == null || !(pipe.getBehaviour() instanceof PipeBehaviourDirectional directional)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return directional.advanceFacing() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
}
