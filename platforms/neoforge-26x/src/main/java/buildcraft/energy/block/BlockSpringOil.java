/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.enums.EnumSpring;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.energy.tile.TileSpringOil;

/**
 * The oil half of 1.12.2's single metadata-subtyped {@code BlockSpring} -- see {@code BlockSpringWater}'s own
 * javadoc for why each spring type became its own real block instead of a blockstate property, and for why the
 * oil half specifically needed its own design: unlike {@link BlockSpringWater} (no block entity at all), this one
 * always has a {@link TileSpringOil}, so it extends {@link BlockBCTile} rather than a plain {@code Block}.
 *
 * <p>Tick behaviour reads {@link EnumSpring#OIL}'s {@code tickRate}/{@code chance}/{@code canGen} fields directly,
 * rather than hardcoding water's own {@code tickRate = 5}/"always spread" constants the way {@code
 * BlockSpringWater} does -- oil's real 1.12.2 values ({@code tickRate = 6000}, {@code chance = 8}, i.e. roughly a
 * 1-in-8 chance to spread every 300 seconds) are meaningfully different from water's, and both this class and
 * {@code core.gen.OilSpringGenerator} (the world-gen feature that places this block) read the same enum fields, so
 * there is exactly one place either would ever need updating. {@link EnumSpring#OIL}'s {@code liquidBlock} is set
 * by {@code BCEnergyRegistries} once the crude-oil fluid block exists -- see that enum's own javadoc for why it
 * has to stay {@code null} until the energy module (this one) fills it in.
 */
public class BlockSpringOil extends BlockBCTile {

    public BlockSpringOil(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileSpringOil(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, this, EnumSpring.OIL.tickRate);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, EnumSpring.OIL.tickRate);
        if (!EnumSpring.OIL.canGen || EnumSpring.OIL.liquidBlock == null) {
            return;
        }
        if (!level.getBlockState(pos.above()).isAir()) {
            return;
        }
        if (EnumSpring.OIL.chance != -1 && random.nextInt(EnumSpring.OIL.chance) != 0) {
            return;
        }
        level.setBlock(pos.above(), EnumSpring.OIL.liquidBlock, 3);
    }
}
