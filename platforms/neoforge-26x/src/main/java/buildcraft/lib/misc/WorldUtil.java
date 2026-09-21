/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public final class WorldUtil {

    private WorldUtil() {
    }

    /**
     * @return True if this level's <em>default</em> game type is creative.
     *
     *         <p>1.12.2 read this from {@code world.getWorldInfo().getGameType()}. A level's
     *         {@code LevelData} no longer carries a game type on the client, and the server's default is a
     *         property of the server rather than of one level, so this goes through the server and answers
     *         false on the client.
     *
     *         <p>Note this was always the wrong question to ask about a <em>player</em> -- a player's own
     *         game mode can differ from the level default. Callers wanting that want
     *         {@code player.isCreative()}.
     */
    public static boolean isLevelCreative(Level level) {
        MinecraftServer server = level.getServer();
        return server != null && server.getDefaultGameType().isCreative();
    }
}
