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

/**
 * Port of 1.12.2's {@code buildcraft.silicon.plug.FacadePhasedState}: one entry of a (possibly-phased) facade --
 * a {@link FacadeBlockStateInfo} plus the wire colour that shows it, or {@code null} for the default.
 *
 * <p>{@code writeToBuffer}/{@code readFromBuffer} are not ported -- {@link PluggableFacade} never uses
 * {@link buildcraft.api.transport.pluggable.PipePluggable#writeCreationPayload}, since this whole pluggable
 * family syncs through {@code TilePipeHolder}'s own whole-tile NBT resync (see that class's own javadoc), not a
 * bespoke per-pluggable network payload. {@code writeBlockState} needs no registry lookup on this target
 * (confirmed via {@code javap} against {@code NbtUtils}: it only needs one to *read* a state back, to resolve the
 * registry-name string against a real {@code Block}), so {@link BuiltInRegistries#BLOCK} -- already a real
 * {@code HolderGetter<Block>} (it implements {@code HolderLookup.RegistryLookup}, confirmed via {@code javap}) --
 * is used directly rather than threading a {@code HolderLookup.Provider} through every facade class just for
 * this. This also lets {@link buildcraft.transport.item.ItemPluggableFacade#getFacade(net.minecraft.world.item.ItemStack)}
 * satisfy {@code IFacadeItem}'s registry-free signature.
 */
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
            BlockState blockState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, nbt.getCompoundOrEmpty("state"));
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
