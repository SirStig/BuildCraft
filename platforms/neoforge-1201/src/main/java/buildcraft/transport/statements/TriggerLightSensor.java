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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.core.render.ISprite;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerInternalSided;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.statement.TriggerLightSensor}: reads the light level of the block
 * space just beyond {@code side}, via {@code LevelReader#getMaxLocalRawBrightness} -- the modern replacement for
 * 1.12.2's {@code World#getLightFromNeighbors} (same "combined block+sky brightness, adjusted for time of day"
 * meaning; both this port's platforms expose it identically). Same self-contained two-instance-array shape as
 * {@link TriggerPipeSignal}.
 */
public final class TriggerLightSensor implements ITriggerInternalSided {

    public static final TriggerLightSensor LOW = new TriggerLightSensor(false);
    public static final TriggerLightSensor HIGH = new TriggerLightSensor(true);

    private static final TriggerLightSensor[] ALL = { LOW, HIGH };

    /** {@code true} for the "triggers on bright light" instance ({@link #HIGH}), {@code false} for "triggers on
     * darkness" ({@link #LOW}) -- matches the original field name and XOR trick in {@link #isTriggerActive}. */
    private final boolean bright;

    private TriggerLightSensor(boolean bright) {
        this.bright = bright;
    }

    public static TriggerLightSensor[] all() {
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
        return "buildcraft:light." + (bright ? "high" : "low");
    }

    @Override
    public Component getDescription() {
        return Component.translatable(bright ? "buildcraft.gate.trigger.light.high" : "buildcraft.gate.trigger.light.low");
    }

    @Override
    @Nullable
    public ISprite getSprite() {
        return null;
    }

    @Override
    public boolean isTriggerActive(@NotNull Direction side, IStatementContainer source, IStatementParameter[] parameters) {
        BlockEntity tile = source.getTile();
        if (tile.getLevel() == null) {
            return false;
        }
        BlockPos pos = tile.getBlockPos().relative(side);
        int light = tile.getLevel().getMaxLocalRawBrightness(pos);
        return (light < 8) ^ bright;
    }
}
