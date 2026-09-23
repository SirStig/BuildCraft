/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;

import buildcraft.api.facades.FacadeType;
import buildcraft.api.facades.IFacade;
import buildcraft.api.facades.IFacadePhasedState;
import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.lib.misc.MathUtil;

import buildcraft.BCTransportRegistries;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.plug.PluggableFacade}: a pipe-face plug that disguises the pipe
 * segment behind it as some other block. See {@link FacadeInstance}'s own javadoc for the phased-facade scope
 * cut, and {@code buildcraft.transport.tile.RenderTilePipeHolder} for how the disguise is actually drawn --
 * {@code getModelRenderKey}/{@code getBlockColor} have no equivalent on {@link PipePluggable} any more (see its
 * own class javadoc), so that class special-cases {@link PluggableFacade} directly, the same way it already
 * special-cases {@code PipeFlowFluids}/{@code PipeFlowItems}.
 *
 * <p><b>Phased switching, wired up for the first time this batch.</b> 1.12.2's own {@code PluggableFacade} never
 * updated {@code activeState} at all outside of NBT load -- confirmed by re-reading the real 1.12.2 source, which
 * has no write site for the field anywhere except the NBT constructor -- so a placed phased facade always showed
 * whichever state happened to load from disk. {@link FacadePhasedState}'s own shape (one {@link DyeColor} or
 * {@code null} per state) makes the intent unambiguous even though 1.12.2 left it disconnected: each non-default
 * state is "the disguise to show while this colour's wire signal is live". {@link #onTick()} below is this port's
 * own best-faith completion of that intent, not a restoration of anything that ever ran: every server tick (see
 * {@code TilePipeHolder#serverTick}) it asks this facade's own {@link IWireManager} (via
 * {@link IPipeHolder#getWireManager()}) whether any wire of each candidate colour is currently powered
 * ({@link IWireManager#isAnyPowered}), in state order, and switches {@code activeState} to the first match --
 * falling back to the colourless (default) state if none is powered, or to index {@code 0} if there is no
 * colourless state at all. A change pushes a real resync ({@link #scheduleNetworkUpdate()}) so the client's
 * disguise render/collision box actually follows it, not just the saved NBT.
 */
public class PluggableFacade extends PipePluggable implements IFacade {

    private static final AABB[] BOXES = new AABB[6];

    static {
        double ll = 0 / 16.0;
        double lu = 2 / 16.0;
        double ul = 14 / 16.0;
        double uu = 16 / 16.0;

        double min = 0 / 16.0;
        double max = 16 / 16.0;

        BOXES[Direction.DOWN.get3DDataValue()] = new AABB(min, ll, min, max, lu, max);
        BOXES[Direction.UP.get3DDataValue()] = new AABB(min, ul, min, max, uu, max);
        BOXES[Direction.NORTH.get3DDataValue()] = new AABB(min, min, ll, max, max, lu);
        BOXES[Direction.SOUTH.get3DDataValue()] = new AABB(min, min, ul, max, max, uu);
        BOXES[Direction.WEST.get3DDataValue()] = new AABB(ll, min, min, lu, max, max);
        BOXES[Direction.EAST.get3DDataValue()] = new AABB(ul, min, min, uu, max, max);
    }

    public final FacadeInstance states;
    public final boolean isSideSolid;

    /** Which of {@link FacadeInstance#phasedStates} is currently shown/solid. Kept at {@code 0} for a
     * {@link FacadeType#BASIC} facade; for a {@link FacadeType#PHASED} one, driven live by {@link #onTick()} --
     * see this class's own javadoc for the switching rule. */
    public int activeState;

    public PluggableFacade(PluggableDefinition definition, IPipeHolder holder, Direction side, FacadeInstance states) {
        super(definition, holder, side);
        this.states = states;
        this.isSideSolid = states.areAllStatesSolid(side);
    }

    public PluggableFacade(
        PluggableDefinition def, IPipeHolder holder, Direction side, CompoundTag nbt, HolderLookup.Provider registries
    ) {
        super(def, holder, side);
        this.states = FacadeInstance.readFromNbt(nbt.getCompoundOrEmpty("facade"));
        this.activeState = MathUtil.clamp(nbt.getIntOr("activeState", 0), 0, states.phasedStates.length - 1);
        this.isSideSolid = states.areAllStatesSolid(side);
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.put("facade", states.writeToNbt());
        nbt.putInt("activeState", activeState);
        return nbt;
    }

    // PipePluggable

    @Override
    public void onTick() {
        if (states.type != FacadeType.PHASED) {
            return;
        }
        int newState = computeActiveState();
        if (newState != activeState) {
            activeState = newState;
            scheduleNetworkUpdate();
        }
    }

    /** See this class's own javadoc for the reasoning: the first phased state whose colour has a currently
     * powered wire wins, falling back to the colourless state (or index 0, if there isn't one). */
    private int computeActiveState() {
        IWireManager wireManager = holder.getWireManager();
        int fallback = -1;
        for (int i = 0; i < states.phasedStates.length; i++) {
            DyeColor colour = states.phasedStates[i].activeColour;
            if (colour == null) {
                if (fallback < 0) {
                    fallback = i;
                }
                continue;
            }
            if (wireManager.isAnyPowered(colour)) {
                return i;
            }
        }
        return fallback < 0 ? 0 : fallback;
    }

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.get3DDataValue()];
    }

    @Override
    public boolean isBlocking() {
        return !isHollow();
    }

    @Override
    public boolean isSideSolid() {
        return isSideSolid;
    }

    @Override
    public float getExplosionResistance(@Nullable Entity exploder, Explosion explosion) {
        return states.phasedStates[activeState].stateInfo.state.getBlock().getExplosionResistance();
    }

    @Override
    public ItemStack getPickStack() {
        return BCTransportRegistries.ITEM_PLUGGABLE_FACADE.get().createItemStack(states);
    }

    // IFacade

    @Override
    public FacadeType getType() {
        return states.getType();
    }

    @Override
    public boolean isHollow() {
        return states.isHollow();
    }

    @Override
    public IFacadePhasedState[] getPhasedStates() {
        return states.getPhasedStates();
    }
}
