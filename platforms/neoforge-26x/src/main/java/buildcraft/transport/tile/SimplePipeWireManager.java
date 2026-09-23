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

import buildcraft.transport.wire.WireNetwork;

/**
 * A genuine {@link IWireManager}. Per-pipe state (which {@link EnumWirePart}s are present and what colour each
 * is) plus NBT persistence was already real; {@link #isPowered}/{@link #isAnyPowered} now are too, delegating to
 * {@link WireNetwork}'s on-demand breadth-first walk -- a port of 1.12.2's own
 * {@code buildcraft.transport.wire.WireManager}/{@code WireSystem} onto this foundation, deliberately without
 * the persisted, incrementally-maintained cross-pipe graph 1.12.2 kept in {@code WorldSavedDataWireSystems} --
 * see {@link WireNetwork}'s own javadoc for why that particular piece was not ported, and what replaces it.
 *
 * <p>{@link #updateBetweens} stays a no-op: it is a purely cosmetic "which wire-to-wire segments render between
 * two octants" concern (1.12.2's {@code EnumWireBetween}), orthogonal to the real connectivity/signal logic
 * {@link WireNetwork} now provides, and out of this batch's scope (no pipe wire renderer exists yet).
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
        if (!parts.containsKey(part)) {
            return false;
        }
        return WireNetwork.isPowered(holder, part);
    }

    @Override
    public boolean isAnyPowered(DyeColor color) {
        for (Map.Entry<EnumWirePart, DyeColor> entry : parts.entrySet()) {
            if (entry.getValue() == color && WireNetwork.isPowered(holder, entry.getKey())) {
                return true;
            }
        }
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
