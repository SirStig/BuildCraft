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

import net.minecraftforge.common.util.FakePlayer;

public class FakePlayerBC extends FakePlayer {
    public FakePlayerBC(ServerLevel level, GameProfile name) {
        super(level, name);
    }

    /**
     * 1.12.2's {@code openEditSign(TileEntitySign)} became {@code openTextEdit(SignBlockEntity, boolean)} here --
     * the boolean is which side of the sign ({@code true} = front) is being edited. 26.x instead takes a
     * {@code SignTextSlot} enum; see that copy for why.
     */
    @Override
    public void openTextEdit(SignBlockEntity signTile, boolean isFrontText) {
        // TODO: Put this in Forge!
    }
}
