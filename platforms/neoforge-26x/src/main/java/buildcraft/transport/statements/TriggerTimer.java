/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.statements;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import buildcraft.api.core.render.ISprite;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerInternal;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.statement.TriggerTimer}: fires once every {@link Duration#ticks}
 * ticks, read straight off {@link buildcraft.lib.tile.TileBC}'s own world clock -- same self-contained
 * three-instance-array shape as {@link TriggerPipeSignal}, since {@code BCSiliconStatements} (the original's
 * holder for these) is not ported.
 */
public final class TriggerTimer implements ITriggerInternal {

    public enum Duration {
        SHORT(5),
        MEDIUM(10),
        LONG(15);

        public final int seconds;
        public final int ticks;

        Duration(int seconds) {
            this.seconds = seconds;
            this.ticks = seconds * 20;
        }
    }

    public static final TriggerTimer SHORT = new TriggerTimer(Duration.SHORT);
    public static final TriggerTimer MEDIUM = new TriggerTimer(Duration.MEDIUM);
    public static final TriggerTimer LONG = new TriggerTimer(Duration.LONG);

    private static final TriggerTimer[] ALL = { SHORT, MEDIUM, LONG };

    public final Duration duration;

    private TriggerTimer(Duration duration) {
        this.duration = duration;
    }

    public static TriggerTimer[] all() {
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
        return "buildcraft:timer." + duration.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Component getDescription() {
        return Component.translatable("buildcraft.gate.trigger.timer", duration.seconds);
    }

    @Override
    @Nullable
    public ISprite getSprite() {
        return null;
    }

    @Override
    public boolean isTriggerActive(IStatementContainer container, IStatementParameter[] parameters) {
        return container.getTile().getLevel() != null
            && container.getTile().getLevel().getGameTime() % duration.ticks == 0;
    }
}
