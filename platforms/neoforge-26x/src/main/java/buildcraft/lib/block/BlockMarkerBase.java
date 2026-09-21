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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import buildcraft.api.properties.BuildCraftProperties;

/** Shared base for both marker blocks ({@code BlockMarkerVolume}/{@code BlockMarkerPath}): the per-facing
 * "stick" shape, the {@link BuildCraftProperties#BLOCK_FACING_6}/{@link BuildCraftProperties#ACTIVE} blockstate
 * properties, placement facing, and self-destruction when the block it is stuck to is removed.
 *
 * <p>{@code IBlockAccess}/{@code AxisAlignedBB} shape methods (0-1-scaled {@code getBoundingBox}/
 * {@code getCollisionBoundingBox}) are gone; the modern equivalents ({@code getShape}/{@code getCollisionShape})
 * return a {@link VoxelShape}, built with {@link Shapes#box}, which -- confirmed via the real decompiled
 * {@code Block#box} (it divides its own pixel-scale arguments by 16 before calling {@link Shapes#box}) --
 * itself already takes 0-1-scaled coordinates, so the six original {@code AxisAlignedBB} literals carry over
 * completely unchanged, just re-wrapped. {@link #getCollisionShape} returns {@link Shapes#empty()}, the modern
 * equivalent of 1.12.2's {@code getCollisionBoundingBox} returning {@code null} (no collision at all, so
 * entities walk straight through -- torches work the same way).
 *
 * <p>{@code getActualState} (which synthesised {@link BuildCraftProperties#ACTIVE} from the tile's
 * {@code isActiveForRender()} at render time) no longer exists -- {@code IBlockState#getActualState} is gone
 * entirely, see PORTING.md's structural-changes list. There is no per-render-frame state override any more, so
 * {@code ACTIVE} instead has to be a real, persisted blockstate value that the owning tile pushes explicitly
 * whenever the underlying condition changes. See {@code TileMarkerVolume}/{@code TileMarkerPath}'s own javadoc
 * for exactly where that push happens; this class only declares the property and its default.
 *
 * <p>Rotation is handled through {@link IBlockWithFacing} rather than a hand-rolled
 * {@code ICustomRotationHandler#attemptRotation} override: that interface already exists in this port
 * specifically to wrench-rotate a block carrying {@link BuildCraftProperties#BLOCK_FACING_6}/
 * {@link BuildCraftProperties#BLOCK_FACING}, cycling through {@code RotationUtil#rotateAll} for the 6-facing
 * case -- a different cycle order than 1.12.2's {@code VanillaRotationHandlers.ROTATE_FACING}
 * (east-south-down-west-north-up there, vs. north-east-south-west-up-down here), but the same "cycle through
 * every face" behaviour, and reusing an already-ported, already-used mechanism beats reimplementing one that
 * happens to order its cycle differently.
 *
 * <p>{@code World#isSideSolid(pos, side)} (used by {@code canPlaceBlockOnSide}) doesn't exist any more; the
 * modern equivalent is {@link BlockState#isFaceSturdy(BlockGetter, BlockPos, Direction)} on the *neighbouring*
 * block's state, confirmed via {@code javap} on both targets. That check is wired into {@link #canSurvive}
 * (a real placement-validity hook 1.12.2 didn't have here at all) as well as {@link #neighborChanged}, matching
 * the pattern vanilla's own {@code DiodeBlock#neighborChanged} uses for the same
 * "destroy myself if my support disappeared" behaviour.
 *
 * <p>{@link #neighborChanged}'s 26.x signature dropped {@code fromPos} entirely, replacing it with an
 * {@link Orientation} (confirmed via {@code javap} against {@code BlockBehaviour} -- neither of 1.12.2's
 * {@code fromPos} nor 1.20.1's copy of it survives here); this class never used {@code fromPos} anyway, so the
 * change is invisible to this file.
 *
 * <p>1.12.2's {@code getBlockLayer() -> BlockRenderLayer.CUTOUT} has no equivalent override here: cutout/solid/
 * translucent render-layer assignment moved to client-side registration
 * ({@code ItemBlockRenderTypes.setRenderShape}) sometime after 1.12.2, and there is no client-side mod
 * initialisation in this tree yet at all (this package is otherwise common/server code) -- it comes back with
 * the rendering pass. */
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(BuildCraftProperties.BLOCK_FACING_6));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BuildCraftProperties.BLOCK_FACING_6, context.getClickedFace());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canAttachTo(level, pos, state.getValue(BuildCraftProperties.BLOCK_FACING_6));
    }

    private static boolean canAttachTo(LevelReader level, BlockPos pos, Direction sideOn) {
        BlockPos supportPos = pos.relative(sideOn.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, sideOn);
    }

    @Override
    protected void neighborChanged(
        BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston
    ) {
        if (level.getBlockState(pos).is(this) && !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true, null, Block.UPDATE_LIMIT);
        }
    }
}
