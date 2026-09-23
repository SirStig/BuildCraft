/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.statements;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.gates.IGate;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.ITriggerInternalSided;
import buildcraft.api.statements.ITriggerProvider;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeEventStatement;

/** Trimmed port of 1.12.2's {@code buildcraft.transport.statements.TriggerProviderPipes}: offers
 * {@link TriggerPipeSignal} for every colour the gate's own pipe actually carries a wire of. The
 * {@code PipeFlowPower}/{@code PipeFlowRedstoneFlux} "power requested" trigger branch is dropped -- out of this
 * batch's scope (no {@code TriggerPowerRequested} exists here). */
public enum TriggerProviderPipes implements ITriggerProvider {
    INSTANCE;

    @Override
    public void addInternalTriggers(Collection<ITriggerInternal> triggers, IStatementContainer container) {
        if (!(container instanceof IGate gate)) {
            return;
        }
        IPipeHolder holder = gate.getPipeHolder();
        holder.fireEvent(new PipeEventStatement.AddTriggerInternal(holder, triggers));
        for (DyeColor colour : DyeColor.values()) {
            if (TriggerPipeSignal.doesGateHaveColour(gate, colour)) {
                triggers.add(TriggerPipeSignal.get(true, colour));
                triggers.add(TriggerPipeSignal.get(false, colour));
            }
        }
    }

    @Override
    public void addInternalSidedTriggers(
        Collection<ITriggerInternalSided> triggers, IStatementContainer container, @NotNull Direction side
    ) {
        if (container instanceof IGate gate) {
            IPipeHolder holder = gate.getPipeHolder();
            holder.fireEvent(new PipeEventStatement.AddTriggerInternalSided(holder, triggers, side));
        }
    }

    @Override
    public void addExternalTriggers(Collection<ITriggerExternal> triggers, @NotNull Direction side, BlockEntity tile) {
    }
}
