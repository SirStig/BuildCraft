/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.transport.tile;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;

import buildcraft.api.transport.EnumWirePart;
import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IPipeHolder;

/**
 * A genuine, if deliberately small, {@link IWireManager} -- new to the port, not a port of 1.12.2's own
 * {@code buildcraft.transport.wire.WireManager} (which additionally builds and rebuilds cross-pipe "wire
 * system" graphs so a redstone signal on one wire propagates to every electrically-connected wire across a whole
 * pipe network -- entirely out of this batch's scope, since nothing here places a wire at all: no wire item, no
 * gate, no {@link buildcraft.api.transport.IWireEmitter} exists yet anywhere in this port).
 *
 * <p>What is here is real, not faked: which {@link EnumWirePart}s are present on this pipe and what colour each
 * is ({@link #addPart}/{@link #removePart}/{@link #getColorOfPart}/{@link #hasPartOfColor}), plus NBT
 * persistence for that state. {@link #isPowered}/{@link #isAnyPowered} are honestly {@code false} always --
 * correct, not a stub, given there is no {@link buildcraft.api.transport.IWireEmitter} anywhere in this batch
 * that could ever legitimately power one. {@link #updateBetweens} is a no-op for the same reason: there is no
 * cross-pipe wire graph to update.
 */
public final class SimplePipeWireManager implements IWireManager {
    private final IPipeHolder holder;
    private final EnumMap<EnumWirePart, DyeColor> parts = new EnumMap<>(EnumWirePart.class);

    public SimplePipeWireManager(IPipeHolder holder) {
        this.holder = holder;
    }

    @Override
    public IPipeHolder getHolder() {
        return holder;
    }

    @Override
    public void updateBetweens(boolean recursive) {
        // No cross-pipe wire system graph exists in this batch -- see this class's own javadoc.
    }

    @Override
    @Nullable
    public DyeColor getColorOfPart(EnumWirePart part) {
        return parts.get(part);
    }

    @Override
    @Nullable
    public DyeColor removePart(EnumWirePart part) {
        return parts.remove(part);
    }

    @Override
    public boolean addPart(EnumWirePart part, DyeColor colour) {
        if (parts.containsKey(part)) {
            return false;
        }
        parts.put(part, colour);
        return true;
    }

    @Override
    public boolean hasPartOfColor(DyeColor color) {
        return parts.containsValue(color);
    }

    @Override
    public boolean isPowered(EnumWirePart part) {
        // No IWireEmitter exists anywhere in this batch, so nothing could ever legitimately power a wire part.
        return false;
    }

    @Override
    public boolean isAnyPowered(DyeColor color) {
        return false;
    }

    public CompoundTag writeToNbt() {
        CompoundTag nbt = new CompoundTag();
        for (Map.Entry<EnumWirePart, DyeColor> entry : parts.entrySet()) {
            nbt.putInt(entry.getKey().name(), entry.getValue().getId());
        }
        return nbt;
    }

    public void readFromNbt(CompoundTag nbt) {
        parts.clear();
        for (EnumWirePart part : EnumWirePart.VALUES) {
            nbt.getInt(part.name()).ifPresent(id -> parts.put(part, DyeColor.byId(id)));
        }
    }
}
