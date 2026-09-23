/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team This Source Code Form is subject to the terms of the Mozilla
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.network.NetworkHooks;

import buildcraft.api.blocks.ICustomRotationHandler;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pluggable.PipePluggable;

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

    /** The real per-connection shape -- see {@link PipeShapes}'s own javadoc for why this was missing and how
     * it is derived. Falls back to the full block only when there is genuinely no tile yet (mid-placement). */
    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
        BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return level.getBlockEntity(pos) instanceof TilePipeHolder holder
            ? PipeShapes.get(holder)
            : net.minecraft.world.phys.shapes.Shapes.block();
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level,
        BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return getShape(state, level, pos, context);
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
        List<ItemStack> drops = new ArrayList<>();
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof TilePipeHolder holder) {
            IPipe pipe = holder.getPipe();
            if (pipe != null) {
                PipeDefinition definition = pipe.getDefinition();
                ItemPipeHolder item = BCTransportRegistries.getItemForPipe(definition);
                if (item != null) {
                    drops.add(new ItemStack(item));
                }
            } else {
                drops.addAll(super.getDrops(state, params));
            }
            NonNullList<ItemStack> pluggableDrops = NonNullList.create();
            for (PipePluggable plug : holder.getPluggables().values()) {
                plug.addDrops(pluggableDrops, 0);
            }
            drops.addAll(pluggableDrops);
            return drops;
        }
        return super.getDrops(state, params);
    }

    /** See the 26.x copy of {@code BlockPipeHolder#preRemoveSideEffects} -- this target has no equivalent tile
     * hook (see PORTING.md's "Block-entity genuine-removal hook" divergence entry), so the block-level
     * {@code onRemove} is the right place instead. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TilePipeHolder holder) {
            holder.notifyPluggablesRemoved();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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

    // Pluggable interaction

    /**
     * Pluggable activate/place dispatch -- see the 26.x copy of this class's own {@code useWithoutItem}/
     * {@code useItemOn} javadoc for the full account (activate an existing pluggable on the clicked face first,
     * then try placing a new one from the held item). Both fold into this single {@code use} override on this
     * target, since 1.20.1 never split the vanilla dispatch the way 26.x did -- see this class's own
     * {@link #attemptRotation} javadoc, which already established that {@code BlockState#use} is the one real
     * entry point a right-click on this block reaches.
     */
    @Override
    public InteractionResult use(
        BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (!(level.getBlockEntity(pos) instanceof TilePipeHolder holder)) {
            return InteractionResult.PASS;
        }
        Direction side = hitResult.getDirection();
        PipePluggable existing = holder.getPluggable(side);
        if (existing != null) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return existing.onPluggableActivate(player, hitResult) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        InteractionResult behaviourActivated = activatePipeBehaviour(holder, level, pos, player, hitResult);
        if (behaviourActivated != InteractionResult.PASS) {
            return behaviourActivated;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof IItemPluggable itemPluggable)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        PipePluggable placed = itemPluggable.onPlace(stack, holder, side, player, hand);
        if (placed == null) {
            return InteractionResult.PASS;
        }
        holder.setPluggable(side, placed);
        placed.onPlacedBy(player);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /**
     * New: dispatches to {@code PipeBehaviour#onPipeActivate}, opening {@code TilePipeHolder}'s own
     * {@code MenuProvider} menu (server-side only, via {@code NetworkHooks.openScreen}, matching
     * {@code BlockDistiller}'s own precedent) when it answers {@code true} -- the diamond pipes' filter GUI. See
     * the 26.x copy of this method's own javadoc for the full account; {@code EnumPipePart.CENTER} always, for
     * the identical reason {@link #attemptRotation} already ignores {@code sideWrenched}.
     */
    private static InteractionResult activatePipeBehaviour(
        TilePipeHolder holder, Level level, BlockPos pos, Player player, BlockHitResult hitResult
    ) {
        IPipe pipe = holder.getPipe();
        if (pipe == null || !pipe.getBehaviour().onPipeActivate(player, hitResult, EnumPipePart.CENTER)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, holder, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
