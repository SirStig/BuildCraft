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
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.ITrigger;
import buildcraft.api.statements.StatementManager;

import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.statement.StatementType;
import buildcraft.lib.statement.TriggerWrapper;
import buildcraft.lib.statement.TriggerWrapper.TriggerWrapperInternal;

/** Port of 1.12.2's {@code buildcraft.silicon.gate.TriggerType} -- see the 26.x copy of this class for the
 * cross-platform account. */
public class TriggerType extends StatementType<TriggerWrapper> {
    public static final TriggerType INSTANCE = new TriggerType();

    private TriggerType() {
        super(TriggerWrapper.class, null);
    }

    @Override
    public TriggerWrapper convertToType(Object value) {
        if (value instanceof buildcraft.api.statements.ITriggerInternal internal) {
            return new TriggerWrapperInternal(internal);
        }
        return null;
    }

    @Override
    public TriggerWrapper readFromNbt(CompoundTag nbt, HolderLookup.Provider registries) {
        String kind = nbt.getString("kind");
        if (kind.isEmpty()) {
            return null;
        }
        EnumPipePart side = EnumPipePart.fromIndex(nbt.getByte("side"));
        IStatement statement = StatementManager.statements.get(kind);
        if (statement instanceof ITrigger) {
            return TriggerWrapper.wrap(statement, side.face);
        }
        BCLog.logger.warn("[gate.trigger] Couldn't find a trigger called '{}'! (found {})", kind, statement);
        return null;
    }

    @Override
    public CompoundTag writeToNbt(TriggerWrapper slot, HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        if (slot == null) {
            return nbt;
        }
        nbt.putString("kind", slot.getUniqueTag());
        nbt.putByte("side", (byte) slot.sourcePart.getIndex());
        return nbt;
    }

    @Override
    public TriggerWrapper readFromBuffer(PacketBufferBC buffer, HolderLookup.Provider registries) throws IOException {
        if (!buffer.readBoolean()) {
            return null;
        }
        String name = buffer.readUtf();
        EnumPipePart part = EnumPipePart.fromIndex(buffer.readByte());
        IStatement statement = StatementManager.statements.get(name);
        if (statement instanceof ITrigger) {
            return TriggerWrapper.wrap(statement, part.face);
        }
        throw new InvalidInputDataException("Unknown trigger '" + name + "'");
    }

    @Override
    public void writeToBuffer(PacketBufferBC buffer, TriggerWrapper slot, HolderLookup.Provider registries) {
        if (slot == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            buffer.writeUtf(slot.getUniqueTag());
            buffer.writeByte(slot.sourcePart.getIndex());
        }
    }
}
