/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.net;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * Special {@link FriendlyByteBuf} subclass providing "offset" reading and writing -- writing a single bit to the
 * stream and auto-compacting it with similar bits into a single byte.
 *
 * <p>Two renames beyond the type: {@code PacketBuffer} is {@link FriendlyByteBuf}, and
 * {@code MathHelper.log2DeBruijn} -- the floor-log-base-2 used to size a compact enum encoding -- is
 * {@link Mth#ceillog2}. (There is no {@code RegistryFriendlyByteBuf} split on this target -- that only exists
 * on 26.x, where this class extends plain {@code FriendlyByteBuf} for the same reason it does here: the
 * bit-packing this class adds needs no registry access.)
 *
 * <p>This class only provides the buffer itself; the message dispatch it served in 1.12.2
 * ({@code IPayloadReceiver}, {@code MessageManager}) is not ported here. NeoForge's networking layer differs
 * materially from 1.12's single dynamically-dispatched channel even on this target, so design for that follows
 * once there is a concrete message to register rather than being speculated on in the abstract.
 */
public class PacketBufferBC extends FriendlyByteBuf {

    // Byte-based flag access
    private int readPartialOffset = 8; // so it resets down to 0 and reads a byte on read
    private int readPartialCache;

    /** The byte position currently being written to. -1 means no bytes have been written yet. */
    private int writePartialIndex = -1;
    /** The current bit offset, used to add successive flags into {@link #writePartialCache}. */
    private int writePartialOffset;
    /** Holds the current set of flags that will be written out. This only saves having to read one back. */
    private int writePartialCache;

    public PacketBufferBC(ByteBuf wrapped) {
        super(wrapped);
    }

    /**
     * Returns the given {@link ByteBuf} as a {@link PacketBufferBC}. If the given instance already is one, it
     * is returned as-is -- note this may have unexpected consequences if multiple partial-bit read/write calls
     * were already made on the given buffer before this call.
     */
    public static PacketBufferBC asPacketBufferBc(ByteBuf buf) {
        if (buf instanceof PacketBufferBC packetBufferBC) {
            return packetBufferBC;
        }
        return new PacketBufferBC(buf);
    }

    public static PacketBufferBC write(IPayloadWriter writer) {
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        writer.write(buffer);
        return buffer;
    }

    @Override
    public PacketBufferBC clear() {
        super.clear();
        readPartialOffset = 8;
        readPartialCache = 0;
        writePartialIndex = -1;
        writePartialOffset = 0;
        writePartialCache = 0;
        return this;
    }

    void writePartialBitsBegin() {
        if (writePartialIndex == -1 || writePartialOffset == 8) {
            writePartialIndex = writerIndex();
            writePartialOffset = 0;
            writePartialCache = 0;
            writeByte(0);
        }
    }

    void readPartialBitsBegin() {
        if (readPartialOffset == 8) {
            readPartialOffset = 0;
            readPartialCache = readUnsignedByte();
        }
    }

    /**
     * Writes a single boolean out to some position in this buffer. The flag might be written to a new byte,
     * increasing the writer index, or it might be added to an existing byte written by a previous call to this
     * method.
     */
    @Override
    public FriendlyByteBuf writeBoolean(boolean flag) {
        writePartialBitsBegin();
        int toWrite = (flag ? 1 : 0) << writePartialOffset;
        writePartialCache |= toWrite;
        writePartialOffset++;
        setByte(writePartialIndex, writePartialCache);
        return this;
    }

    /**
     * Reads a single boolean from some position in this buffer. The flag might be read from a new byte,
     * increasing the reader index, or it might be read from a byte read by a previous call to this method.
     */
    @Override
    public boolean readBoolean() {
        readPartialBitsBegin();
        int offset = 1 << readPartialOffset++;
        return (readPartialCache & offset) == offset;
    }

    /**
     * Writes a fixed number of bits out to the stream.
     *
     * @param value The value to write out.
     * @param length The number of bits to write.
     * @throws IllegalArgumentException if length was less than 1 or greater than 32.
     */
    public PacketBufferBC writeFixedBits(int value, int length) throws IllegalArgumentException {
        if (length <= 0) {
            throw new IllegalArgumentException("Tried to write too few bits! (" + length + ")");
        }
        if (length > 32) {
            throw new IllegalArgumentException("Tried to write more bits than are in an integer! (" + length + ")");
        }

        writePartialBitsBegin();

        if (writePartialOffset > 0) {
            int availableBits = 8 - writePartialOffset;

            if (availableBits >= length) {
                int mask = (1 << length) - 1;
                int bitsToWrite = value & mask;

                writePartialCache |= bitsToWrite << writePartialOffset;
                setByte(writePartialIndex, writePartialCache);
                writePartialOffset += length;
                return this;
            } else {
                int mask = (1 << availableBits) - 1;
                int shift = length - availableBits;
                int bitsToWrite = (value >>> shift) & mask;

                writePartialCache |= bitsToWrite << writePartialOffset;
                setByte(writePartialIndex, writePartialCache);

                writePartialCache = 0;
                writePartialOffset = 8;

                length -= availableBits;
            }
        }

        while (length >= 8) {
            writePartialBitsBegin();

            int byteToWrite = (value >>> (length - 8)) & 0xFF;
            setByte(writePartialIndex, byteToWrite);

            writePartialCache = 0;
            writePartialOffset = 8;

            length -= 8;
        }

        if (length > 0) {
            writePartialBitsBegin();

            int mask = (1 << length) - 1;
            writePartialCache = value & mask;
            setByte(writePartialIndex, writePartialCache);
            writePartialOffset = length;
        }

        return this;
    }

    /**
     * @return The read bits, compacted into an int.
     * @throws IllegalArgumentException if length was less than 1 or greater than 32.
     */
    public int readFixedBits(int length) throws IllegalArgumentException {
        if (length <= 0) {
            throw new IllegalArgumentException("Tried to read too few bits! (" + length + ")");
        }
        if (length > 32) {
            throw new IllegalArgumentException("Tried to read more bits than are in an integer! (" + length + ")");
        }
        readPartialBitsBegin();

        int value = 0;

        if (readPartialOffset > 0) {
            int availableBits = 8 - readPartialOffset;
            if (availableBits >= length) {
                int mask = (1 << length) - 1;
                value = (readPartialCache >>> readPartialOffset) & mask;
                readPartialOffset += length;
                return value;
            } else {
                int bitsRead = readPartialCache >>> readPartialOffset;
                value = bitsRead;

                readPartialCache = 0;
                readPartialOffset = 8;

                length -= availableBits;
            }
        }

        while (length >= 8) {
            readPartialBitsBegin();
            length -= 8;
            value <<= 8;
            value |= readPartialCache;
            readPartialOffset = 8;
        }

        if (length > 0) {
            readPartialBitsBegin();

            int mask = (1 << length) - 1;

            value <<= length;
            value |= readPartialCache & mask;
            readPartialOffset = length;
        }

        return value;
    }

    /** Writes an enum value compacted to the minimum number of bits needed for its constant count, rather than
     * a full var-int. */
    public PacketBufferBC writeCompactEnum(Enum<?> value) {
        Enum<?>[] possible = value.getDeclaringClass().getEnumConstants();
        if (possible == null) throw new IllegalArgumentException("Not an enum " + value.getClass());
        if (possible.length == 0) {
            throw new IllegalArgumentException("Tried to write an enum value without any values! How did you do this?");
        }
        if (possible.length == 1) return this;
        writeFixedBits(value.ordinal(), Mth.ceillog2(possible.length));
        return this;
    }

    /** Reads an enum value written by {@link #writeCompactEnum(Enum)}. */
    public <E extends Enum<E>> E readCompactEnum(Class<E> enumClass) {
        E[] enums = enumClass.getEnumConstants();
        if (enums == null) throw new IllegalArgumentException("Not an enum " + enumClass);
        if (enums.length == 0) {
            throw new IllegalArgumentException("Tried to read an enum value without any values! How did you do this?");
        }
        if (enums.length == 1) return enums[0];
        int length = Mth.ceillog2(enums.length);
        int index = readFixedBits(length);
        return enums[index];
    }

    /** Reads a string of any possible length. */
    public String readStringOfAnyLength() {
        int length = readVarInt();
        byte[] array = new byte[length];
        for (int i = 0; i < length; i++) {
            array[i] = readByte();
        }
        return new String(array, StandardCharsets.UTF_8);
    }
}
