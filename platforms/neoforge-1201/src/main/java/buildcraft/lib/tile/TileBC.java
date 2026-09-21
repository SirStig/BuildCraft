/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.lib.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base class for every BuildCraft block entity, for Minecraft 1.20.1.
 *
 * <p>Mirrors the 26.x class of the same name, and is deliberately not a port of 1.12.2's 773-line
 * {@code TileBC_Neptune} -- see the 26.x version for why. The difference between the two targets is serialisation:
 * 1.20.1 still uses {@code CompoundTag} throughout, so the hooks are {@code load}/{@code saveAdditional} and
 * {@code getUpdateTag()} takes no registry lookup.
 */
public abstract class TileBC extends BlockEntity {

    protected TileBC(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Saves this block entity and pushes its new state to every client tracking the chunk.
     *
     * <p>The 1.12.2 equivalent was {@code sendNetworkUpdate(NET_RENDER_DATA)}. Plain {@link #setChanged()} only
     * marks the chunk for saving.
     */
    public void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Sent to clients on chunk load. Defaults to the full saved state, which is what BuildCraft machines want. */
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }
}
