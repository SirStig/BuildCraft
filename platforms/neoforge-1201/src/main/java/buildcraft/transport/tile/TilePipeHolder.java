/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.tile;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

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
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.lib.tile.TileBC;

import buildcraft.BCTransportRegistries;
import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.block.EnumPipeActiveFace;
import buildcraft.transport.block.EnumPipeMaterial;
import buildcraft.transport.container.ContainerDiamondPipe;
import buildcraft.transport.container.ContainerDiamondWoodPipe;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.PipeEventBus;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDirectional;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;

/**
 * The single shared block entity every pipe kind uses -- implements the already-ported {@link IPipeHolder}. See
 * the 26.x copy of this class for the full account of what is deliberately dropped ({@code PluggableHolder},
 * every {@code NET_UPDATE_*} network message) and why, and of what {@link #scheduleNetworkUpdate} does now
 * (identical reasoning and implementation on this target). {@link #pluggables} is real as of the
 * wires/gates/pluggables batch -- see the 26.x copy's own javadoc for the full account, identical here bar the
 * direct {@link CompoundTag} read/write shape this target's {@link #load}/{@link #saveAdditional} already use.
 *
 * <p>The one real per-platform divergence: capabilities. 1.20.1 still has {@code ICapabilityProvider}, so this
 * tile exposes its own {@link #getCapability} override directly (matching {@code TileChute}'s own precedent on
 * this target), rather than 26.x's separate {@code RegisterCapabilitiesEvent} listener.
 */
public class TilePipeHolder extends TileBC implements IPipeHolder, MenuProvider {

    @Nullable
    private Pipe pipe;
    public final PipeEventBus eventBus = new PipeEventBus();
    private final SimplePipeWireManager wireManager = new SimplePipeWireManager(this);
    private final EnumMap<Direction, PipePluggable> pluggables = new EnumMap<>(Direction.class);

    @Nullable
    private UUID ownerId;
    private String ownerName = "";

    public TilePipeHolder(BlockPos pos, BlockState state) {
        super(BCTransportRegistries.PIPE_HOLDER_TYPE.get(), pos, state);
    }

    // Read + write

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("pipe")) {
            try {
                // HolderLookup.Provider is unused by every PipeBehaviour/PipeFlow this batch ports on this
                // target (see TravellingItem's own javadoc) -- kept only for cross-platform constructor parity,
                // so a null level here (possible mid-deserialisation) is harmless.
                Pipe loaded = new Pipe(this, nbt.getCompound("pipe"), level == null ? null : level.registryAccess());
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
        }
        if (nbt.contains("wireManager")) {
            wireManager.readFromNbt(nbt.getCompound("wireManager"));
        }
        ownerId = nbt.hasUUID("ownerId") ? nbt.getUUID("ownerId") : null;
        ownerName = nbt.getString("ownerName");

        // A full-map replace, matching pipe's own "reload replaces wholesale" precedent above -- see this
        // class's own javadoc.
        pluggables.clear();
        if (nbt.contains("pluggables")) {
            CompoundTag pluggablesTag = nbt.getCompound("pluggables");
            HolderLookup.Provider registries = level == null ? null : level.registryAccess();
            for (Direction side : Direction.values()) {
                if (!pluggablesTag.contains(side.getName())) {
                    continue;
                }
                CompoundTag sideTag = pluggablesTag.getCompound(side.getName());
                String idStr = sideTag.getString("id");
                if (idStr.isEmpty()) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(idStr);
                PluggableDefinition definition = id == null ? null : PipeApi.pluggableRegistry.getDefinition(id);
                if (definition == null) {
                    continue;
                }
                pluggables.put(side, definition.readFromNbt(this, side, sideTag.getCompound("data"), registries));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (pipe != null) {
            nbt.put("pipe", pipe.writeToNbt(level == null ? null : level.registryAccess()));
        }
        nbt.put("wireManager", wireManager.writeToNbt());
        if (ownerId != null) {
            nbt.putUUID("ownerId", ownerId);
            nbt.putString("ownerName", ownerName);
        }
        if (!pluggables.isEmpty()) {
            HolderLookup.Provider registries = level == null ? null : level.registryAccess();
            CompoundTag pluggablesTag = new CompoundTag();
            for (Map.Entry<Direction, PipePluggable> entry : pluggables.entrySet()) {
                CompoundTag sideTag = new CompoundTag();
                sideTag.putString("id", entry.getValue().definition.identifier.toString());
                sideTag.put("data", entry.getValue().writeToNbt(registries));
                pluggablesTag.put(entry.getKey().getName(), sideTag);
            }
            nbt.put("pluggables", pluggablesTag);
        }
    }

    // Misc

    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player) {
            ownerId = player.getUUID();
            ownerName = player.getGameProfile().getName();
        }
        if (stack.getItem() instanceof IItemPipe itemPipe) {
            PipeDefinition definition = itemPipe.getDefinition();
            this.pipe = new Pipe(this, definition);
            eventBus.registerHandler(pipe.behaviour);
            eventBus.registerHandler(pipe.flow);
            eventBus.fireEvent(new PipeEventPlaced(this, placer, stack));
        }
    }

    /** Driven by {@code BlockPipeHolder#getTicker}. */
    public void serverTick() {
        if (pipe != null) {
            pipe.onTick();
        }
        for (PipePluggable plug : pluggables.values()) {
            plug.onTick();
        }
        if (pipe != null) {
            pipe.postPluggableTick();
        }
        setChanged();
    }

    /** Notifies every attached pluggable that this tile is genuinely going away -- called from
     * {@code BlockPipeHolder#onRemove} (the block-level hook on this target; see PORTING.md's "Block-entity
     * genuine-removal hook" divergence entry for why this target's hook lives on the block, not the tile). */
    public void notifyPluggablesRemoved() {
        for (PipePluggable plug : pluggables.values()) {
            plug.onRemove();
        }
    }

    public void onNeighbourChanged() {
        if (pipe != null) {
            pipe.markForUpdate();
        }
    }

    /** Pushes the real connection/material shape onto the placed {@link BlockState} -- see the 26.x copy of this
     * method for the full account of why this lives here, needs no separate placement/load-time call, and reaches
     * this tile via an {@code instanceof TilePipeHolder} check on {@code Pipe}'s own {@code holder} field rather
     * than a new {@link IPipeHolder} method. {@code public}, not package-visible: {@code Pipe} lives in the
     * sibling {@code buildcraft.transport.pipe} package, so package-private access would not reach across. */
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

    @Override
    public boolean canPlayerInteract(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    @Nullable
    public PipePluggable getPluggable(Direction side) {
        return pluggables.get(side);
    }

    /** Every pluggable currently attached, keyed by side -- see the 26.x copy of this method's own javadoc. */
    public Map<Direction, PipePluggable> getPluggables() {
        return pluggables;
    }

    /** Attaches {@code pluggable} to {@code side}, replacing whatever was there -- see the 26.x copy of this
     * method's own javadoc. */
    public void setPluggable(Direction side, @Nullable PipePluggable pluggable) {
        if (pluggable == null) {
            pluggables.remove(side);
        } else {
            pluggables.put(side, pluggable);
        }
        markDirtyAndSync();
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
        if (neighbour == null) {
            return null;
        }
        return neighbour.getCapability(PipeApi.CAP_PIPE, side.getOpposite()).orElse(null);
    }

    @Override
    @Nullable
    public <T> T getCapabilityFromPipe(Direction side, Capability<T> capability) {
        PipePluggable plug = pluggables.get(side);
        if (plug != null) {
            T val = plug.getInternalCapability(capability);
            if (val != null) {
                return val;
            }
            if (plug.isBlocking()) {
                // A blocking pluggable fully occupies this face -- no pipe connection is left underneath it.
                return null;
            }
        }
        if (pipe == null || !pipe.isConnected(side)) {
            return null;
        }
        BlockEntity neighbour = getNeighbourTile(side);
        if (neighbour == null) {
            return null;
        }
        return neighbour.getCapability(capability, side.getOpposite()).orElse(null);
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

    /** No renderer needs a *block-model* rebuild in this batch -- see the 26.x copy of this method's own
     * javadoc. */
    @Override
    public void scheduleRenderUpdate() {
    }

    /** Pushes this tile's whole NBT-serialised state to every client tracking it -- see the 26.x copy of this
     * method's own javadoc for the real, investigated finding that made this necessary and why it is shaped this
     * coarsely. */
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

    /** See the 26.x copy of this method. */
    private static EnumPipeActiveFace activeFaceOf(IPipe forPipe) {
        if (forPipe.getBehaviour() instanceof PipeBehaviourDirectional directional) {
            return EnumPipeActiveFace.fromFacing(directional.getCurrentDir());
        }
        return EnumPipeActiveFace.NONE;
    }

    /** Re-pushes only {@link BlockPipeHolder#ACTIVE} whenever a directional behaviour changes its face outside a
     * connection recompute -- see the 26.x copy of this method for the full account. */
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

    @Override
    public void onPlayerOpen(Player player) {
    }

    @Override
    public void onPlayerClose(Player player) {
    }

    // MenuProvider -- new this batch, the diamond pipes' filter GUI. See the 26.x copy of this class's own
    // javadoc for the createMenu dispatch. No writeClientSideData override is needed on this target:
    // BlockPipeHolder's own use() calls NetworkHooks.openScreen(serverPlayer, holder, pos), whose 3-arg overload
    // already writes the BlockPos as extra data by itself (matching BlockDistiller's own precedent).

    @Override
    public Component getDisplayName() {
        if (pipe != null) {
            ItemPipeHolder item = BCTransportRegistries.getItemForPipe(pipe.getDefinition());
            if (item != null) {
                return Component.translatable(item.getDescriptionId());
            }
        }
        return Component.translatable("block.buildcraft.pipe_holder");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        if (pipe == null) {
            return null;
        }
        if (pipe.getBehaviour() instanceof PipeBehaviourWoodDiamond woodDiamond) {
            return new ContainerDiamondWoodPipe(windowId, playerInv, woodDiamond);
        }
        if (pipe.getBehaviour() instanceof PipeBehaviourDiamond diamond) {
            return new ContainerDiamondPipe(windowId, playerInv, diamond);
        }
        return null;
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

    @Override
    public boolean setRedstoneOutput(@Nullable Direction side, int value) {
        return false;
    }

    // Caps

    /** {@link PipeApi#CAP_PIPE_HOLDER}/{@link PipeApi#CAP_PIPE}/{@link PipeApi#CAP_PLUG} expose the tile/pipe/
     * pluggable objects themselves -- see the 26.x copy of this file's own {@code registerCapabilities} javadoc
     * for why this is load-bearing, not decorative: without it, {@code getNeighbourPipe} can never detect a
     * neighbouring pipe as a pipe, so two adjacent pipe segments never actually connect to each other. Direct
     * 1.20.1-shaped equivalent of 1.12.2's own {@code TilePipeHolder} constructor's
     * {@code caps.addCapabilityInstance}/{@code addCapability} calls. */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PipeApi.CAP_PIPE_HOLDER) {
            return LazyOptional.of(() -> (IPipeHolder) this).cast();
        }
        if (cap == PipeApi.CAP_PIPE && pipe != null) {
            IPipe p = pipe;
            return LazyOptional.of(() -> p).cast();
        }
        if (cap == PipeApi.CAP_PLUG) {
            PipePluggable plug = getPluggable(side);
            if (plug != null) {
                return LazyOptional.of(() -> plug).cast();
            }
        }
        if (side != null) {
            PipePluggable plug = pluggables.get(side);
            if (plug != null) {
                T val = plug.getCapability(cap);
                if (val != null) {
                    return LazyOptional.of(() -> val).cast();
                }
            }
        }
        if (pipe != null) {
            T val = pipe.getCapability(cap, side);
            if (val != null) {
                return LazyOptional.of(() -> val).cast();
            }
        }
        return super.getCapability(cap, side);
    }
}
