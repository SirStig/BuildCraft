/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventStatement;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.lib.misc.MathUtil;
import buildcraft.lib.misc.SoundUtil;

import buildcraft.BCTransportRegistries;
import buildcraft.transport.statements.ActionPowerPulsar;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.plug.PluggablePulsar}: a blocking plug that pushes one MJ pulse into
 * the underlying pipe behaviour's {@link IMjRedstoneReceiver} every {@link #PULSE_STAGE} ticks, whenever it is
 * "on" -- manually (right-click toggle, {@link #onPluggableActivate}), continuously (a gate's
 * {@code ACTION_PULSAR_CONSTANT}, {@link #enablePulsar}), or for a single upcoming pulse
 * (a gate's {@code ACTION_PULSAR_SINGLE}, {@link #addSinglePulse}) -- see {@link ActionPowerPulsar}, ported onto
 * this port's already-real {@code PipeEventStatement.AddActionInternalSided} pipeline the same way
 * {@code PluggableTimer}/{@code PluggableLightSensor} use the trigger side of it.
 *
 * <p><b>Scope cuts:</b> the whole {@code MODEL_FUNC_CTX}/{@code ModelVariableData}/{@code clientModelData}
 * client-model-variable block and {@code getModelRenderKey} are dropped -- rendering-rewrite territory, out of
 * scope everywhere in this batch (see {@link PluggableBlocker}'s own javadoc); the bespoke
 * {@code writeCreationPayload}/{@code writePayload}/{@code readPayload} network trio is dropped too, since this
 * port's whole-tile NBT sync already round-trips every field below on every change (see {@link PluggableGate}'s
 * own javadoc for the identical reasoning). The per-item-or-per-millibucket MJ cost of a single pulse
 * ({@code BCTransportConfig.mjPerItem}/{@code mjPerMillibucket}, gated on {@code BCModules.TRANSPORT.isLoaded()})
 * has no equivalent config system in this port at all (no {@code BCTransportConfig} exists here, and there is
 * only ever one flow kind, items, ported so far) -- a single pulse always costs a flat {@link MjAPI#MJ} instead,
 * same as a constant pulse.
 */
public class PluggablePulsar extends PipePluggable {

    private static final int PULSE_STAGE = 20;

    private static final AABB[] BOXES = new AABB[6];

    static {
        double ll = 2 / 16.0;
        double lu = 4 / 16.0;
        double ul = 12 / 16.0;
        double uu = 14 / 16.0;

        double min = 5 / 16.0;
        double max = 11 / 16.0;

        BOXES[Direction.DOWN.get3DDataValue()] = new AABB(min, ll, min, max, lu, max);
        BOXES[Direction.UP.get3DDataValue()] = new AABB(min, ul, min, max, uu, max);
        BOXES[Direction.NORTH.get3DDataValue()] = new AABB(min, min, ll, max, max, lu);
        BOXES[Direction.SOUTH.get3DDataValue()] = new AABB(min, min, ul, max, max, uu);
        BOXES[Direction.WEST.get3DDataValue()] = new AABB(ll, min, min, lu, max, max);
        BOXES[Direction.EAST.get3DDataValue()] = new AABB(ul, min, min, uu, max, max);
    }

    private boolean manuallyEnabled = false;
    /** Increments from 0 to {@link #PULSE_STAGE} to decide when it should pulse some power into the pipe
     * behaviour. */
    private int pulseStage = 0;
    private int gateEnabledTicks;
    private int gateSinglePulses;

    public PluggablePulsar(PluggableDefinition definition, IPipeHolder holder, Direction side) {
        super(definition, holder, side);
    }

    // Saving + Loading

    public PluggablePulsar(PluggableDefinition definition, IPipeHolder holder, Direction side, CompoundTag nbt) {
        super(definition, holder, side);
        this.manuallyEnabled = nbt.getBoolean("manuallyEnabled");
        gateEnabledTicks = nbt.getInt("gateEnabledTicks");
        gateSinglePulses = nbt.getInt("gateSinglePulses");
        pulseStage = MathUtil.clamp(nbt.getInt("pulseStage"), 0, PULSE_STAGE);
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.putBoolean("manuallyEnabled", manuallyEnabled);
        nbt.putInt("gateEnabledTicks", gateEnabledTicks);
        nbt.putInt("gateSinglePulses", gateSinglePulses);
        nbt.putInt("pulseStage", pulseStage);
        return nbt;
    }

    // PipePluggable

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.get3DDataValue()];
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        return new ItemStack(BCTransportRegistries.PLUG_PULSAR.get());
    }

    @Override
    public void onTick() {
        if (holder.getPipeLevel().isClientSide()) {
            return;
        }
        boolean isOn = isPulsing();

        if (isOn) {
            pulseStage++;
        } else {
            pulseStage = 0;
        }
        if (gateEnabledTicks > 0) {
            gateEnabledTicks--;
        }
        if (pulseStage == PULSE_STAGE) {
            pulseStage = 0;
            if (holder.getPipe() != null
                && holder.getPipe().getBehaviour() instanceof IMjRedstoneReceiver rsRec) {
                if (gateSinglePulses > 0) {
                    long excess = rsRec.receivePower(MjAPI.MJ, true);
                    if (excess == 0) {
                        rsRec.receivePower(MjAPI.MJ, false);
                    } else {
                        // Nothing was extracted, so lets extract in the future
                        gateSinglePulses++;
                    }
                } else {
                    rsRec.receivePower(MjAPI.MJ, false);
                }
                if (gateSinglePulses > 0) {
                    gateSinglePulses--;
                }
            }
        }
    }

    @PipeEventHandler
    public void onAddActions(PipeEventStatement.AddActionInternalSided event) {
        if (event.side == this.side) {
            event.actions.add(ActionPowerPulsar.CONSTANT);
            event.actions.add(ActionPowerPulsar.SINGLE);
        }
    }

    @Override
    public boolean onPluggableActivate(Player player, BlockHitResult trace) {
        if (!holder.getPipeLevel().isClientSide()) {
            manuallyEnabled = !manuallyEnabled;
            SoundUtil.playLeverSwitch(holder.getPipeLevel(), holder.getPipePos(), manuallyEnabled);
            scheduleNetworkUpdate();
        }
        return true;
    }

    public void enablePulsar() {
        gateEnabledTicks = 10;
    }

    public void addSinglePulse() {
        gateSinglePulses++;
    }

    private boolean isPulsing() {
        return manuallyEnabled || gateEnabledTicks > 0 || gateSinglePulses > 0;
    }
}
