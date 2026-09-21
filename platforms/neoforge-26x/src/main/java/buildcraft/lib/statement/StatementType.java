/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.statement;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.statements.IGuiSlot;

import buildcraft.lib.net.PacketBufferBC;

/** Both serialisation directions gained a {@link HolderLookup.Provider}, following
 * {@code buildcraft.api.statements.IStatementParameter}'s own port -- reading/writing a parameter can mean
 * reading/writing an {@link net.minecraft.world.item.ItemStack}, whose data components need registry access on
 * this target. The 1.20.1 copy of this class takes the same parameter, ignoring it, so both platforms keep one
 * signature. */
public abstract class StatementType<S extends IGuiSlot> {

    public final Class<S> clazz;
    public final S defaultStatement;

    public StatementType(Class<S> clazz, S defaultStatement) {
        this.clazz = clazz;
        this.defaultStatement = defaultStatement;
    }

    /** Reads a {@link StatementWrapper} from the given {@link CompoundTag}. The tag compound will be equal to the
     * one returned by {@link #writeToNbt(IGuiSlot, HolderLookup.Provider)} */
    public abstract S readFromNbt(CompoundTag nbt, HolderLookup.Provider registries);

    public abstract CompoundTag writeToNbt(S slot, HolderLookup.Provider registries);

    /** Reads a {@link StatementWrapper} from the given {@link PacketBufferBC}. The buffer will return the data written
     * to a different buffer by {@link #writeToBuffer(PacketBufferBC, IGuiSlot, HolderLookup.Provider)}. */
    public abstract S readFromBuffer(PacketBufferBC buffer, HolderLookup.Provider registries) throws IOException;

    public abstract void writeToBuffer(PacketBufferBC buffer, S slot, HolderLookup.Provider registries);

    @Nullable
    public abstract S convertToType(Object value);
}
