/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
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

/** The waypoint marker block for paths. Mirrors the 26.x class of the same name -- see that one for why
 * {@code TileBC_Neptune#getPermBlock()} is replaced with a direct {@link PermissionUtil#createFrom} call.
 * The only difference on this target is the interaction split: a single {@link #use} rather than 26.x's
 * {@code useWithoutItem} (see PORTING.md's divergence table). */
public class BlockMarkerPath extends BlockMarkerBase {
    public BlockMarkerPath(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileMarkerPath(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileMarkerPath marker) {
            if (PermissionUtil.hasPermission(PermissionUtil.PERM_EDIT, player, PermissionUtil.createFrom(level, pos))) {
                marker.reverseDirection();
            }
        }
        return InteractionResult.SUCCESS;
    }
}
