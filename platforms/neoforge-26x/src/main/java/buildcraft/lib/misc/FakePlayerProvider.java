/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.IFakePlayerProvider;

import buildcraft.lib.fake.FakePlayerBC;

/** {@link IFakePlayerProvider} dropped the deprecated {@code getBuildCraftPlayer(WorldServer)} overload and the
 * {@code unloadWorld} lifecycle hook when it was ported (see that interface's own copy) -- neither survives
 * here either. {@code ServerPlayer} (which {@link FakePlayerBC} extends) no longer has a mutable {@code world}
 * field to reassign per call; {@link FakePlayerBC#setServerLevel} is the modern equivalent. */
public enum FakePlayerProvider implements IFakePlayerProvider {
    INSTANCE;

    /** The default {@link GameProfile} to use if a tile entity cannot determine its real owner. Most of the time
     * this shouldn't be necessary, as we should be able to get a {@link GameProfile} from all {@link
     * net.minecraft.world.entity.player.Player}'s that place or create tiles/robots */
    public static final GameProfile NULL_PROFILE;

    static {
        UUID id = UUID.nameUUIDFromBytes("buildcraft.core".getBytes(StandardCharsets.UTF_8));
        NULL_PROFILE = new GameProfile(id, "[BuildCraft]");
    }

    private final Map<GameProfile, FakePlayerBC> players = new HashMap<>();

    @Override
    public FakePlayerBC getFakePlayer(ServerLevel level, GameProfile profile) {
        return getFakePlayer(level, profile, BlockPos.ZERO);
    }

    @Override
    public FakePlayerBC getFakePlayer(ServerLevel level, GameProfile profile, BlockPos pos) {
        if (profile == null) {
            BCLog.logger.warn("[lib.fake] Null GameProfile! This is a bug!", new IllegalArgumentException());
            profile = NULL_PROFILE;
        }
        FakePlayerBC player = players.computeIfAbsent(profile, p -> new FakePlayerBC(level, p));
        player.setServerLevel(level);
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        return player;
    }

    public void unloadLevel(ServerLevel level) {
        players.values().removeIf(player -> player.level() == level);
    }
}
