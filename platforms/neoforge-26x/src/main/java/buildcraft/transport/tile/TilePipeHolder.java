/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.tile;

import java.util.EnumMap;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.block.EnumPipeActiveFace;
import buildcraft.transport.block.EnumPipeMaterial;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.PipeEventBus;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDirectional;

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
 * message -- 1.12.2's own per-message granularity, not brought back even now that {@link #scheduleNetworkUpdate}
 * does real work again (see that method's own javadoc): this port's sync goes through the coarser, already-
 * existing whole-tile {@link buildcraft.lib.tile.TileBC#markDirtyAndSync()} instead. Persistence goes through
 * {@link #loadAdditional}/{@link #saveAdditional} only.
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
                Pipe loaded = new Pipe(this, tag, input.lookup());
                // A reload replaces an existing Pipe (every client sync, /data merge), so the old one's handlers
                // must leave the bus or they keep firing -- e.g. a stale directional behaviour vetoing sides.
                if (pipe != null) {
                    eventBus.unregisterHandler(pipe.behaviour);
                    eventBus.unregisterHandler(pipe.flow);
                }
                pipe = loaded;
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

    /** Pushes the real connection/material shape onto the placed {@link BlockState} -- called from
     * {@link Pipe#updateConnections()} every time it recomputes, the "Pipe holds a direct reference back to its
     * own holder and calls a method on it" shape (an {@code instanceof TilePipeHolder} check on {@code Pipe}'s
     * own {@code holder} field, since {@link IPipeHolder} itself stays free of this block-specific concern --
     * {@code TilePipeHolder} is the only real implementation, confirmed by a repo-wide search finding no other
     * {@code implements IPipeHolder}). {@code public}, not package-visible: {@code Pipe} lives in a sibling
     * package ({@code buildcraft.transport.pipe}, not {@code buildcraft.transport.tile}), so package-private
     * access does not reach across; kept out of {@link IPipeHolder} itself all the same, since it is a block-
     * state-rendering concern this tile's own interface has no business exposing to every other holder.
     *
     * <p>Unlike {@code TileEngineBase#updateFacingBlockState} (which needs an explicit call from both the wrench
     * path and {@code onPlacedBy}, because facing is only ever recomputed on those two events), this needs no
     * separate {@code onPlacedBy}/load-time call: {@code Pipe#updateMarked} already starts {@code true} on both
     * of {@code Pipe}'s own constructors, so the very next real {@link #serverTick()} after either a fresh
     * placement or a disk-based reload runs {@code updateConnections()} regardless -- the same tick-driven
     * guarantee the pipe-materials batch's own RCON rig already relies on (see its PORTING.md entry). Material
     * is recomputed on every call rather than cached separately: {@code EnumPipeMaterial.fromId} is a cheap
     * five-way string compare, and a pipe's material never actually changes after placement, so recomputing it
     * alongside the connections it always changes with is simpler than a second "has this run yet" flag.
     * {@link BlockPipeHolder#ACTIVE} is pushed here too (see {@link #activeFaceOf}), and additionally from
     * {@link #scheduleNetworkUpdate} whenever a directional behaviour changes its face on its own. */
    public void updateConnectionBlockState(Pipe forPipe, EnumMap<Direction, IPipe.ConnectedType> connectionTypes) {
        if (level == null || level.isClientSide()) {
            return;
        }
        EnumPipeMaterial material = EnumPipeMaterial.fromId(forPipe.getDefinition().identifier.getPath());
        BlockState state = getBlockState();
        BlockState newState = state
            .setValue(BlockPipeHolder.MATERIAL, material)
            .setValue(BlockPipeHolder.ACTIVE, activeFaceOf(forPipe))
            .setValue(BlockStateProperties.NORTH, connectionTypes.containsKey(Direction.NORTH))
            .setValue(BlockStateProperties.SOUTH, connectionTypes.containsKey(Direction.SOUTH))
            .setValue(BlockStateProperties.EAST, connectionTypes.containsKey(Direction.EAST))
            .setValue(BlockStateProperties.WEST, connectionTypes.containsKey(Direction.WEST))
            .setValue(BlockStateProperties.UP, connectionTypes.containsKey(Direction.UP))
            .setValue(BlockStateProperties.DOWN, connectionTypes.containsKey(Direction.DOWN));
        if (newState != state) {
            level.setBlock(worldPosition, newState, Block.UPDATE_CLIENTS);
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

    /** No renderer needs a *block-model* rebuild (a {@code BlockState} recompute) in this batch -- the one real
     * renderer that does exist ({@code RenderTilePipeHolder}) reads the tile's own live state every frame instead,
     * so there is nothing for this to schedule. Distinct from {@link #scheduleNetworkUpdate}, which now does
     * real work -- see that method's own javadoc. */
    @Override
    public void scheduleRenderUpdate() {
    }

    /** Pushes this tile's whole NBT-serialised state to every client tracking it, via {@link #markDirtyAndSync()}
     * -- see {@code PipeFlowItems#getTravellingItemsForRender()}'s own javadoc for the real, investigated finding
     * that made this necessary (a client-side {@code TilePipeHolder} never ticks its own {@code Pipe} at all, so
     * this is the *only* way it ever learns a travelling item's {@code tickStarted}/{@code tickFinished} changed).
     * Coarser than 1.12.2's own per-{@code PipeMessageReceiver} granularity ({@code parts} is intentionally
     * ignored beyond "was this even called") -- this port dropped the whole per-message network layer
     * ({@code NET_UPDATE_*}) that granularity depended on, and every real caller of this method today
     * ({@code Pipe#updateConnections} for {@code BEHAVIOUR}, {@code PipeFlowItems}' five real call sites for
     * {@code FLOW}) already only fires at a genuinely-changed moment, not on some tight per-tick cadence, so a
     * whole-tile resync per call is cheap enough not to need finer targeting. */
    @Override
    public void scheduleNetworkUpdate(PipeMessageReceiver... parts) {
        if (parts.length > 0) {
            for (PipeMessageReceiver part : parts) {
                if (part == PipeMessageReceiver.BEHAVIOUR) {
                    updateActiveFaceBlockState();
                    break;
                }
            }
            markDirtyAndSync();
        }
    }

    /** The {@link BlockPipeHolder#ACTIVE} value for a pipe: its {@link PipeBehaviourDirectional} face, or
     * {@link EnumPipeActiveFace#NONE} for every non-directional material. */
    private static EnumPipeActiveFace activeFaceOf(IPipe forPipe) {
        if (forPipe.getBehaviour() instanceof PipeBehaviourDirectional directional) {
            return EnumPipeActiveFace.fromFacing(directional.getCurrentDir());
        }
        return EnumPipeActiveFace.NONE;
    }

    /** Re-pushes only {@link BlockPipeHolder#ACTIVE} -- called from {@link #scheduleNetworkUpdate} for
     * {@code BEHAVIOUR}, which is what {@code PipeBehaviourDirectional#setCurrentDir} sends whenever the face
     * changes outside a connection recompute (a wrench cycle, or the tick-time fallback picking a new face after
     * {@code Pipe#updateConnections} already ran this tick). A no-op when nothing changed, so the extra call
     * {@code Pipe#updateConnections}' own {@code BEHAVIOUR} update also triggers costs one comparison. */
    private void updateActiveFaceBlockState() {
        if (level == null || level.isClientSide() || pipe == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(BlockPipeHolder.ACTIVE)) {
            return;
        }
        BlockState newState = state.setValue(BlockPipeHolder.ACTIVE, activeFaceOf(pipe));
        if (newState != state) {
            level.setBlock(worldPosition, newState, Block.UPDATE_CLIENTS);
        }
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
