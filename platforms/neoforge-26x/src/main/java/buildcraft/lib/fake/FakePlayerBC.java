/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fake;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

import net.neoforged.neoforge.common.util.FakePlayer;

public class FakePlayerBC extends FakePlayer {
    public FakePlayerBC(ServerLevel level, GameProfile name) {
        super(level, name);
    }

    /**
     * 1.12.2's {@code openEditSign(TileEntitySign)} became {@code openTextEdit(SignBlockEntity, SignTextSlot)} --
     * signs now carry independent front/back text, so the slot being edited is part of the call.
     */
    @Override
    public void openTextEdit(SignBlockEntity signTile, SignTextSlot slot) {
        // TODO: Put this in NeoForge!
    }
}
