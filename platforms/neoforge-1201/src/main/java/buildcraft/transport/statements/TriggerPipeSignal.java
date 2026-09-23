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
import buildcraft.api.gates.IGate;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.transport.IWireManager;

/**
 * Trimmed port of 1.12.2's {@code buildcraft.transport.statements.TriggerPipeSignal} -- one instance per
 * (active/inactive) x (dye colour) pair, 32 total, matching 1.12.2's own {@code BCTransportStatements
 * .TRIGGER_PIPE_SIGNAL} array shape but self-contained (that array-holding class is not ported; see this
 * batch's own scope notes on the dropped sprite/statement-registry classes).
 *
 * <p><b>Scope cut:</b> the {@code TriggerParameterSignal} parameter slots (letting one trigger additionally
 * require up to three other colours) are not ported -- {@link #maxParameters()} is honestly {@code 0}. This is
 * this batch's one real end-to-end trigger: {@link #isTriggerActive} reads {@link IWireManager#isAnyPowered},
 * which is now backed by {@link buildcraft.transport.wire.WireNetwork}'s real cross-pipe BFS, not a stub.
 */
public final class TriggerPipeSignal implements ITriggerInternal {

    private static final TriggerPipeSignal[] ALL = new TriggerPipeSignal[DyeColor.values().length * 2];

    static {
        for (DyeColor colour : DyeColor.values()) {
            ALL[colour.getId() * 2] = new TriggerPipeSignal(true, colour);
            ALL[colour.getId() * 2 + 1] = new TriggerPipeSignal(false, colour);
        }
    }

    public final boolean active;
    public final DyeColor colour;

    private TriggerPipeSignal(boolean active, DyeColor colour) {
        this.active = active;
        this.colour = colour;
    }

    public static TriggerPipeSignal get(boolean active, DyeColor colour) {
        return ALL[colour.getId() * 2 + (active ? 0 : 1)];
    }

    public static TriggerPipeSignal[] all() {
        return ALL;
    }

    /** Whether {@code gate}'s own pipe carries a wire of {@code c} at all -- used by
     * {@link TriggerProviderPipes}/{@link ActionProviderPipes} to only ever offer a colour a gate could
     * plausibly read or emit. */
    public static boolean doesGateHaveColour(IGate gate, DyeColor c) {
        return gate.getPipeHolder().getWireManager().hasPartOfColor(c);
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
        return "buildcraft:pipe.wire.input." + colour.getName() + (active ? ".active" : ".inactive");
    }

    @Override
    public Component getDescription() {
        return Component.translatable(
            "buildcraft.gate.trigger.pipe.wire." + (active ? "active" : "inactive"), colour.getName()
        );
    }

    @Override
    @Nullable
    public ISprite getSprite() {
        return null;
    }

    @Override
    public boolean isTriggerActive(IStatementContainer container, IStatementParameter[] parameters) {
        if (!(container instanceof IGate gate)) {
            return false;
        }
        IWireManager wires = gate.getPipeHolder().getWireManager();
        return active == wires.isAnyPowered(colour);
    }
}
