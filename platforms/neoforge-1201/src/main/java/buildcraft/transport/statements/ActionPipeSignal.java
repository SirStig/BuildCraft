/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.statements;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

import buildcraft.api.core.render.ISprite;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.transport.IWireEmitter;

/**
 * Trimmed port of 1.12.2's {@code buildcraft.transport.statements.ActionPipeSignal} -- one instance per dye
 * colour, matching {@link TriggerPipeSignal}'s own self-contained-array shape. The other half of this batch's
 * one real end-to-end trigger/action pair: firing this action calls {@link IWireEmitter#emitWire}, which
 * {@link buildcraft.transport.gate.GateLogic} (the only {@link IWireEmitter} in this batch) turns into a real,
 * BFS-reachable powered state for {@link TriggerPipeSignal} to read back on the far end of a wire network.
 *
 * <p><b>Scope cut:</b> {@code ActionParameterSignal} (additional colours via parameter slots) is not ported --
 * see {@link TriggerPipeSignal}'s own javadoc for the identical reasoning.
 */
public final class ActionPipeSignal implements IActionInternal {

    private static final ActionPipeSignal[] ALL = new ActionPipeSignal[DyeColor.values().length];

    static {
        for (DyeColor colour : DyeColor.values()) {
            ALL[colour.getId()] = new ActionPipeSignal(colour);
        }
    }

    public final DyeColor colour;

    private ActionPipeSignal(DyeColor colour) {
        this.colour = colour;
    }

    public static ActionPipeSignal get(DyeColor colour) {
        return ALL[colour.getId()];
    }

    public static ActionPipeSignal[] all() {
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
    public ActionPipeSignal[] getPossible() {
        return ALL;
    }

    @Override
    public String getUniqueTag() {
        return "buildcraft:pipe.wire.output." + colour.getName();
    }

    @Override
    public Component getDescription() {
        return Component.translatable("buildcraft.gate.action.pipe.wire", colour.getName());
    }

    @Override
    @Nullable
    public ISprite getSprite() {
        return null;
    }

    @Override
    public void actionActivate(IStatementContainer container, IStatementParameter[] parameters) {
        if (container instanceof IWireEmitter emitter) {
            emitter.emitWire(colour);
        }
    }
}
