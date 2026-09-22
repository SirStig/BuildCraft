/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.marker;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import buildcraft.BuildCraft;

/** The two game-event hooks the marker caches need, ported from 1.12.2's {@code BCLibEventDist#onEntityJoinWorld}/
 * {@code #onWorldUnload}. Neither was wired up by the earlier marker pass, which only mattered once something on the
 * client actually read the client-side cache -- the marker laser renderers now do:
 *
 * <ul>
 * <li>{@link #onEntityJoinLevel}: a player joining a level (logging in, respawning, or changing dimension) is sent
 * every marker and every connection in that level's cache ({@link MarkerSubCache#onPlayerJoinWorld}). Without it a
 * client only ever learned about connections made while it was already watching -- relog, and every box vanished.
 * Confirmed against the real 26.3 {@code PlayerList}/{@code ServerPlayer}: {@code EntityJoinLevelEvent} fires from
 * {@code ServerLevel#addPlayer}, which every one of those paths reaches only <em>after</em> it has sent the
 * {@code ClientboundLoginPacket}/{@code ClientboundRespawnPacket} that creates the client's new level, so the marker
 * payloads (sent on the same connection, in order) always land in the right client level. 1.12.2 delayed this by a
 * tick "to make it work in single-player"; that ordering guarantee makes the delay unnecessary here. The level is
 * taken from the event, not {@code player.level()}, since the player isn't formally in it yet.</li>
 * <li>{@link #onLevelUnload}: drops that level's cache ({@link MarkerCache#onWorldUnload}) on either side. On the
 * client this is what stops one world's boxes surviving into the next world joined with the same dimension key;
 * on the server the saved data still holds everything, so the cache simply reloads from it next time.</li>
 * </ul>
 *
 * <p>Plain {@code @EventBusSubscriber} (both dists): nothing here touches a client-only type. */
@EventBusSubscriber(modid = BuildCraft.MOD_ID)
public final class MarkerCacheEvents {
    private MarkerCacheEvents() {}

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Level level = event.getLevel();
            for (MarkerCache<?> cache : MarkerCache.CACHES) {
                cache.getSubCache(level).onPlayerJoinWorld(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            MarkerCache.onWorldUnload(level);
        }
    }
}
