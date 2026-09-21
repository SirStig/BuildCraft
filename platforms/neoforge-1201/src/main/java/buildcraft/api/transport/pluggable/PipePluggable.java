/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pluggable;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;

/**
 * Something attached to one face of a pipe: a gate, a plug, a facade, a robot station.
 *
 * <p>Four 1.12.2 hooks had nothing to map onto and are dropped rather than renamed, all of them for the same
 * underlying reason -- a pluggable was pretending to be a block face, and the vanilla concepts it borrowed no
 * longer exist:
 *
 * <ul>
 * <li>{@code getBlockFaceShape()} returned a {@code BlockFaceShape}, removed in 1.13 when block shapes became
 *     {@code VoxelShape}. {@link #isSideSolid()} already covers what BuildCraft asked it.</li>
 * <li>{@code getModelRenderKey(BlockRenderLayer)} took a {@code BlockRenderLayer}, which is now a
 *     {@code RenderType} chosen per model rather than passed in. The model key belongs to the rendering
 *     rewrite, so it comes back with it.</li>
 * <li>{@code getBlockColor(int tintIndex)} was {@code @SideOnly(Side.CLIENT)} and is part of that same
 *     rewrite.</li>
 * <li>{@code canBeConnected()} referenced {@code Block.canBeConnectedTo}, which no longer exists.</li>
 * </ul>
 *
 * <p>Otherwise the changes match {@link buildcraft.api.transport.pipe.PipeBehaviour}: no
 * {@code ICapabilityProvider}, {@link LogicalSide} for {@code Side}, {@link FriendlyByteBuf} for
 * {@code PacketBuffer}, {@link AABB} for {@code AxisAlignedBB}, and the hit vector on the
 * {@link BlockHitResult} rather than three loose floats.
 */
public abstract class PipePluggable {

    public final PluggableDefinition definition;
    public final IPipeHolder holder;
    public final Direction side;

    public PipePluggable(PluggableDefinition definition, IPipeHolder holder, Direction side) {
        this.definition = definition;
        this.holder = holder;
        this.side = side;
    }

    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    /**
     * Writes the payload that will be passed into {@link PluggableDefinition#loadFromBuffer} on the client.
     * Called on the server and sent to the client. Note this is called <em>instead</em> of
     * {@link #writePayload}/{@link #readPayload}.
     */
    public void writeCreationPayload(FriendlyByteBuf buffer) {
    }

    public void writePayload(FriendlyByteBuf buffer, LogicalSide side) {
    }

    public void readPayload(FriendlyByteBuf buffer, LogicalSide side) throws IOException {
    }

    public final void scheduleNetworkUpdate() {
        holder.scheduleNetworkUpdate(PipeMessageReceiver.PLUGGABLES[side.ordinal()]);
    }

    public void onTick() {
    }

    /** @return A bounding box used for collisions and ray tracing. */
    public abstract AABB getBoundingBox();

    /** @return True if the pipe cannot connect outwards, or false if this does not block the pipe. */
    public boolean isBlocking() {
        return false;
    }

    /** @return This pluggable's implementation of the given capability, as seen from outside the pipe. */
    @Nullable
    public <T> T getCapability(Capability<T> cap) {
        return null;
    }

    /** @return The capability accessible from the pipe this is attached to. */
    @Nullable
    public <T> T getInternalCapability(Capability<T> cap) {
        return null;
    }

    /** Called whenever this pluggable is removed from the pipe. */
    public void onRemove() {
    }

    /** @param toDrop A list of all the items to drop; add yours to it. */
    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
        ItemStack stack = getPickStack();
        if (!stack.isEmpty()) {
            toDrop.add(stack);
        }
    }

    /**
     * Called whenever this pluggable is picked by the player, similar to {@code Block.getCloneItemStack}.
     *
     * @return The stack that should be picked, or {@link ItemStack#EMPTY} if none can be.
     */
    public ItemStack getPickStack() {
        return ItemStack.EMPTY;
    }

    public boolean onPluggableActivate(Player player, BlockHitResult trace) {
        return false;
    }

    /** The pluggable equivalent of a solid block face. */
    public boolean isSideSolid() {
        return false;
    }

    public float getExplosionResistance(@Nullable Entity exploder, Explosion explosion) {
        return 0;
    }

    public boolean canConnectToRedstone(@Nullable Direction to) {
        return false;
    }

    public void onPlacedBy(Player player) {
    }
}
