/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.server.ServerLifecycleHooks;

import buildcraft.api.core.BCLog;

/** 1.12.2's {@code AdvancementManager#getAdvancement}/{@code PlayerAdvancements#grantCriterion} pair became
 * {@code ServerAdvancementManager#get} (returning an {@link AdvancementHolder}, the id+advancement pair every
 * advancement is wrapped in since data-driven advancements were reworked) and {@code PlayerAdvancements#award}.
 * The manager itself moved from the world to the server -- {@code WorldServer#getAdvancementManager()} is gone,
 * replaced by {@code MinecraftServer#getAdvancements()} -- and {@code FMLCommonHandler.instance()
 * .getMinecraftServerInstance()} is {@link ServerLifecycleHooks#getCurrentServer()} now. */
public class AdvancementUtil {
    private static final Set<Identifier> UNKNOWN_ADVANCEMENTS = new HashSet<>();

    public static void unlockAdvancement(Player player, Identifier advancementName) {
        if (player instanceof ServerPlayer playerMP) {
            ServerAdvancementManager advancementManager = playerMP.level().getServer().getAdvancements();
            AdvancementHolder advancement = advancementManager.get(advancementName);
            if (advancement != null) {
                // never assume the advancement exists, we create them but they are removable by datapacks
                PlayerAdvancements tracker = playerMP.getAdvancements();
                // When the fake player gets constructed it will set itself to the main player advancement tracker
                // (So this just harmlessly removes it)
                tracker.setPlayer(playerMP);
                tracker.award(advancement, "code_trigger");
            } else if (UNKNOWN_ADVANCEMENTS.add(advancementName)) {
                BCLog.logger.warn("[lib.advancement] Attempted to trigger undefined advancement: " + advancementName);
            }
        }
    }

    public static boolean unlockAdvancement(UUID player, Identifier advancementName) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer playerMP = server == null ? null : server.getPlayerList().getPlayer(player);
        if (playerMP != null) {
            unlockAdvancement((Player) playerMP, advancementName);
            return true;
        }
        return false;
    }
}
