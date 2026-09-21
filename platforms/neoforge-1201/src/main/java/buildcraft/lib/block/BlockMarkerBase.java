/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.block;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.api.properties.BuildCraftProperties;

/** Shared base for both marker blocks ({@code BlockMarkerVolume}/{@code BlockMarkerPath}): the per-facing
 * "stick" shape, the {@link BuildCraftProperties#BLOCK_FACING_6}/{@link BuildCraftProperties#ACTIVE} blockstate
 * properties, placement facing, and self-destruction when the block it is stuck to is removed.
 *
 * <p>Mirrors the 26.x class of the same name -- see that one for the full account of what changed from 1.12.2
 * (the {@code AxisAlignedBB} -> {@link VoxelShape} shape conversion, {@code getActualState}'s removal, rotation
 * through {@link IBlockWithFacing} rather than a hand-rolled {@code attemptRotation}, and
 * {@code World#isSideSolid} -> {@link BlockState#isFaceSturdy}). The one real difference on this target is
 * {@link #neighborChanged}'s signature: 1.20.1 still carries {@code fromPos}/{@code isMoving} rather than 26.x's
 * {@code Orientation} (confirmed via {@code javap} against {@code BlockBehaviour} on both targets) -- this class
 * never used {@code fromPos} either way. Every {@code BlockBehaviour} hook this class overrides
 * ({@code getShape}/{@code getCollisionShape}/{@code canSurvive}/{@code neighborChanged}) is also {@code public}
 * here rather than 26.x's {@code protected}, part of the same encapsulation pass documented in
 * {@code ItemWrench}'s divergence notes. */
public abstract class BlockMarkerBase extends BlockBCTile implements IBlockWithFacing {
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        double halfWidth = 0.1;
        double h = 0.65;
        // Little variables to make reading a *bit* more sane
        final double nw = 0.5 - halfWidth;
        final double pw = 0.5 + halfWidth;
        final double ih = 1 - h;
        SHAPES.put(Direction.DOWN, Shapes.box(nw, ih, nw, pw, 1, pw));
        SHAPES.put(Direction.UP, Shapes.box(nw, 0, nw, pw, h, pw));
        SHAPES.put(Direction.SOUTH, Shapes.box(nw, nw, 0, pw, pw, h));
        SHAPES.put(Direction.NORTH, Shapes.box(nw, nw, ih, pw, pw, 1));
        SHAPES.put(Direction.EAST, Shapes.box(0, nw, nw, h, pw, pw));
        SHAPES.put(Direction.WEST, Shapes.box(ih, nw, nw, 1, pw, pw));
    }

    protected BlockMarkerBase(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP)
            .setValue(BuildCraftProperties.ACTIVE, false));
    }

    @Override
    public boolean canFaceVertically() {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BuildCraftProperties.BLOCK_FACING_6, BuildCraftProperties.ACTIVE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(BuildCraftProperties.BLOCK_FACING_6));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING_6, context.getClickedFace());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canAttachTo(level, pos, state.getValue(BuildCraftProperties.BLOCK_FACING_6));
    }

    private static boolean canAttachTo(LevelReader level, BlockPos pos, Direction sideOn) {
        BlockPos supportPos = pos.relative(sideOn.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, sideOn);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        if (level.getBlockState(pos).is(this) && !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true, null, Block.UPDATE_LIMIT);
        }
    }
}
