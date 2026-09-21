/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * The pipe inside a pipe holder: its definition, behaviour, flow and connections.
 *
 * <p>1.12.2 had this extend {@code ICapabilityProvider} so that callers could ask a pipe for a capability
 * directly. That interface does not exist on this target -- capabilities are resolved against a level and
 * position, not asked of an object -- so the single method that mattered is declared here instead, as
 * {@link #getCapability}.
 */
public interface IPipe {

    IPipeHolder getHolder();

    PipeDefinition getDefinition();

    PipeBehaviour getBehaviour();

    PipeFlow getFlow();

    @Nullable
    DyeColor getColour();

    void setColour(@Nullable DyeColor colour);

    void markForUpdate();

    /**
     * @return This pipe's own implementation of the given capability, or null if it has none. Replaces
     *         {@code ICapabilityProvider.getCapability}.
     */
    @Nullable
    <T> T getCapability(Capability<T> capability, @Nullable Direction side);

    @Nullable
    BlockEntity getConnectedTile(Direction side);

    @Nullable
    IPipe getConnectedPipe(Direction side);

    boolean isConnected(Direction side);

    @Nullable
    ConnectedType getConnectedType(Direction side);

    enum ConnectedType {
        TILE,
        PIPE
    }
}
