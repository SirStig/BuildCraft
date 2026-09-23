/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;

import buildcraft.api.facades.FacadeType;
import buildcraft.api.facades.IFacade;
import buildcraft.api.facades.IFacadePhasedState;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.plug.FacadeInstance}: the full disguise a facade item or placed
 * {@link PluggableFacade} carries -- one {@link FacadePhasedState} for a plain facade, several for a
 * wire-colour-phased one.
 *
 * <p><b>Scope cut, found while porting, not invented here:</b> nothing anywhere in 1.12.2's own
 * {@code buildcraft.silicon}/{@code buildcraft.transport} sources ever writes {@code PluggableFacade#activeState}
 * except its own NBT constructor (confirmed by a repo-wide grep) -- there is no live wire-signal-driven phase
 * switch on a placed facade in the original source this ports from, only a client-side time-based cycle
 * ({@link #getCurrentStateForStack()}) used for the item's own tooltip/tab preview. This port preserves that
 * same (in)completeness rather than inventing a real switching mechanism: a {@link FacadeType#PHASED} facade's
 * data model round-trips correctly, but a placed one always renders/behaves as its zeroth state.
 */
public class FacadeInstance implements IFacade {
    public final FacadePhasedState[] phasedStates;
    public final FacadeType type;
    public final boolean isHollow;

    public FacadeInstance(FacadePhasedState[] phasedStates, boolean isHollow) {
        if (phasedStates == null) throw new NullPointerException("phasedStates");
        if (phasedStates.length == 0) throw new IllegalArgumentException("phasedStates.length was 0");
        // Maximum of 17 states - 16 for each colour, 1 for no colour
        if (phasedStates.length > 17) throw new IllegalArgumentException("phasedStates.length was > 17");
        this.phasedStates = phasedStates;
        this.type = phasedStates.length == 1 ? FacadeType.BASIC : FacadeType.PHASED;
        this.isHollow = isHollow;
    }

    public static FacadeInstance createSingle(FacadeBlockStateInfo info, boolean isHollow) {
        return new FacadeInstance(new FacadePhasedState[] { new FacadePhasedState(info, null) }, isHollow);
    }

    public static FacadeInstance readFromNbt(CompoundTag nbt) {
        List<CompoundTag> stateTags = NBTUtilBC.readCompoundList(nbt.get("states")).collect(Collectors.toList());
        if (stateTags.isEmpty()) {
            return FacadeInstance.createSingle(FacadeStateManager.defaultState, false);
        }
        FacadePhasedState[] states = new FacadePhasedState[stateTags.size()];
        for (int i = 0; i < stateTags.size(); i++) {
            states[i] = FacadePhasedState.readFromNbt(stateTags.get(i));
        }
        boolean hollow = nbt.getBooleanOr("isHollow", false);
        return new FacadeInstance(states, hollow);
    }

    public CompoundTag writeToNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("states", NBTUtilBC.writeCompoundList(Arrays.stream(phasedStates).map(FacadePhasedState::writeToNbt)));
        nbt.putBoolean("isHollow", isHollow);
        return nbt;
    }

    public boolean canAddColour(@Nullable DyeColor colour) {
        for (FacadePhasedState state : phasedStates) {
            if (state.activeColour == colour) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public FacadeInstance withState(FacadePhasedState state) {
        if (canAddColour(state.activeColour)) {
            FacadePhasedState[] newStates = Arrays.copyOf(phasedStates, phasedStates.length + 1);
            newStates[newStates.length - 1] = state;
            return new FacadeInstance(newStates, isHollow);
        } else {
            return null;
        }
    }

    /** Time-based cycle for a held item's own preview -- see this class's own javadoc for why this is the only
     * "phase switching" that was ever real in 1.12.2's own source. */
    public FacadePhasedState getCurrentStateForStack() {
        int count = phasedStates.length;
        if (count == 1) {
            return phasedStates[0];
        } else {
            long now = System.currentTimeMillis() % 100_000;
            return phasedStates[(int) ((now / 500) % count)];
        }
    }

    public FacadeInstance withSwappedIsHollow() {
        return new FacadeInstance(phasedStates, !isHollow);
    }

    public boolean areAllStatesSolid(Direction side) {
        for (FacadePhasedState state : phasedStates) {
            if (!state.isSideSolid(side)) {
                return false;
            }
        }
        return true;
    }

    // IFacade

    @Override
    public FacadeType getType() {
        return type;
    }

    @Override
    public boolean isHollow() {
        return isHollow;
    }

    @Override
    public IFacadePhasedState[] getPhasedStates() {
        return phasedStates;
    }
}
