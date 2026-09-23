/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.gate;

import java.io.IOException;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.statements.IAction;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.StatementManager;

import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.ActionWrapper.ActionWrapperInternal;
import buildcraft.lib.statement.StatementType;

/** Port of 1.12.2's {@code buildcraft.silicon.gate.ActionType}, matching {@link TriggerType}'s own port. */
public class ActionType extends StatementType<ActionWrapper> {
    public static final ActionType INSTANCE = new ActionType();

    private ActionType() {
        super(ActionWrapper.class, null);
    }

    @Override
    public ActionWrapper convertToType(Object value) {
        if (value instanceof IActionInternal internal) {
            return new ActionWrapperInternal(internal);
        }
        // Sided actions cannot be converted -- they require a side, which this generic path does not have.
        return null;
    }

    @Override
    public ActionWrapper readFromNbt(CompoundTag nbt, HolderLookup.Provider registries) {
        String kind = nbt.getStringOr("kind", "");
        if (kind.isEmpty()) {
            return null;
        }
        EnumPipePart side = EnumPipePart.fromIndex(nbt.getByteOr("side", (byte) EnumPipePart.CENTER.getIndex()));
        IStatement statement = StatementManager.statements.get(kind);
        if (statement instanceof IAction) {
            return ActionWrapper.wrap(statement, side.face);
        }
        BCLog.logger.warn("[gate.trigger] Couldn't find an action called '{}'! (found {})", kind, statement);
        return null;
    }

    @Override
    public CompoundTag writeToNbt(ActionWrapper slot, HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        if (slot == null) {
            return nbt;
        }
        nbt.putString("kind", slot.getUniqueTag());
        nbt.putByte("side", (byte) slot.sourcePart.getIndex());
        return nbt;
    }

    @Override
    public ActionWrapper readFromBuffer(PacketBufferBC buffer, HolderLookup.Provider registries) throws IOException {
        if (!buffer.readBoolean()) {
            return null;
        }
        String name = buffer.readUtf();
        EnumPipePart part = EnumPipePart.fromIndex(buffer.readByte());
        IStatement statement = StatementManager.statements.get(name);
        if (statement instanceof IAction) {
            return ActionWrapper.wrap(statement, part.face);
        }
        throw new InvalidInputDataException("Unknown action '" + name + "'");
    }

    @Override
    public void writeToBuffer(PacketBufferBC buffer, ActionWrapper slot, HolderLookup.Provider registries) {
        if (slot == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            buffer.writeUtf(slot.getUniqueTag());
            buffer.writeByte(slot.sourcePart.getIndex());
        }
    }
}
