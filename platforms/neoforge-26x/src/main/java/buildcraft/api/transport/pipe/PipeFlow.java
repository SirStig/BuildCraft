/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.capabilities.BlockCapability;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipeHolder.IWriter;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

/**
 * What a pipe <em>carries</em> -- items, fluids, power -- as opposed to what it is, which is
 * {@link PipeBehaviour}.
 *
 * <p>The same three changes as {@link PipeBehaviour}: no {@code ICapabilityProvider}, {@link LogicalSide} in
 * place of {@code Side}, and {@link RegistryFriendlyByteBuf} in place of {@code PacketBuffer}.
 */
public abstract class PipeFlow {

    /** The ID for completely refreshing the state of this flow. */
    public static final int NET_ID_FULL_STATE = 0;

    /**
     * The ID for updating what has changed since the last {@link #NET_ID_FULL_STATE} or {@link #NET_ID_UPDATE}
     * was sent.
     */
    public static final int NET_ID_UPDATE = 1;

    public final IPipe pipe;

    public PipeFlow(IPipe pipe) {
        this.pipe = pipe;
    }

    public PipeFlow(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        this.pipe = pipe;
    }

    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    /** Writes a payload with the specified id. Standard ids are {@link #NET_ID_FULL_STATE} and
     * {@link #NET_ID_UPDATE}. */
    public void writePayload(int id, RegistryFriendlyByteBuf buffer, LogicalSide side) {
    }

    /** Reads a payload with the specified id. Standard ids are {@link #NET_ID_FULL_STATE} and
     * {@link #NET_ID_UPDATE}. */
    public void readPayload(int id, RegistryFriendlyByteBuf buffer, LogicalSide side) throws IOException {
    }

    public void sendPayload(int id) {
        LogicalSide side = pipe.getHolder().getPipeLevel().isClientSide()
            ? LogicalSide.CLIENT
            : LogicalSide.SERVER;
        sendCustomPayload(id, buffer -> writePayload(id, buffer, side));
    }

    public final void sendCustomPayload(int id, IWriter writer) {
        pipe.getHolder().sendMessage(PipeMessageReceiver.FLOW, buffer -> {
            buffer.writeBoolean(true);
            buffer.writeShort(id);
            writer.write(buffer);
        });
    }

    public abstract boolean canConnect(Direction face, PipeFlow other);

    public abstract boolean canConnect(Direction face, BlockEntity oTile);

    /** Used to force a connection to a given block entity, even if the {@link PipeBehaviour} would not. */
    public boolean shouldForceConnection(Direction face, BlockEntity oTile) {
        return false;
    }

    public void onTick() {
    }

    public void postPluggableTick() {
    }

    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
    }

    public boolean onFlowActivate(Player player, BlockHitResult trace, EnumPipePart part) {
        return false;
    }

    /** @return This flow's implementation of the given capability, or null if it has none. */
    @Nullable
    public <T> T getCapability(BlockCapability<T, Direction> capability, @Nullable Direction facing) {
        return null;
    }
}
