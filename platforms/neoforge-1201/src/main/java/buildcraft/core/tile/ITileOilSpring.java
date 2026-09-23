/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.tile;

import java.util.UUID;

import net.minecraft.core.BlockPos;

/** Implemented by {@code buildcraft.energy.tile.TileSpringOil}. Referenced (but left unimplemented) by
 * {@code buildcraft.factory.tile.TilePump}'s own javadoc, which anticipated this exact interface -- see that
 * class's own account of the oil-drain advancement it is not yet wired up to trigger.
 *
 * <p>1.12.2's version took a {@code com.mojang.authlib.GameProfile}; this takes just the player's {@link UUID}
 * (all {@code AdvancementUtil#unlockAdvancement} -- the only real consumer of a pumper's identity -- ever needed
 * from it), which sidesteps needing a full profile-NBT codec for this pass. */
public interface ITileOilSpring {

    /** Pumps should call this when they pump oil from this spring. */
    void onPumpOil(UUID pumpOwner, BlockPos oilPos);

}
