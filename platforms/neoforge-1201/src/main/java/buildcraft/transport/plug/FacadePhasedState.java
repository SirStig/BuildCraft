/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.facades.IFacadePhasedState;
import buildcraft.api.facades.IFacadeState;

import buildcraft.lib.misc.NBTUtilBC;

/** Port of 1.12.2's {@code buildcraft.silicon.plug.FacadePhasedState} -- see the 26.x copy of this class for the
 * full account (why {@link BuiltInRegistries#BLOCK} is used directly rather than threading a
 * {@code HolderLookup.Provider} through, and why no buffer round-trip exists). One real per-platform divergence,
 * found by {@code javap} rather than assumed: on 26.x, {@code Registry<T>} itself already satisfies
 * {@code HolderGetter<T>} (it extends {@code HolderLookup.RegistryLookup<T>} directly); on this target's older
 * registry hierarchy, {@code DefaultedRegistry<Block>} does not, and needs its own {@link
 * net.minecraft.core.Registry#asLookup()} called first to get a {@code HolderLookup.RegistryLookup<Block>} (which
 * *does* extend {@code HolderGetter<Block>} on both targets). Otherwise only the classic {@code CompoundTag}
 * accessors differ ({@code contains}/{@code getCompound}, not the {@code OrEmpty} suffixed ones 26.x's newer NBT
 * API added). */
public class FacadePhasedState implements IFacadePhasedState {
    public final FacadeBlockStateInfo stateInfo;

    @Nullable
    public final DyeColor activeColour;

    public FacadePhasedState(FacadeBlockStateInfo stateInfo, @Nullable DyeColor activeColour) {
        this.stateInfo = stateInfo;
        this.activeColour = activeColour;
    }

    public static FacadePhasedState readFromNbt(CompoundTag nbt) {
        FacadeBlockStateInfo stateInfo = FacadeStateManager.defaultState;
        if (nbt.contains("state")) {
            BlockState blockState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), nbt.getCompound("state"));
            FacadeBlockStateInfo found = FacadeStateManager.validFacadeStates.get(blockState);
            if (found != null) {
                stateInfo = found;
            }
        }
        DyeColor colour = NBTUtilBC.readEnum(nbt.get("activeColour"), DyeColor.class);
        return new FacadePhasedState(stateInfo, colour);
    }

    public CompoundTag writeToNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("state", NbtUtils.writeBlockState(stateInfo.state));
        if (activeColour != null) {
            nbt.put("activeColour", NBTUtilBC.writeEnum(activeColour));
        }
        return nbt;
    }

    public FacadePhasedState withColour(@Nullable DyeColor colour) {
        return new FacadePhasedState(stateInfo, colour);
    }

    public boolean isSideSolid(net.minecraft.core.Direction side) {
        return stateInfo.isSideSolid[side.get3DDataValue()];
    }

    @Override
    public String toString() {
        return (activeColour == null ? "" : activeColour + " ") + getState();
    }

    // IFacadePhasedState

    @Override
    public IFacadeState getState() {
        return stateInfo;
    }

    @Override
    @Nullable
    public DyeColor getActiveColor() {
        return activeColour;
    }
}
