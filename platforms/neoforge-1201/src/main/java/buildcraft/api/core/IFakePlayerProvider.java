/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import net.minecraftforge.common.util.FakePlayer;

/** Supplies the fake players BuildCraft machines act through when they break or place blocks. */
public interface IFakePlayerProvider {
    /**
     * @param level The level the fake player will act in.
     * @param profile The owner's profile.
     * @return A fake player that can be used IN THE CURRENT METHOD CONTEXT ONLY. This will cause problems if the
     *         player is left around, as it holds a reference to the level.
     */
    FakePlayer getFakePlayer(ServerLevel level, GameProfile profile);

    /**
     * @param level The level the fake player will act in.
     * @param profile The owner's profile.
     * @param pos The position the fake player should be placed at.
     * @return A fake player that can be used IN THE CURRENT METHOD CONTEXT ONLY. This will cause problems if the
     *         player is left around, as it holds a reference to the level.
     */
    FakePlayer getFakePlayer(ServerLevel level, GameProfile profile, BlockPos pos);
}
