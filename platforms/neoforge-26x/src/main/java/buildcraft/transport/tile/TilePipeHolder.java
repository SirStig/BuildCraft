/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.tile;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.neoforged.neoforge.capabilities.BlockCapability;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeEvent;
import buildcraft.api.transport.pipe.PipeEventPlaced;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.tile.TileBC;

import buildcraft.BCTransportRegistries;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.PipeEventBus;

/**
 * The single shared block entity every pipe kind uses, whatever {@link PipeDefinition} it was placed with --
 * implements the already-ported {@link IPipeHolder}. A trimmed port of 1.12.2's own {@code TilePipeHolder}
 * (609 lines): only the members {@link IPipeHolder}'s own interface actually requires, plus whatever
 * {@link Pipe}/{@code PipeFlowItems} genuinely need to function, per this batch's own scope -- see each member's
 * own javadoc below for what was kept, simplified, or stubbed and why.
 *
 * <p><b>Wholesale dropped, both out of this batch's scope entirely:</b> {@code PluggableHolder} (no
 * {@link buildcraft.api.transport.pluggable.PipePluggable} exists in this batch at all -- see
 * {@link #getPluggable} below) and every {@code NET_UPDATE_*}/{@code writePayload}/{@code readPayload} network
 * message (no client rendering exists yet to sync state to -- see {@link #scheduleNetworkUpdate} and friends
 * below). Persistence goes through {@link #loadAdditional}/{@link #saveAdditional} only.
 */
public class TilePipeHolder extends TileBC implements IPipeHolder {

    @Nullable
    private Pipe pipe;
    public final PipeEventBus eventBus = new PipeEventBus();
    private final SimplePipeWireManager wireManager = new SimplePipeWireManager(this);

    @Nullable
    private UUID ownerId;
    private String ownerName = "";

    public TilePipeHolder(BlockPos pos, BlockState state) {
        super(BCTransportRegistries.PIPE_HOLDER_TYPE.get(), pos, state);
    }

    // Read + write

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("pipe", CompoundTag.CODEC).ifPresent(tag -> {
            try {
                pipe = new Pipe(this, tag, input.lookup());
                eventBus.registerHandler(pipe.behaviour);
                eventBus.registerHandler(pipe.flow);
            } catch (InvalidInputDataException e) {
                // Unfortunately we can't throw an exception, because then this tile won't persist at all.
                e.printStackTrace();
            }
        });
        input.read("wireManager", CompoundTag.CODEC).ifPresent(wireManager::readFromNbt);
        ownerId = input.read("ownerId", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("ownerName", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (pipe != null && level != null) {
            output.store("pipe", CompoundTag.CODEC, pipe.writeToNbt(level.registryAccess()));
        }
        output.store("wireManager", CompoundTag.CODEC, wireManager.writeToNbt());
        if (ownerId != null) {
            output.store("ownerId", UUIDUtil.CODEC, ownerId);
            output.putString("ownerName", ownerName);
        }
    }

    // Misc

    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player) {
            ownerId = player.getUUID();
            ownerName = player.getGameProfile().name();
        }
        if (stack.getItem() instanceof IItemPipe itemPipe) {
            PipeDefinition definition = itemPipe.getDefinition();
            this.pipe = new Pipe(this, definition);
            eventBus.registerHandler(pipe.behaviour);
            eventBus.registerHandler(pipe.flow);
            eventBus.fireEvent(new PipeEventPlaced(this, placer, stack));
        }
    }

    /** Driven by {@code BlockPipeHolder#getTicker}; was 1.12.2's {@code ITickable.update()}. */
    public void serverTick() {
        if (pipe != null) {
            pipe.onTick();
        }
        // No pluggables exist to tick in this batch -- see getPluggable's own javadoc.
        if (pipe != null) {
            pipe.postPluggableTick();
        }
        // 1.12.2 always marked the chunk dirty every tick rather than tracking exactly what changed -- kept
        // faithfully, since a travelling item queue can change in ways nothing else here would notice either.
        setChanged();
    }

    /** Called from {@code BlockPipeHolder#neighborChanged}: a neighbour changing might mean a new inventory
     * appeared (or disappeared) for this pipe to connect to. */
    public void onNeighbourChanged() {
        if (pipe != null) {
            pipe.markForUpdate();
        }
    }

    // IPipeHolder

    @Override
    public Level getPipeLevel() {
        return getLevel();
    }

    @Override
    public BlockPos getPipePos() {
        return getBlockPos();
    }

    @Override
    public BlockEntity getPipeTile() {
        return this;
    }

    @Override
    @Nullable
    public IPipe getPipe() {
        return pipe;
    }

    /** No GUI exists for this pipe in this batch, so this just checks the tile is still validly placed --
     * matching {@code Container.stillValidBlockEntity}'s own "same block entity, within reach" precedent. */
    @Override
    public boolean canPlayerInteract(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    /** Always {@code null}: no {@link PipePluggable} type is registered anywhere in this batch -- no gates, no
     * wires-as-placeable-items, no facades. {@code PluggableHolder} is not ported. */
    @Override
    @Nullable
    public PipePluggable getPluggable(Direction side) {
        return null;
    }

    @Override
    @Nullable
    public BlockEntity getNeighbourTile(Direction side) {
        if (level == null) {
            return null;
        }
        return level.getBlockEntity(worldPosition.relative(side));
    }

    @Override
    @Nullable
    public IPipe getNeighbourPipe(Direction side) {
        BlockEntity neighbour = getNeighbourTile(side);
        if (neighbour == null || level == null) {
            return null;
        }
        return level.getCapability(PipeApi.CAP_PIPE, worldPosition.relative(side), side.getOpposite());
    }

    @Override
    @Nullable
    public <T> T getCapabilityFromPipe(Direction side, BlockCapability<T, Direction> capability) {
        // getPluggable always returns null in this batch, so there is no pluggable to consult first.
        if (pipe == null || !pipe.isConnected(side) || level == null) {
            return null;
        }
        BlockEntity neighbour = getNeighbourTile(side);
        if (neighbour == null) {
            return null;
        }
        return level.getCapability(capability, worldPosition.relative(side), side.getOpposite());
    }

    @Override
    public IWireManager getWireManager() {
        return wireManager;
    }

    @Override
    public GameProfile getOwner() {
        return new GameProfile(ownerId != null ? ownerId : new UUID(0L, 0L), ownerName);
    }

    @Override
    public boolean fireEvent(PipeEvent event) {
        return eventBus.fireEvent(event);
    }

    /** No renderer exists in this batch, so there is nothing to schedule a render update for. */
    @Override
    public void scheduleRenderUpdate() {
    }

    /** No client sync exists in this batch -- see this class's own javadoc for why. */
    @Override
    public void scheduleNetworkUpdate(PipeMessageReceiver... parts) {
    }

    @Override
    public void scheduleNetworkGuiUpdate(PipeMessageReceiver... parts) {
    }

    @Override
    public void sendMessage(PipeMessageReceiver to, IWriter writer) {
    }

    @Override
    public void sendGuiMessage(PipeMessageReceiver to, IWriter writer) {
    }

    /** No GUI exists for this pipe in this batch. */
    @Override
    public void onPlayerOpen(Player player) {
    }

    @Override
    public void onPlayerClose(Player player) {
    }

    // IRedstoneStatementContainer

    @Override
    public int getRedstoneInput(@Nullable Direction side) {
        if (level == null) {
            return 0;
        }
        if (side == null) {
            return level.getBestNeighborSignal(worldPosition);
        }
        return level.getSignal(worldPosition.relative(side), side);
    }

    /** No gate/statement system exists anywhere in this batch that could ever call this with something real to
     * output, so this genuinely no-ops -- see this batch's own scope notes. */
    @Override
    public boolean setRedstoneOutput(@Nullable Direction side, int value) {
        return false;
    }

    // Caps

    /** Not an override -- {@code BlockEntity} has no instance {@code getCapability} method on this target (see
     * PORTING.md's capabilities entry). This is the convenience method {@code BCTransportRegistries}'
     * {@code RegisterCapabilitiesEvent} listener calls into for every capability this pipe wants to expose. */
    @Nullable
    public <T> T getCapability(BlockCapability<T, Direction> capability, @Nullable Direction facing) {
        if (pipe != null) {
            T val = pipe.getCapability(capability, facing);
            if (val != null) {
                return val;
            }
        }
        return null;
    }
}
