/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.lib.block.BlockMarkerBase;
import buildcraft.lib.misc.PermissionUtil;

import buildcraft.core.tile.TileMarkerPath;

/** The waypoint marker block for paths. Unlike {@link BlockMarkerVolume}, this block does not override
 * {@code neighborChanged} at all, so it keeps {@link BlockMarkerBase}'s self-destruct-if-unsupported check.
 *
 * <p>1.12.2's {@code TileBC_Neptune#getPermBlock()} (used to build the {@code PermissionUtil.PermissionBlock}
 * for the edit-permission check below) doesn't exist on {@link buildcraft.lib.tile.TileBC} -- it was 1.12.2's
 * own shortcut for {@code new PermissionBlock(this, pos)}, and {@code TileBC} carries none of
 * {@code TileBC_Neptune}'s permission/ownership plumbing forward. {@link PermissionUtil#createFrom} already does
 * the same lookup generically (checking whether the block entity at a position implements
 * {@code IPlayerOwned}), so this calls that directly instead of adding the shortcut back. */
public class BlockMarkerPath extends BlockMarkerBase {
    public BlockMarkerPath(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileMarkerPath(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileMarkerPath marker) {
            if (PermissionUtil.hasPermission(PermissionUtil.PERM_EDIT, player, PermissionUtil.createFrom(level, pos))) {
                marker.reverseDirection();
            }
        }
        return InteractionResult.SUCCESS;
    }
}
