/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.capabilities.BlockCapability;

import buildcraft.api.statements.containers.IRedstoneStatementContainer;
import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pluggable.PipePluggable;

/**
 * Designates a block entity that can contain a pipe and up to six sided pluggables.
 *
 * <p>Two things changed beyond the type renames. {@code Capability<T>} is {@link BlockCapability}, so
 * {@link #getCapabilityFromPipe} is keyed by a block capability and a side rather than by a capability alone.
 * And {@code PacketBuffer} is {@link RegistryFriendlyByteBuf} rather than plain {@code FriendlyByteBuf}: a
 * pluggable writing an item or fluid into its custom message needs registry access to do it, and the registry
 * aware buffer is what the payload codecs hand out.
 */
public interface IPipeHolder extends IRedstoneStatementContainer {

    Level getPipeLevel();

    BlockPos getPipePos();

    BlockEntity getPipeTile();

    IPipe getPipe();

    /**
     * @return True if the player should be able to interact with the pipe holder in GUI form. Implementors
     *         should generally check that they are still present in-world.
     */
    boolean canPlayerInteract(Player player);

    @Nullable
    PipePluggable getPluggable(Direction side);

    @Nullable
    BlockEntity getNeighbourTile(Direction side);

    @Nullable
    IPipe getNeighbourPipe(Direction side);

    /**
     * Gets the given capability going outwards from the pipe. This tests
     * {@link PipePluggable#getInternalCapability} first, then looks at the neighbouring block.
     */
    @Nullable
    <T> T getCapabilityFromPipe(Direction side, BlockCapability<T, Direction> capability);

    IWireManager getWireManager();

    GameProfile getOwner();

    /** @return True if at least one handler received this event, false if not. */
    boolean fireEvent(PipeEvent event);

    void scheduleRenderUpdate();

    /** @param parts The parts that want to send a network update. */
    void scheduleNetworkUpdate(PipeMessageReceiver... parts);

    /**
     * Schedules a GUI network update: only the players who currently have a pipe element open in a GUI are
     * updated.
     *
     * @param parts The parts that want to send a network update.
     */
    void scheduleNetworkGuiUpdate(PipeMessageReceiver... parts);

    /**
     * Sends a custom message from a pluggable or pipe centre to the server or client, depending on which side
     * this is currently on.
     */
    void sendMessage(PipeMessageReceiver to, IWriter writer);

    void sendGuiMessage(PipeMessageReceiver to, IWriter writer);

    /** Called on the server whenever a GUI container object is opened. */
    void onPlayerOpen(Player player);

    /** Called on the server whenever a GUI container object is closed. */
    void onPlayerClose(Player player);

    enum PipeMessageReceiver {
        BEHAVIOUR(null),
        FLOW(null),
        PLUGGABLE_DOWN(Direction.DOWN),
        PLUGGABLE_UP(Direction.UP),
        PLUGGABLE_NORTH(Direction.NORTH),
        PLUGGABLE_SOUTH(Direction.SOUTH),
        PLUGGABLE_WEST(Direction.WEST),
        PLUGGABLE_EAST(Direction.EAST),
        /** Wires are updated differently and never use this API. */
        WIRES(null);

        public static final PipeMessageReceiver[] VALUES = values();
        public static final PipeMessageReceiver[] PLUGGABLES = new PipeMessageReceiver[6];

        static {
            for (PipeMessageReceiver type : VALUES) {
                if (type.face != null) {
                    PLUGGABLES[type.face.ordinal()] = type;
                }
            }
        }

        @Nullable
        public final Direction face;

        PipeMessageReceiver(@Nullable Direction face) {
            this.face = face;
        }
    }

    @FunctionalInterface
    interface IWriter {
        void write(RegistryFriendlyByteBuf buffer);
    }
}
