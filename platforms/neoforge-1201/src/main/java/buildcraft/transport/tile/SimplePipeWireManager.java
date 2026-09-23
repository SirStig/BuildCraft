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
 * A genuine {@link IWireManager} -- see the 26.x copy of this class for the full account of
 * {@link #isPowered}/{@link #isAnyPowered} now delegating to {@link WireNetwork}'s on-demand BFS rather than
 * being honest no-ops, and of why {@link #updateBetweens} stays a no-op (purely cosmetic, out of scope).
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
            if (nbt.contains(part.name())) {
                parts.put(part, DyeColor.byId(nbt.getInt(part.name())));
            }
        }
    }
}
