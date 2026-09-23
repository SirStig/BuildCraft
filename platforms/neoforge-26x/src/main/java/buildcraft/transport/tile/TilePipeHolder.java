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

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * The single shared block entity every pipe kind uses, whatever {@link PipeDefinition} it was placed with --
 * implements the already-ported {@link IPipeHolder}. A trimmed port of 1.12.2's own {@code TilePipeHolder}
 * (609 lines): only the members {@link IPipeHolder}'s own interface actually requires, plus whatever
 * {@link Pipe}/{@code PipeFlowItems} genuinely need to function, per this batch's own scope -- see each member's
 * own javadoc below for what was kept, simplified, or stubbed and why.
 *
 * <p><b>Pluggables</b> ({@link #pluggables}) are real as of the wires/gates/pluggables batch: up to one
 * {@link PipePluggable} per {@link Direction}, persisted whole (definition id plus its own NBT) under the
 * {@code "pluggables"} key, same as {@link #pipe} -- a full-map replace on every load, not an incremental diff,
 * matching this class's existing "reload replaces {@code pipe} wholesale" precedent. {@code PluggableHolder}
 * itself (1.12.2's per-message network envelope) is still not ported, and every
 * {@code NET_UPDATE_*}/{@code writePayload}/{@code readPayload} message stays dropped for the same reason as
 * before: this port's sync goes through the coarser, already-existing whole-tile
 * {@link buildcraft.lib.tile.TileBC#markDirtyAndSync()} instead. Persistence goes through
 * {@link #loadAdditional}/{@link #saveAdditional} only.
 *
 * <p><b>Pluggable event handlers</b> (added this pass, for the accessory-pluggables batch: {@code PluggableLens}/
 * {@code Timer}/{@code LightSensor}/{@code Pulsar}) -- every attach point ({@link #loadAdditional}'s pluggable
 * loop, {@link #setPluggable}, {@link #preRemoveSideEffects}) now also registers/unregisters the pluggable itself
 * with {@link #eventBus}, mirroring {@link #pipe}'s own behaviour/flow registration immediately above. Before
 * this pass no pluggable had any {@code @PipeEventHandler} method, so the gap was invisible; a lens's item-flow
 * filtering and a timer/light-sensor/pulsar's trigger/action offers are the first pluggables that actually need
 * to be on the bus to do anything at all.
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

        // A full-map replace, matching pipe's own "reload replaces wholesale" precedent above -- see this
        // class's own javadoc. Any pluggable not mentioned in this sync is genuinely gone (removed, or never
        // existed on a fresh tile), so the map is cleared first rather than merged. Every replaced pluggable's
        // own @PipeEventHandler methods (real as of the accessory-pluggables batch: PluggableLens/Timer/
        // LightSensor/Pulsar) must leave the bus the same way pipe's own reload does above, or a stale one keeps
        // answering trigger/action queries and item-flow events after being replaced.
        for (PipePluggable old : pluggables.values()) {
            eventBus.unregisterHandler(old);
        }
        pluggables.clear();
        input.read("pluggables", CompoundTag.CODEC).ifPresent(tag -> {
            for (Direction side : Direction.values()) {
                if (!(tag.get(side.getName()) instanceof CompoundTag sideTag)) {
                    continue;
                }
                String idStr = sideTag.getStringOr("id", "");
                if (idStr.isEmpty()) {
                    continue;
                }
                Identifier id = Identifier.tryParse(idStr);
                PluggableDefinition definition = id == null ? null : PipeApi.pluggableRegistry.getDefinition(id);
                if (definition == null) {
                    continue;
                }
                CompoundTag data = sideTag.getCompoundOrEmpty("data");
                PipePluggable loaded = definition.readFromNbt(this, side, data, input.lookup());
                pluggables.put(side, loaded);
                eventBus.registerHandler(loaded);
            }
        });
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
        if (!pluggables.isEmpty() && level != null) {
            CompoundTag pluggablesTag = new CompoundTag();
            for (Map.Entry<Direction, PipePluggable> entry : pluggables.entrySet()) {
                CompoundTag sideTag = new CompoundTag();
                sideTag.putString("id", entry.getValue().definition.identifier.toString());
                sideTag.put("data", entry.getValue().writeToNbt(level.registryAccess()));
                pluggablesTag.put(entry.getKey().getName(), sideTag);
            }
            output.store("pluggables", CompoundTag.CODEC, pluggablesTag);
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
        for (PipePluggable plug : pluggables.values()) {
            plug.onTick();
        }
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

    /** Checks the tile is still validly placed -- matching {@code Container.stillValidBlockEntity}'s own "same
     * block entity, within reach" precedent. Used by {@code IPipeHolder} callers generally, not by the
     * {@link MenuProvider} menus below (their own {@code AbstractContainerMenu#stillValid} does the equivalent
     * check independently). */
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

    /** Every pluggable currently attached, keyed by side -- used by {@code BlockPipeHolder#getDrops} to drop
     * each one, and by this class's own {@code preRemoveSideEffects}. Not part of {@link IPipeHolder}: nothing
     * outside {@code buildcraft.transport} needs the whole map at once, only {@link #getPluggable} per side. */
    public Map<Direction, PipePluggable> getPluggables() {
        return pluggables;
    }

    /** Attaches {@code pluggable} to {@code side}, replacing whatever was there. Called from
     * {@code BlockPipeHolder}'s item-use dispatch (placing a new pluggable) -- see that class's own javadoc.
     * Not part of {@link IPipeHolder}: placement is a block-interaction concern, not a pipe-internal one. */
    public void setPluggable(Direction side, @Nullable PipePluggable pluggable) {
        PipePluggable old = pluggables.get(side);
        if (old != null) {
            eventBus.unregisterHandler(old);
        }
        if (pluggable == null) {
            pluggables.remove(side);
        } else {
            pluggables.put(side, pluggable);
            eventBus.registerHandler(pluggable);
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
        if (neighbour == null || level == null) {
            return null;
        }
        return level.getCapability(PipeApi.CAP_PIPE, worldPosition.relative(side), side.getOpposite());
    }

    @Override
    @Nullable
    public <T> T getCapabilityFromPipe(Direction side, BlockCapability<T, Direction> capability) {
        PipePluggable plug = pluggables.get(side);
        if (plug != null) {
            T val = plug.getInternalCapability(capability);
            if (val != null) {
                return val;
            }
            if (plug.isBlocking()) {
                // A blocking pluggable (a facade, a gate, a blocker plug) fully occupies this face -- there is
                // no pipe connection left underneath it for the neighbour to be reached through.
                return null;
            }
        }
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

    /** Not used by the diamond pipes' new {@code ContainerDiamondPipe}/{@code ContainerDiamondWoodPipe} menus
     * below (their own {@code stillValid} already tracks the same thing generically) -- kept a no-op for every
     * other pipe material, which still has no GUI at all. */
    @Override
    public void onPlayerOpen(Player player) {
    }

    @Override
    public void onPlayerClose(Player player) {
    }

    // MenuProvider

    /** The diamond pipes' filter-configuration GUI title -- the placed item's own display name, read back through
     * {@link BCTransportRegistries#getItemForPipe}, so every material this ever applies to gets a correct title
     * with no per-material title table. Never actually shown for any other material: {@link #createMenu} returns
     * {@code null} for those, and a {@code null}-returning {@link MenuProvider} is never opened in the first
     * place (see {@code BlockPipeHolder#activatePipeBehaviour}, which only calls {@code Player#openMenu} after
     * the behaviour itself already answered {@code true} to {@code onPipeActivate}). */
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

    /** Dispatches to the one {@link PipeBehaviour} type (of the two new this batch) that actually has a menu --
     * {@link PipeBehaviourWoodDiamond} is checked first since it extends {@link PipeBehaviourDiamond}'s own
     * sibling class hierarchy only in name, not in type (the two are unrelated classes, both extending
     * {@code PipeBehaviour} directly), so the order here does not matter for correctness, only for
     * documentation clarity. Returns {@code null} (no menu) for every other material -- matching
     * {@code BlockDistiller}'s own "nothing to open" contract for a tile with no work to do, except here the
     * common case is "not this kind of pipe" rather than "not ready yet". */
    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        if (pipe == null) {
            return null;
        }
        if (pipe.getBehaviour() instanceof PipeBehaviourWoodDiamond woodDiamond) {
            return new ContainerDiamondWoodPipe(
                BCTransportRegistries.PIPE_DIAMOND_WOOD_MENU.get(), windowId, playerInv, woodDiamond);
        }
        if (pipe.getBehaviour() instanceof PipeBehaviourDiamond diamond) {
            return new ContainerDiamondPipe(BCTransportRegistries.PIPE_DIAMOND_MENU.get(), windowId, playerInv, diamond);
        }
        return null;
    }

    /** The server-side half of {@code ContainerDiamondPipe}/{@code ContainerDiamondWoodPipe}'s own client-side
     * factory constructor -- matches {@code TileDistiller#writeClientSideData}'s own precedent exactly (this
     * tile's {@link BlockPos}, nothing else needed to look the pipe back up on the client). */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(getBlockPos());
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
        if (facing != null) {
            PipePluggable plug = pluggables.get(facing);
            if (plug != null) {
                T val = plug.getCapability(capability);
                if (val != null) {
                    return val;
                }
            }
        }
        if (pipe != null) {
            T val = pipe.getCapability(capability, facing);
            if (val != null) {
                return val;
            }
        }
        return null;
    }

    /** Notifies every attached pluggable that this tile is genuinely going away (not just unloading) -- see
     * PORTING.md's "Block-entity genuine-removal hook" divergence entry for why this, rather than
     * {@code onChunkUnloaded}/{@code setRemoved}, is the correct place for this. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        for (PipePluggable plug : pluggables.values()) {
            plug.onRemove();
            eventBus.unregisterHandler(plug);
        }
    }
}
