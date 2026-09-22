/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe;

import java.util.EnumMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.BlockCapability;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeConnectionAPI;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeEventConnectionChange;
import buildcraft.api.transport.pipe.PipeFlow;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * The pipe inside a {@link IPipeHolder}: its definition, behaviour, flow and connections. A close port of
 * 1.12.2's own {@code Pipe}, implementing the already-ported {@link IPipe}.
 *
 * <p><b>Deliberately not ported, both for the same reason: nothing client-side needs to observe a pipe's
 * connection state or behaviour data yet, because this batch has no client rendering at all.</b>
 * {@code writePayload}/{@code readPayload}/the network constructor ({@code Pipe(IPipeHolder, PacketBufferBC,
 * MessageContext)}) do not exist here -- persistence goes through {@link #writeToNbt(HolderLookup.Provider)}/the
 * NBT constructor only, which the tile's own {@code loadAdditional}/{@code saveAdditional} drive directly.
 * {@code getModel()}/{@code PipeModelKey} are dropped with them; both only ever fed the (unported) renderer.
 *
 * <p>Connection tracking -- {@link #connected}/{@link #types}, {@link #updateConnections()},
 * {@link #canPipesConnect}/{@link #canBehavioursConnect}/{@link #canFlowsConnect} -- is real, server-side-only
 * logic, ported unchanged: this is what actually links neighbouring pipes (and neighbouring inventories) into a
 * working network, so it is very much in scope even though nothing renders it.
 *
 * <p>{@code hasCapability}/{@code getCapability(Capability<T>, EnumFacing)} collapse into the single
 * {@link #getCapability(BlockCapability, Direction)} below, matching {@link IPipe}'s own already-ported shape --
 * see that interface's javadoc for why.
 */
public final class Pipe implements IPipe {
    private static final float DEFAULT_CONNECTION_DISTANCE = 0.25f;

    public final IPipeHolder holder;
    public final PipeDefinition definition;
    public final PipeBehaviour behaviour;
    public final PipeFlow flow;
    private DyeColor colour = null;
    private boolean updateMarked = true;
    private final EnumMap<Direction, Float> connected = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, ConnectedType> types = new EnumMap<>(Direction.class);

    public Pipe(IPipeHolder holder, PipeDefinition definition) {
        this.holder = holder;
        this.definition = definition;
        this.behaviour = definition.logicConstructor.createBehaviour(this);
        this.flow = definition.flowType.creator.createFlow(this);
    }

    /** Loads a pipe (and its behaviour/flow) back from NBT written by {@link #writeToNbt}. */
    public Pipe(IPipeHolder holder, CompoundTag nbt, HolderLookup.Provider registries) throws InvalidInputDataException {
        this.holder = holder;
        this.colour = NBTUtilBC.readEnum(nbt.get("col"), DyeColor.class);
        this.definition = PipeRegistry.INSTANCE.loadDefinition(nbt.getStringOr("def", ""));
        if (!definition.canBeColoured) {
            colour = null;
        }
        this.behaviour = definition.logicLoader.loadBehaviour(this, nbt.getCompoundOrEmpty("beh"), registries);
        this.flow = definition.flowType.loader.loadFlow(this, nbt.getCompoundOrEmpty("flow"), registries);

        int connectionData = nbt.getIntOr("con", 0);
        for (Direction face : Direction.values()) {
            int data = (connectionData >>> (face.ordinal() * 2)) & 0b11;
            // The only important aspect of this is the pipe type, since the texture index is only used
            // client-side (unported) and the distance only matters server-side for item travel timing, which is
            // minor enough that the default is close enough after a reload.
            if (data == 0b01) {
                connected.put(face, DEFAULT_CONNECTION_DISTANCE);
                types.put(face, ConnectedType.PIPE);
            } else if (data == 0b10) {
                connected.put(face, DEFAULT_CONNECTION_DISTANCE);
                types.put(face, ConnectedType.TILE);
            }
        }
    }

    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        nbt.put("col", NBTUtilBC.writeEnum(colour));
        nbt.putString("def", definition.identifier.toString());
        nbt.put("beh", behaviour.writeToNbt(registries));
        nbt.put("flow", flow.writeToNbt(registries));

        int connectionData = 0;
        for (Direction face : Direction.values()) {
            ConnectedType type = types.get(face);
            if (type != null) {
                int data = type == ConnectedType.PIPE ? 0b01 : 0b10;
                connectionData |= data << (face.ordinal() * 2);
            }
        }
        nbt.putInt("con", connectionData);
        return nbt;
    }

    // IPipe

    @Override
    public IPipeHolder getHolder() {
        return holder;
    }

    @Override
    public PipeDefinition getDefinition() {
        return definition;
    }

    @Override
    public PipeBehaviour getBehaviour() {
        return behaviour;
    }

    @Override
    public PipeFlow getFlow() {
        return flow;
    }

    @Override
    @Nullable
    public DyeColor getColour() {
        return this.colour;
    }

    @Override
    public void setColour(@Nullable DyeColor colour) {
        if (definition.canBeColoured) {
            this.colour = colour;
            markForUpdate();
        }
    }

    // Caps

    @Override
    @Nullable
    public <T> T getCapability(BlockCapability<T, Direction> capability, @Nullable Direction facing) {
        T val = behaviour.getCapability(capability, facing);
        if (val != null) return val;
        return flow.getCapability(capability, facing);
    }

    // misc

    public void onLoad() {
        markForUpdate();
    }

    public void onTick() {
        if (updateMarked) {
            // Ensure that the behaviour and flow *always* get valid connection data
            // (for example if we just read from disk)
            updateConnections();
        }
        behaviour.onTick();
        flow.onTick();
        if (updateMarked) {
            updateConnections();
        }
    }

    public void postPluggableTick() {
        flow.postPluggableTick();
    }

    private void updateConnections() {
        if (holder.getPipeLevel().isClientSide()) {
            return;
        }
        updateMarked = false;

        EnumMap<Direction, Float> old = connected.clone();

        connected.clear();
        types.clear();

        for (Direction facing : Direction.values()) {
            PipePluggable plug = getHolder().getPluggable(facing);
            if (plug != null && plug.isBlocking()) {
                continue;
            }
            BlockEntity oTile = getHolder().getNeighbourTile(facing);
            if (oTile == null) {
                continue;
            }
            IPipe oPipe = getHolder().getNeighbourPipe(facing);
            if (oPipe != null) {
                var nPosPlug = holder.getPipePos().relative(facing);
                PipePluggable oPlug = holder.getPipeLevel()
                    .getCapability(PipeApi.CAP_PLUG, nPosPlug, facing.getOpposite());
                if (oPlug == null || !oPlug.isBlocking()) {
                    if (canPipesConnect(facing, this, oPipe)) {
                        connected.put(facing, DEFAULT_CONNECTION_DISTANCE);
                        types.put(facing, ConnectedType.PIPE);
                    }
                    continue;
                }
            }

            var nPos = holder.getPipePos().relative(facing);
            BlockState neighbour = holder.getPipeLevel().getBlockState(nPos);

            var cust = PipeConnectionAPI.getCustomConnection(neighbour.getBlock());
            if (cust == null) {
                cust = DefaultPipeConnection.INSTANCE;
            }
            float ext = DEFAULT_CONNECTION_DISTANCE
                + cust.getExtension(holder.getPipeLevel(), nPos, facing.getOpposite(), neighbour);

            if (behaviour.shouldForceConnection(facing, oTile) || flow.shouldForceConnection(facing, oTile)
                || (behaviour.canConnect(facing, oTile) && flow.canConnect(facing, oTile))) {
                connected.put(facing, ext);
                types.put(facing, ConnectedType.TILE);
            }
        }
        if (!old.equals(connected)) {
            for (Direction face : Direction.values()) {
                boolean o = old.containsKey(face);
                boolean n = connected.containsKey(face);
                if (o != n) {
                    IPipe oPipe = getHolder().getNeighbourPipe(face);
                    if (oPipe != null) {
                        oPipe.markForUpdate();
                    }
                    holder.fireEvent(new PipeEventConnectionChange(holder, face));
                }
            }
        }
        getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
    }

    public static boolean canPipesConnect(Direction to, IPipe one, IPipe two) {
        return canColoursConnect(one.getColour(), two.getColour())
            && canBehavioursConnect(to, one.getBehaviour(), two.getBehaviour())
            && canFlowsConnect(to, one.getFlow(), two.getFlow());
    }

    public static boolean canColoursConnect(@Nullable DyeColor one, @Nullable DyeColor two) {
        return one == null || two == null || one == two;
    }

    public static boolean canBehavioursConnect(Direction to, PipeBehaviour one, PipeBehaviour two) {
        return one.canConnect(to, two) && two.canConnect(to.getOpposite(), one);
    }

    public static boolean canFlowsConnect(Direction to, PipeFlow one, PipeFlow two) {
        return one.canConnect(to, two) && two.canConnect(to.getOpposite(), one);
    }

    @Override
    public void markForUpdate() {
        updateMarked = true;
    }

    @Override
    @Nullable
    public BlockEntity getConnectedTile(Direction side) {
        if (connected.containsKey(side)) {
            BlockEntity offset = getHolder().getNeighbourTile(side);
            if (offset == null && !getHolder().getPipeLevel().isClientSide()) {
                markForUpdate();
            } else {
                return offset;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public IPipe getConnectedPipe(Direction side) {
        if (connected.containsKey(side) && getConnectedType(side) == ConnectedType.PIPE) {
            IPipe offset = getHolder().getNeighbourPipe(side);
            if (offset == null && !getHolder().getPipeLevel().isClientSide()) {
                markForUpdate();
            } else {
                return offset;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public ConnectedType getConnectedType(Direction side) {
        return types.get(side);
    }

    @Override
    public boolean isConnected(Direction side) {
        return connected.containsKey(side);
    }

    public float getConnectedDist(Direction face) {
        Float custom = connected.get(face);
        return custom == null ? 0 : custom;
    }
}
