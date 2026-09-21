/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.world;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * A {@link BlockGetter} for getting the properties of a single {@link BlockState} at
 * {@link SingleBlockAccess#POS}.
 *
 * <p>1.12.2's {@code IBlockAccess} also carried {@code getCombinedLight}, {@code getBiome},
 * {@code getStrongPower} and {@code isSideSolid}/{@code getWorldType}. None of those survive on the modern
 * {@link BlockGetter} contract: light is computed by the level's light engine rather than synthesised from a
 * caller-supplied value, biomes are registry entries rather than static instances (see {@code Biomes}, now a
 * table of {@code ResourceKey<Biome>}), redstone strength lives on the separate {@code SignalGetter} interface,
 * and {@code WorldType} was removed in 1.16. The two that still make sense for a single fake block --
 * "is this side sturdy" and "is this a redstone source" -- are kept as plain methods below, just no longer as
 * interface overrides, since nothing declares them any more.
 */
public class SingleBlockAccess implements BlockGetter {
    public static final BlockPos POS = BlockPos.ZERO;
    public final BlockState state;

    public SingleBlockAccess(BlockState state) {
        this.state = state;
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return POS.equals(pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return POS.equals(pos) ? state.getFluidState() : Fluids.EMPTY.defaultFluidState();
    }

    public boolean isAirBlock(BlockPos pos) {
        return getBlockState(pos).isAir();
    }

    /** Always 0: there is nothing beside the single block to source a redstone signal from. */
    public int getStrongPower(BlockPos pos, Direction direction) {
        return 0;
    }

    public boolean isSideSolid(BlockPos pos, Direction side, boolean _default) {
        if (POS.equals(pos)) {
            return _default;
        }
        return state.isFaceSturdy(this, pos, side);
    }

    /**
     * {@link #POS} is always {@link BlockPos#ZERO}, so any sane height range covers it. Height and min-Y are
     * only queried for build-height bounds checks, so these track the overworld's dimensions rather than
     * meaning anything about the fake single-block world itself.
     */
    @Override
    public int getHeight() {
        return 384;
    }

    @Override
    public int getMinY() {
        return -64;
    }
}
