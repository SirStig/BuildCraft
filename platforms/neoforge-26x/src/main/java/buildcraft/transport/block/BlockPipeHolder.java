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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

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
 * The single block every pipe kind shares -- the real 1.12.2 architecture ({@code BlockPipeHolder}/
 * {@code TilePipeHolder} is one shared block/tile pair, not one block per material; see
 * {@code common/buildcraft/transport/pipe/PipeRegistry.java}/{@code Pipe.java} for the original's own proof of
 * this) -- with a plain full-cube collision/placement shape, matching {@code BlockTank}/{@code BlockPump}'s own
 * "no renderer yet" precedent. 1.12.2's real block ({@code common/buildcraft/transport/block/BlockPipeHolder.java},
 * 600+ lines) is almost entirely rendering (per-octant collision boxes for pipe/wire/pluggable selection, paint
 * handling, particle spawning) and pluggable interaction, none of which is in this batch's scope -- see this
 * package's own module-level scope notes.
 *
 * <p><b>Connection-shape rendering.</b> Six vanilla direction booleans ({@link BlockStateProperties#NORTH}/
 * {@code SOUTH}/{@code EAST}/{@code WEST}/{@code UP}/{@code DOWN} -- confirmed by {@code javap} against the real
 * jar on both targets to be {@code BooleanProperty} instances already used by vanilla {@code IronBarsBlock}/
 * {@code GlassPaneBlock}/{@code ChorusPlantBlock} for exactly this "is there a connection this direction" purpose,
 * so reused directly rather than declaring six BuildCraft-native ones) plus {@link #MATERIAL}, a new
 * {@link EnumPipeMaterial} property, are declared here purely so {@code pipe_holder.json}'s own {@code multipart}
 * blockstate can pick the right per-material centre-cube model and rotate one arm-stub model per connected
 * direction -- the same "the block never reads its own property, {@code Pipe} pushes it onto the placed state
 * directly" pattern {@code BlockEngineWood}'s own {@code FACING} declaration already established for the engine
 * facing-visibility batch. See {@code TilePipeHolder#updateConnectionBlockState}/{@code Pipe#updateConnections}
 * for where the real values actually get pushed. {@link #ACTIVE} (the directional pipes' "filled" face) follows
 * the same pattern -- see its own javadoc.
 *
 * <p><b>Wrench rotation</b> ({@link ICustomRotationHandler}): see {@link #attemptRotation}.
 */
public class BlockPipeHolder extends BlockBCTile implements ICustomRotationHandler {

    /** New, port-only: see this class's own "Connection-shape rendering" javadoc entry above. Not a vanilla
     * property -- nothing in {@code BlockStateProperties} fits a five-value pipe-material enum, matching this
     * port's already-established {@code EnumEngineType}/{@code BuildCraftProperties} precedent for a custom
     * enum blockstate property, just kept local to {@code buildcraft.transport} (see {@link EnumPipeMaterial}'s
     * own javadoc for why). */
    public static final EnumProperty<EnumPipeMaterial> MATERIAL = EnumProperty.create("material", EnumPipeMaterial.class);

    /** New, port-only: the active face of a {@link PipeBehaviourDirectional} pipe (wood, iron), or
     * {@link EnumPipeActiveFace#NONE} -- always {@code NONE} for every other material. Pushed by
     * {@code TilePipeHolder#updateConnectionBlockState} exactly like {@link #MATERIAL}; {@code pipe_holder.json}
     * uses it to swap that one direction's arm onto the material's {@code _filled} arm model, reproducing
     * 1.12.2's clear/filled face textures. Multiplies the state count by 7: 18 materials (9 item + 9 fluid) x 64
     * connection combinations x 7 = 8064 states (4032 before the fluid pipes) -- still acceptable for a single
     * block (the state table is built once at startup; vanilla redstone wire, for comparison, has 1296), and far
     * simpler than a custom baked model. */
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
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TilePipeHolder holder) {
            holder.onNeighbourChanged();
        }
    }

    /** Drops the actual placed pipe material's own item, read live from the tile's own {@code Pipe}, instead of
     * trusting {@code pipe_holder.json}'s static loot table -- a real, load-bearing bug on file in PORTING.md
     * since the wood-pipe batch ("harmless while cobblestone is the only material"), now fixed because this
     * batch's three new materials make it genuinely wrong: a static JSON loot table has no way to read which
     * {@link PipeDefinition} is actually stamped onto a given tile's NBT at break time, so it always dropped a
     * hard-coded {@code buildcraft:pipe_item_cobblestone} regardless of what was actually broken.
     * {@code BlockBehaviour#getDrops(BlockState, LootParams.Builder)} -- confirmed, by reading the real
     * decompiled {@code Block}/{@code BlockBehaviour} source, to be exactly what
     * {@code Block#getDrops(BlockState, ServerLevel, BlockPos, BlockEntity)}/{@code BlockState#getDrops}
     * delegate to, with the block entity already threaded through as
     * {@link LootContextParams#BLOCK_ENTITY} -- is the real, modern, per-block-instance override point for
     * this, not {@code playerWillDestroy} (which only gets to see the state/position, not the tile, and exists
     * to let a block react to being destroyed, not to decide what it drops).
     *
     * <p>Falls back to the static loot table (via {@code super.getDrops}) whenever no {@code Pipe} is actually
     * present -- the tile's own {@code pipe} field is {@code @Nullable} and stays {@code null} until a real
     * placement (a player's click, or the direct {@code TilePipeHolder#onPlacedBy} call this batch's own RCON
     * rig uses) stamps a {@link PipeDefinition} onto it, so a bare {@code /setblock buildcraft:pipe_holder}
     * with no pipe ever assigned falls through to the same cobblestone default this block always had, rather
     * than dropping nothing at all for a case that (per this class's own placement contract) should not arise
     * in normal survival play. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
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
            // Every attached pluggable drops itself too -- see TilePipeHolder#getPluggables' own javadoc.
            NonNullList<ItemStack> pluggableDrops = NonNullList.create();
            for (PipePluggable plug : holder.getPluggables().values()) {
                plug.addDrops(pluggableDrops, 0);
            }
            drops.addAll(pluggableDrops);
            return drops;
        }
        return super.getDrops(state, params);
    }

    // ICustomRotationHandler

    /**
     * Cycles a directional pipe's (wood, iron) active face with a wrench -- 1.12.2's
     * {@code PipeBehaviourDirectional#onPipeActivate} wrench branch, reached through a different door. Confirmed by
     * reading the real call chain rather than assumed: {@code ItemWrench#useOn} calls
     * {@code CustomRotationHelper.INSTANCE.attemptRotateBlock}, whose first check is
     * {@code block instanceof ICustomRotationHandler} -> {@code attemptRotation}, and the vanilla
     * {@code ServerPlayerGameMode#useItemOn} only reaches {@code Item#useOn} at all because this block keeps the
     * default {@code useItemOn} ({@code TRY_WITH_EMPTY_HAND}) and {@code useWithoutItem} ({@code PASS}) -- so this
     * is the one hook a wrench right-click actually lands on, without touching {@code ItemWrench} itself. Same
     * route {@code BlockEngineWood}/{@code BlockEngineCreative} already use.
     *
     * <p>{@code sideWrenched} is ignored: every wrench use cycles (see {@link PipeBehaviourDirectional}'s own
     * javadoc for why the "click an arm to pick that face" branch cannot be reproduced on a full-cube block).
     * Returns {@code PASS} for every non-directional pipe (and a tile with no {@code Pipe} at all), so the wrench
     * does nothing and plays no sound there. Client side, a directional pipe answers {@code SUCCESS} without
     * mutating anything (the arm swings; the server decides the new face and syncs it back), matching the usual
     * vanilla "predict success, let the server act" shape rather than letting the client's own, never-ticked
     * copy of the pipe guess at connections.
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
     * Activates whatever {@link PipePluggable} already sits on the clicked face (a gate's GUI, a copier) -- tried
     * before {@link #useItemOn} regardless of what the player is holding, matching {@code PluggableGate}'s own
     * 1.12.2 precedent of always intercepting a click on its own face. Falls through to
     * {@link #activatePipeBehaviour} (a right-click with no pluggable in the way -- new for the diamond pipes'
     * own filter GUI, see {@link buildcraft.transport.pipe.behaviour.PipeBehaviourDiamond}'s own javadoc) and then
     * {@link #useItemOn} (by returning {@link InteractionResult#PASS}) whenever there is nothing to activate, so
     * an empty face can still be interacted with -- e.g. to place a new pluggable there.
     */
    @Override
    protected InteractionResult useWithoutItem(
        BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult
    ) {
        InteractionResult activated = activateExistingPluggable(level, pos, player, hitResult);
        if (activated != InteractionResult.PASS) {
            return activated;
        }
        return activatePipeBehaviour(level, pos, player, hitResult);
    }

    /**
     * {@link IItemPluggable} dispatch: attaches whatever {@link PipePluggable} the held item builds to the
     * clicked face, first giving an already-attached pluggable the chance to intercept the click instead (see
     * {@link #useWithoutItem}'s own javadoc -- this override exists because {@code useWithoutItem} runs with no
     * {@link ItemStack} at all, so activation has to be re-tried here too for the case where it is
     * {@code useItemOn}, not {@code useWithoutItem}, that the game actually dispatches to first), then
     * {@link #activatePipeBehaviour} before falling through to the placement dispatch below -- a diamond pipe's
     * own GUI opens for a right-click holding any item too (1.12.2's own {@code PipeBehaviourDiamond} has no
     * held-item guard either), which as a side effect means a plug cannot currently be placed onto a diamond
     * pipe's face by right-click (matching 1.12.2's own behaviour: its {@code onPipeActivate} unconditionally
     * returns {@code true} there too, consuming the whole interaction before any placement logic runs).
     */
    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
        BlockHitResult hitResult
    ) {
        InteractionResult activated = activateExistingPluggable(level, pos, player, hitResult);
        if (activated != InteractionResult.PASS) {
            return activated;
        }
        InteractionResult behaviourActivated = activatePipeBehaviour(level, pos, player, hitResult);
        if (behaviourActivated != InteractionResult.PASS) {
            return behaviourActivated;
        }
        if (!(level.getBlockEntity(pos) instanceof TilePipeHolder holder)) {
            return InteractionResult.PASS;
        }
        Direction side = hitResult.getDirection();
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

    private static InteractionResult activateExistingPluggable(
        Level level, BlockPos pos, Player player, BlockHitResult hitResult
    ) {
        if (!(level.getBlockEntity(pos) instanceof TilePipeHolder holder)) {
            return InteractionResult.PASS;
        }
        PipePluggable plug = holder.getPluggable(hitResult.getDirection());
        if (plug == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return plug.onPluggableActivate(player, hitResult) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * New: dispatches to {@code PipeBehaviour#onPipeActivate}, and opens {@code TilePipeHolder}'s own
     * {@code MenuProvider} menu (server-side only) when it answers {@code true} -- the diamond pipes' filter GUI.
     * Every other material's behaviour still answers {@code false} by default ({@link PipeBehaviour}'s own base
     * implementation), so this is a pure addition: {@code PASS} for everything that isn't a diamond pipe, exactly
     * as before this batch. {@code EnumPipePart.CENTER} always, matching {@code attemptRotation}'s own
     * {@code sideWrenched}-is-ignored precedent above -- see {@code PipeBehaviourDirectional}'s own javadoc for
     * why this full-cube block cannot tell which arm was actually clicked.
     */
    private static InteractionResult activatePipeBehaviour(
        Level level, BlockPos pos, Player player, BlockHitResult hitResult
    ) {
        if (!(level.getBlockEntity(pos) instanceof TilePipeHolder holder)) {
            return InteractionResult.PASS;
        }
        IPipe pipe = holder.getPipe();
        if (pipe == null || !pipe.getBehaviour().onPipeActivate(player, hitResult, EnumPipePart.CENTER)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(holder);
        return InteractionResult.SUCCESS;
    }
}
