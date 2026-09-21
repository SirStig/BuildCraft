/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.DyeColor;

import buildcraft.api.transport.pipe.IPipeHolder;

/** The pipe wires attached to one pipe, and their powered state. */
public interface IWireManager {

    IPipeHolder getHolder();

    void updateBetweens(boolean recursive);

    @Nullable
    DyeColor getColorOfPart(EnumWirePart part);

    @Nullable
    DyeColor removePart(EnumWirePart part);

    boolean addPart(EnumWirePart part, DyeColor colour);

    boolean hasPartOfColor(DyeColor color);

    boolean isPowered(EnumWirePart part);

    boolean isAnyPowered(DyeColor color);
}
