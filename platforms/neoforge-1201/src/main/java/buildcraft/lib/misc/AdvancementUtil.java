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

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.server.ServerLifecycleHooks;

import buildcraft.api.core.BCLog;

/** 1.12.2's {@code AdvancementManager#getAdvancement}/{@code PlayerAdvancements#grantCriterion} pair became
 * {@code ServerAdvancementManager#getAdvancement} and {@code PlayerAdvancements#award} here (26.x additionally
 * wraps the advancement itself in an {@code AdvancementHolder} -- this target still hands back a plain {@link
 * Advancement}). The manager itself moved from the world to the server -- {@code WorldServer
 * #getAdvancementManager()} is gone, replaced by {@code MinecraftServer#getAdvancements()} -- and {@code
 * FMLCommonHandler.instance().getMinecraftServerInstance()} is {@link ServerLifecycleHooks#getCurrentServer()}
 * now. */
public class AdvancementUtil {
    private static final Set<ResourceLocation> UNKNOWN_ADVANCEMENTS = new HashSet<>();

    public static void unlockAdvancement(Player player, ResourceLocation advancementName) {
        if (player instanceof ServerPlayer playerMP) {
            ServerAdvancementManager advancementManager = playerMP.serverLevel().getServer().getAdvancements();
            Advancement advancement = advancementManager.getAdvancement(advancementName);
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

    public static boolean unlockAdvancement(UUID player, ResourceLocation advancementName) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer playerMP = server == null ? null : server.getPlayerList().getPlayer(player);
        if (playerMP != null) {
            unlockAdvancement((Player) playerMP, advancementName);
            return true;
        }
        return false;
    }
}
