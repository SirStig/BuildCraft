/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCTile;
import buildcraft.lib.block.IBlockWithFacing;

import buildcraft.factory.tile.TileChute;

/**
 * The first {@code buildcraft.factory} block in this port -- a 4-slot item buffer that pulls dropped items in
 * through its open face and pushes them into whatever neighbouring inventory will take them; see
 * {@link TileChute}'s own javadoc for the machine logic.
 *
 * <p>Three things are deliberately dropped from the 1.12.2 original, each for a reason already established
 * elsewhere in this port rather than a new kind of gap:
 *
 * <ul>
 * <li><b>{@code onBlockActivated} opening a GUI.</b> Nothing ported so far has a GUI/container framework at all
 *     (see PORTING.md's {@code buildcraft.lib.gui} entries) -- this is simply the first block that *would* have
 *     wanted one. Right-clicking a chute is a no-op for now; there is no override here at all, matching how
 *     {@code TileEngineCreative}'s wrench-cycle feature and {@code ItemMarkerConnector}'s volume-box editor were
 *     each deferred without needing a GUI either, except this is the first deferral that genuinely would have
 *     needed one.</li>
 * <li><b>The per-side {@code CONNECTED_MAP} blockstate</b> (a purely cosmetic "this face visually touches an
 *     inventory" indicator, synthesised in 1.12.2's {@code getActualState}, which no longer exists at all -- see
 *     PORTING.md's structural-changes list). Unlike {@code TileMarkerVolume}'s {@code ACTIVE} property (which
 *     mattered for connection *logic*, not just looks), nothing reads {@code CONNECTED_MAP} for gameplay
 *     purposes, and there is no renderer to make the visual distinction visible anyway -- so it is dropped
 *     outright rather than reproduced as an always-pushed real blockstate. {@code TileChute.hasInventoryAtPosition}
 *     (1.12.2's only caller of the neighbour scan {@code CONNECTED_MAP} needed) is dropped with it.</li>
 * <li><b>A custom {@code VoxelShape}.</b> The real 1.12.2 model ({@code buildcraft_resources/assets/
 *     buildcraftfactory/models/block/chute.json}) is a genuine stepped funnel, not a cube, and is ported
 *     faithfully as a real block model below. But nothing in this pass exercises collision fidelity: item
 *     pickup scans an {@code AABB} sitting *above* the block ({@code BoundingBoxUtil.extrudeFace}), never the
 *     block's own volume, so a full-cube collision/light-occlusion shape costs nothing functionally. A faithful
 *     rotated composite shape across all six facings would be real extra engineering for a purely cosmetic gap
 *     with no Java renderer to show it off either way -- the same call {@code BlockEngineWood} already made for
 *     its own non-cube 1.12.2 render type. {@link BlockBehaviour.Properties#noOcclusion()}, set where this block
 *     is registered, keeps the one non-cosmetic piece of {@code isOpaqueCube() -> false} (light does not treat
 *     this block as a full occluder).</li>
 * </ul>
 */
public class BlockChute extends BlockBCTile implements IBlockWithFacing {

    public BlockChute(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BuildCraftProperties.BLOCK_FACING_6);
    }

    /** The real per-facing shape -- see {@link ChuteShapes}'s own javadoc for why this was missing and how it
     * is derived from the block's own already-real hopper-funnel model geometry. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ChuteShapes.get(state.getValue(BuildCraftProperties.BLOCK_FACING_6));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /** Simplified from 1.12.2's player-eye-height-based placement logic (in the unported
     * {@code BlockBCBase_Neptune#getStateForPlacement}) to the clicked face directly, matching
     * {@code BlockMarkerBase}'s already-established precedent for the same simplification. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING_6, context.getClickedFace());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileChute(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileChute chute) {
                chute.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileChute chute) {
            chute.onPlacedBy(placer);
        }
    }

    // IBlockWithFacing

    @Override
    public boolean canFaceVertically() {
        return true;
    }
}
