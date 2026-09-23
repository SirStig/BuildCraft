/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.gate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.gates.IGate;
import buildcraft.api.statements.IActionExternal;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IActionInternalSided;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.ITriggerInternalSided;
import buildcraft.api.statements.StatementManager;
import buildcraft.api.statements.StatementSlot;
import buildcraft.api.statements.containers.IRedstoneStatementContainer;
import buildcraft.api.transport.IWireEmitter;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeEvent;
import buildcraft.api.transport.pipe.PipeEventActionActivate;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.ActionWrapper.ActionWrapperExternal;
import buildcraft.lib.statement.ActionWrapper.ActionWrapperInternal;
import buildcraft.lib.statement.ActionWrapper.ActionWrapperInternalSided;
import buildcraft.lib.statement.FullStatement;
import buildcraft.lib.statement.TriggerWrapper;
import buildcraft.lib.statement.TriggerWrapper.TriggerWrapperExternal;
import buildcraft.lib.statement.TriggerWrapper.TriggerWrapperInternal;
import buildcraft.lib.statement.TriggerWrapper.TriggerWrapperInternalSided;

import buildcraft.transport.plug.PluggableGate;

/** Port of 1.12.2's {@code buildcraft.silicon.gate.GateLogic} -- see the 26.x copy of this class for the full
 * account, including why the whole bespoke network layer is dropped. The only real divergence here is
 * {@link CompoundTag}'s classic implicit-default API ({@code getCompound}/{@code getShort} rather than
 * {@code getCompoundOrEmpty}/{@code getShortOr}). */
public class GateLogic implements IGate, IWireEmitter, IRedstoneStatementContainer {

    public final PluggableGate pluggable;
    public final GateVariant variant;
    public final StatementPair[] statements;

    public final List<StatementSlot> activeActions = new ArrayList<>();

    /** Used to determine if gate logic should go across several trigger/action pairs. */
    public final boolean[] connections;

    /** Used by the GUI to display if an action is activated, or a trigger is currently triggering. */
    public final boolean[] triggerOn, actionOn;

    private final EnumSet<DyeColor> wireBroadcasts = EnumSet.noneOf(DyeColor.class);

    /** Used by the GUI/renderer to determine if this gate should glow or not. */
    public boolean isOn;

    public GateLogic(PluggableGate pluggable, GateVariant variant) {
        this.pluggable = pluggable;
        this.variant = variant;
        statements = new StatementPair[variant.numSlots];
        for (int s = 0; s < variant.numSlots; s++) {
            statements[s] = new StatementPair();
        }

        connections = new boolean[Math.max(0, variant.numSlots - 1)];
        triggerOn = new boolean[variant.numSlots];
        actionOn = new boolean[variant.numSlots];
    }

    // Saving + Loading

    public GateLogic(PluggableGate pluggable, CompoundTag nbt, HolderLookup.Provider registries) {
        this(pluggable, new GateVariant(nbt.getCompound("variant")));
        readConfigData(nbt, registries);
        wireBroadcasts.addAll(NBTUtilBC.readEnumSet(nbt.get("wireBroadcasts"), DyeColor.class));
    }

    public void readConfigData(CompoundTag nbt, HolderLookup.Provider registries) {
        short c = nbt.getShort("connections");
        for (int i = 0; i < connections.length; i++) {
            connections[i] = ((c >>> i) & 1) == 1;
        }
        for (int i = 0; i < statements.length; i++) {
            statements[i].trigger.readFromNbt(nbt.getCompound("trigger[" + i + "]"), registries);
            statements[i].action.readFromNbt(nbt.getCompound("action[" + i + "]"), registries);
        }
    }

    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("variant", variant.writeToNBT());

        short c = 0;
        for (int i = 0; i < connections.length; i++) {
            if (connections[i]) {
                c |= 1 << i;
            }
        }
        nbt.putShort("connections", c);

        for (int s = 0; s < statements.length; s++) {
            if (statements[s].trigger.get() != null) {
                nbt.put("trigger[" + s + "]", statements[s].trigger.writeToNbt(registries));
            }
            if (statements[s].action.get() != null) {
                nbt.put("action[" + s + "]", statements[s].action.writeToNbt(registries));
            }
        }
        nbt.put("wireBroadcasts", NBTUtilBC.writeEnumSet(wireBroadcasts, DyeColor.class));
        return nbt;
    }

    // IGate

    @Override
    public Direction getSide() {
        return pluggable.side;
    }

    @Override
    public BlockEntity getTile() {
        return getPipeHolder().getPipeTile();
    }

    @Override
    public BlockEntity getNeighbourTile(Direction side) {
        return getPipeHolder().getNeighbourTile(side);
    }

    @Override
    public IPipeHolder getPipeHolder() {
        return pluggable.holder;
    }

    @Override
    public List<IStatement> getTriggers() {
        List<IStatement> list = new ArrayList<>(statements.length);
        for (StatementPair pair : statements) {
            TriggerWrapper e = pair.trigger.get();
            list.add(e == null ? null : e.delegate);
        }
        return list;
    }

    @Override
    public List<IStatement> getActions() {
        List<IStatement> list = new ArrayList<>(statements.length);
        for (StatementPair pair : statements) {
            ActionWrapper e = pair.action.get();
            list.add(e == null ? null : e.delegate);
        }
        return list;
    }

    @Override
    public List<StatementSlot> getActiveActions() {
        return activeActions;
    }

    @Override
    public List<IStatementParameter> getTriggerParameters(int slot) {
        return Arrays.asList(statements[slot].trigger.getParameters());
    }

    @Override
    public List<IStatementParameter> getActionParameters(int slot) {
        return Arrays.asList(statements[slot].action.getParameters());
    }

    @Override
    public int getRedstoneInput(Direction side) {
        return getPipeHolder().getRedstoneInput(side);
    }

    @Override
    public boolean setRedstoneOutput(Direction side, int value) {
        return getPipeHolder().setRedstoneOutput(side, value);
    }

    // Wire related

    @Override
    public boolean isEmitting(DyeColor colour) {
        return wireBroadcasts.contains(colour);
    }

    @Override
    public void emitWire(DyeColor colour) {
        wireBroadcasts.add(colour);
    }

    // Internal logic

    public boolean isSplitInTwo() {
        return variant.numSlots > 4;
    }

    /** The whole trigger/action resolution loop -- see the 26.x copy of this method for the full account. */
    public void resolveActions() {
        int groupCount = 0;
        int groupActive = 0;

        isOn = false;
        Arrays.fill(triggerOn, false);
        Arrays.fill(actionOn, false);
        activeActions.clear();
        wireBroadcasts.clear();

        for (int triggerIndex = 0; triggerIndex < statements.length; triggerIndex++) {
            StatementPair pair = statements[triggerIndex];
            TriggerWrapper trigger = pair.trigger.get();
            groupCount++;
            if (trigger != null) {
                IStatementParameter[] params = new IStatementParameter[pair.trigger.getParamCount()];
                for (int p = 0; p < pair.trigger.getParamCount(); p++) {
                    params[p] = pair.trigger.getParamRef(p).get();
                }
                if (trigger.isTriggerActive(this, params)) {
                    groupActive++;
                    triggerOn[triggerIndex] = true;
                }
            }
            if (connections.length == triggerIndex || !connections[triggerIndex]) {
                boolean allActionsActive =
                    variant.logic == EnumGateLogic.AND ? groupActive == groupCount : groupActive > 0;
                for (int i = groupCount - 1; i >= 0; i--) {
                    int actionIndex = triggerIndex - i;
                    StatementPair fullAction = statements[actionIndex];
                    ActionWrapper action = fullAction.action.get();
                    actionOn[actionIndex] = allActionsActive;
                    if (action != null && allActionsActive) {
                        isOn = true;
                        StatementSlot slot = new StatementSlot();
                        slot.statement = action.delegate;
                        slot.parameters = fullAction.action.getParameters().clone();
                        slot.part = action.sourcePart;
                        activeActions.add(slot);
                        action.actionActivate(this, slot.parameters);
                        PipeEvent evt = new PipeEventActionActivate(
                            getPipeHolder(), action.getDelegate(), slot.parameters, action.sourcePart
                        );
                        getPipeHolder().fireEvent(evt);
                    } else if (action != null) {
                        action.actionDeactivated(this, fullAction.action.getParameters());
                    }
                }
                groupActive = 0;
                groupCount = 0;
            }
        }
    }

    public void onTick() {
        if (getPipeHolder().getPipeLevel().isClientSide()) {
            return;
        }
        resolveActions();
    }

    public SortedSet<TriggerWrapper> getAllValidTriggers() {
        SortedSet<TriggerWrapper> set = new TreeSet<>();
        for (ITriggerInternal trigger : StatementManager.getInternalTriggers(this)) {
            if (isValidTrigger(trigger)) {
                set.add(new TriggerWrapperInternal(trigger));
            }
        }
        for (Direction face : Direction.values()) {
            for (ITriggerInternalSided trigger : StatementManager.getInternalSidedTriggers(this, face)) {
                if (isValidTrigger(trigger)) {
                    set.add(new TriggerWrapperInternalSided(trigger, face));
                }
            }
            BlockEntity neighbour = getNeighbourTile(face);
            if (neighbour != null) {
                for (ITriggerExternal trigger : StatementManager.getExternalTriggers(face, neighbour)) {
                    if (isValidTrigger(trigger)) {
                        set.add(new TriggerWrapperExternal(trigger, face));
                    }
                }
            }
        }
        return set;
    }

    public SortedSet<ActionWrapper> getAllValidActions() {
        SortedSet<ActionWrapper> set = new TreeSet<>();
        for (IActionInternal action : StatementManager.getInternalActions(this)) {
            if (isValidAction(action)) {
                set.add(new ActionWrapperInternal(action));
            }
        }
        for (Direction face : Direction.values()) {
            for (IActionInternalSided action : StatementManager.getInternalSidedActions(this, face)) {
                if (isValidAction(action)) {
                    set.add(new ActionWrapperInternalSided(action, face));
                }
            }
            BlockEntity neighbour = getNeighbourTile(face);
            if (neighbour != null) {
                for (IActionExternal action : StatementManager.getExternalActions(face, neighbour)) {
                    if (isValidAction(action)) {
                        set.add(new ActionWrapperExternal(action, face));
                    }
                }
            }
        }
        return set;
    }

    public boolean isValidTrigger(IStatement statement) {
        return statement != null && statement.minParameters() <= variant.numTriggerArgs;
    }

    public boolean isValidAction(IStatement statement) {
        return statement != null && statement.minParameters() <= variant.numActionArgs;
    }

    public class StatementPair {
        public final FullStatement<TriggerWrapper> trigger;
        public final FullStatement<ActionWrapper> action;

        public StatementPair() {
            trigger = new FullStatement<>(TriggerType.INSTANCE, variant.numTriggerArgs, (s, i) -> {});
            action = new FullStatement<>(ActionType.INSTANCE, variant.numActionArgs, (s, i) -> {});
        }
    }
}
