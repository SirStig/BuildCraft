/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.statement;

import java.io.IOException;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementManager;
import buildcraft.api.statements.StatementManager.IParamReaderBuf;
import buildcraft.api.statements.StatementManager.IParameterReader;

import buildcraft.lib.net.PacketBufferBC;

public class StatementTypeParam extends StatementType<IStatementParameter> {
    public static final StatementTypeParam INSTANCE = new StatementTypeParam();

    public StatementTypeParam() {
        super(IStatementParameter.class, null);
    }

    @Override
    public IStatementParameter convertToType(Object value) {
        return value instanceof IStatementParameter ? (IStatementParameter) value : null;
    }

    @Override
    public IStatementParameter readFromNbt(CompoundTag nbt, HolderLookup.Provider registries) {
        String kind = nbt.getString("kind").orElse("");
        IParameterReader reader = StatementManager.parameters.get(kind);
        if (reader == null) {
            return null;
        } else {
            return reader.readFromNbt(nbt, registries);
        }
    }

    @Override
    public CompoundTag writeToNbt(IStatementParameter slot, HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        if (slot != null) {
            slot.writeToNbt(nbt, registries);
            nbt.putString("kind", slot.getUniqueTag());
        }
        return nbt;
    }

    @Override
    public IStatementParameter readFromBuffer(PacketBufferBC buffer, HolderLookup.Provider registries) throws IOException {
        if (buffer.readBoolean()) {
            String tag = buffer.readUtf();
            IParamReaderBuf reader = StatementManager.paramsBuf.get(tag);
            if (reader == null) {
                throw new InvalidInputDataException("Unknown paramater type " + tag);
            }
            return reader.readFromBuf(buffer, registries);
        } else {
            return null;
        }
    }

    @Override
    public void writeToBuffer(PacketBufferBC buffer, IStatementParameter slot, HolderLookup.Provider registries) {
        if (slot == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            buffer.writeUtf(slot.getUniqueTag());
            slot.writeToBuf(buffer, registries);
        }
    }
}
