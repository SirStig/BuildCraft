/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * The placeable in-world block for a BuildCraft fluid -- 1.12.2's {@code BlockFluidClassic} subclass, now a vanilla
 * {@link LiquidBlock}, which already does everything {@code BlockFluidClassic} used to (flowing, source/level
 * state, bucket pickup) off the {@link FlowingFluid} it wraps.
 *
 * <p>Kept from 1.12.2: flammability. {@code BCMaterialFluid#setBurning} plus {@code getFlammability}/
 * {@code getFireSpreadSpeed} returning {@code 200} for a burnable material become the NeoForge
 * {@code IBlockExtension} overrides below (plus {@code ignitedByLava()} on the block properties, set by
 * {@code BCEnergyFluids}, which is what {@code Material#getCanBurn} meant for lava ignition).
 *
 * <p>Not carried over, each deliberately: the {@code displacements} map (1.12.2 let a denser-than-water fluid push
 * water aside; vanilla {@code FlowingFluid} has no displacement concept and {@code BaseFlowingFluid} already never
 * lets another fluid replace ours sideways); {@code isEntityInsideMaterial} returning "water" (NeoForge's
 * {@code FluidType} now drives swimming/drag for every fluid generically); {@code renderLayer = SOLID} (26.x picks
 * the chunk layer from the sprite's own transparency -- the recoloured sprites are fully opaque, so the result is
 * the same {@code SOLID}); and the {@code sticky} web-like drag, which 1.12.2 only ever enabled behind the
 * {@code general.oilIsDense} config option, default {@code false}, and this port has no energy config yet.
 */
public class BCFluidBlock extends LiquidBlock {

    private final boolean flammable;

    public BCFluidBlock(FlowingFluid fluid, boolean flammable, Properties properties) {
        super(fluid, properties);
        this.flammable = flammable;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammable ? 200 : 0;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammable ? 200 : 0;
    }

    /** Named after the fluid, heat suffix included, instead of needing a separate {@code block.buildcraft.*} lang
     * key per heat variant. */
    @Override
    public MutableComponent getName() {
        return fluid.getFluidType().getDescription().copy();
    }
}
