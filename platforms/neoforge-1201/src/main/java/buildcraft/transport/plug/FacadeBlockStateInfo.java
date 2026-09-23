/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.facades.IFacadeState;

import buildcraft.lib.world.SingleBlockAccess;

/** Port of 1.12.2's {@code buildcraft.silicon.plug.FacadeBlockStateInfo} -- see the 26.x copy of this class for
 * the full account of the {@code varyingProperties}/{@code blockFaceShape} scope cuts. */
public class FacadeBlockStateInfo implements IFacadeState {
    public final BlockState state;
    public final ItemStack requiredStack;
    public final boolean isTransparent;
    public final boolean isVisible;
    public final boolean[] isSideSolid = new boolean[6];

    public FacadeBlockStateInfo(BlockState state, ItemStack requiredStack) {
        this.state = state;
        this.requiredStack = requiredStack;
        this.isTransparent = !state.canOcclude();
        this.isVisible = !requiredStack.isEmpty();
        BlockGetter access = new SingleBlockAccess(state);
        BlockPos pos = SingleBlockAccess.POS;
        for (Direction side : Direction.values()) {
            isSideSolid[side.get3DDataValue()] = state.isFaceSturdy(access, pos, side);
        }
    }

    public FacadePhasedState createPhased(net.minecraft.world.item.DyeColor activeColour) {
        return new FacadePhasedState(this, activeColour);
    }

    @Override
    public String toString() {
        return "StateInfo [id=" + System.identityHashCode(this) + ", block = " + state.getBlock() + ", state = "
            + state + "]";
    }

    // IFacadeState

    @Override
    public BlockState getBlockState() {
        return state;
    }

    @Override
    public boolean isTransparent() {
        return isTransparent;
    }

    @Override
    public ItemStack getRequiredStack() {
        return requiredStack;
    }
}
