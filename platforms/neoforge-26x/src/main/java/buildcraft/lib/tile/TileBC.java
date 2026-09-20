/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base class for every BuildCraft block entity.
 *
 * <p>This is deliberately <em>not</em> a port of 1.12.2's 773-line {@code TileBC_Neptune}. Most of that class was
 * BuildCraft's own network layer -- id-tagged payloads, a player-tracking set, deferred network guards, and the
 * {@code NetworkUpdateType} enum -- all of which NeoForge now provides. What remains worth keeping is the small bit
 * that every machine actually needs: mark-dirty-and-sync, and client sync on chunk load.
 *
 * <p>The rest of {@code TileBC_Neptune} (item handlers, tank handlers, the GUI/container link, and the debug
 * interface) comes back as machines need it, rather than being carried over wholesale.
 *
 * <p>Note for anyone porting a machine: on 26.x, block entity serialisation goes through {@code ValueInput}/
 * {@code ValueOutput} rather than {@code CompoundTag}, so {@code readFromNBT}/{@code writeToNBT} become
 * {@code loadAdditional}/{@code saveAdditional} with a different parameter type. 1.20.1 still uses {@code CompoundTag}.
 */
public abstract class TileBC extends BlockEntity {

    protected TileBC(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Saves this block entity and pushes its new state to every client tracking the chunk.
     *
     * <p>The 1.12.2 equivalent was {@code sendNetworkUpdate(NET_RENDER_DATA)}. Call it whenever a change needs to be
     * visible client-side; plain {@link #setChanged()} only marks the chunk for saving.
     */
    public void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Sends {@link #getUpdateTag} to clients when this block entity changes. */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Sent to clients on chunk load. Defaults to the full saved state, which is what BuildCraft machines want. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
