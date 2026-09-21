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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.capabilities.BlockCapability;

import buildcraft.api.core.EnumPipePart;

/**
 * What a pipe <em>is</em> -- wooden, iron, diamond -- as opposed to what it carries, which is {@link PipeFlow}.
 *
 * <p>Three parts of the 1.12.2 signature had to change.
 *
 * <ul>
 * <li>{@code ICapabilityProvider} does not exist, so the {@code hasCapability}/{@code getCapability} pair
 *     collapses into the single {@link #getCapability} below -- "has" was always just a null check.</li>
 * <li>{@code Side} is {@link LogicalSide}, and {@code MessageContext} is gone with the 1.12 packet system. The
 *     context only ever supplied the player and the thread, both of which the pipe holder already knows, so
 *     {@code readPayload} takes the side alone.</li>
 * <li>{@code PacketBuffer} is {@link RegistryFriendlyByteBuf}: writing an item or fluid into a payload needs
 *     registry access on this target.</li>
 * </ul>
 *
 * <p>Also {@code RayTraceResult} is {@link BlockHitResult}, and the {@code hitX/hitY/hitZ} floats that went
 * with it are gone -- the hit vector is on the result itself now.
 */
public abstract class PipeBehaviour {

    public final IPipe pipe;

    public PipeBehaviour(IPipe pipe) {
        this.pipe = pipe;
    }

    public PipeBehaviour(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        this.pipe = pipe;
    }

    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    public void writePayload(RegistryFriendlyByteBuf buffer, LogicalSide side) {
    }

    public void readPayload(RegistryFriendlyByteBuf buffer, LogicalSide side) throws IOException {
    }

    /** @deprecated Replaced by {@link #getTextureData(Direction)}. */
    @Deprecated
    public int getTextureIndex(@Nullable Direction face) {
        return 0;
    }

    /**
     * Gets the texture data to use for the specified face.
     *
     * @param face Null indicates the centre of the pipe.
     * @return The texture data for the given face. May be null, but only for the centre, which indicates that
     *         the centre will use the face texture instead.
     */
    @Nullable
    public PipeFaceTex getTextureData(@Nullable Direction face) {
        return PipeFaceTex.get(getTextureIndex(face));
    }

    // ###############
    //
    // Event handling
    //
    // ###############

    public boolean canConnect(Direction face, PipeBehaviour other) {
        return true;
    }

    public boolean canConnect(Direction face, BlockEntity oTile) {
        return true;
    }

    /** Used to force a connection to a given block entity, even if the {@link PipeFlow} would not connect. */
    public boolean shouldForceConnection(Direction face, BlockEntity oTile) {
        return false;
    }

    public boolean onPipeActivate(Player player, BlockHitResult trace, EnumPipePart part) {
        return false;
    }

    public void onEntityCollide(Entity entity) {
    }

    public void onTick() {
    }

    /**
     * @return This behaviour's implementation of the given capability, or null if it has none. Replaces
     *         {@code ICapabilityProvider}; the old {@code hasCapability} was a null check on this.
     */
    @Nullable
    public <T> T getCapability(BlockCapability<T, Direction> capability, @Nullable Direction facing) {
        return null;
    }

    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
    }
}
