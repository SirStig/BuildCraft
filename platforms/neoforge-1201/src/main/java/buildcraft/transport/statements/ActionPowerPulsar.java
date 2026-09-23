/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.statements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import buildcraft.api.core.render.ISprite;
import buildcraft.api.gates.IGate;
import buildcraft.api.statements.IActionInternalSided;
import buildcraft.api.statements.IActionSingle;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.transport.plug.PluggablePulsar;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.statement.ActionPowerPulsar}: the sided action a gate fires to
 * enable a {@link PluggablePulsar} on the same face, either continuously ({@link #CONSTANT}) or for one pulse
 * ({@link #SINGLE}, {@link IActionSingle} -- this port's {@code ActionWrapper} already fires
 * {@link IActionSingle#singleActionTick()}-marked actions only on the tick they become active, so no extra
 * wiring is needed for the "single" half of this to actually only fire once).
 */
public final class ActionPowerPulsar implements IActionInternalSided, IActionSingle {

    public static final ActionPowerPulsar CONSTANT = new ActionPowerPulsar(true);
    public static final ActionPowerPulsar SINGLE = new ActionPowerPulsar(false);

    private static final ActionPowerPulsar[] ALL = { CONSTANT, SINGLE };

    public final boolean constant;

    private ActionPowerPulsar(boolean constant) {
        this.constant = constant;
    }

    public static ActionPowerPulsar[] all() {
        return ALL;
    }

    @Override
    public int maxParameters() {
        return 0;
    }

    @Override
    public int minParameters() {
        return 0;
    }

    @Override
    @Nullable
    public IStatementParameter createParameter(int index) {
        return null;
    }

    @Override
    public IStatement rotateLeft() {
        return this;
    }

    @Override
    public IStatement[] getPossible() {
        return ALL;
    }

    @Override
    public String getUniqueTag() {
        return "buildcraft:pulsar." + (constant ? "constant" : "single");
    }

    @Override
    public Component getDescription() {
        return Component.translatable(
            constant ? "buildcraft.gate.action.pulsar.constant" : "buildcraft.gate.action.pulsar.single"
        );
    }

    @Override
    @Nullable
    public ISprite getSprite() {
        return null;
    }

    @Override
    public boolean singleActionTick() {
        return !constant;
    }

    @Override
    public void actionActivate(@NotNull Direction side, IStatementContainer source, IStatementParameter[] parameters) {
        if (source instanceof IGate gate) {
            IPipeHolder pipe = gate.getPipeHolder();
            PipePluggable plug = pipe.getPluggable(side);
            if (plug instanceof PluggablePulsar pulsar) {
                if (constant) {
                    pulsar.enablePulsar();
                } else {
                    pulsar.addSinglePulse();
                }
            }
        }
    }
}
