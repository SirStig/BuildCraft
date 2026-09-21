/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import net.minecraft.core.Direction;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A pipe flow that carries Forge energy.
 *
 * <p>{@code IEnergyStorage} is {@link EnergyHandler} on this target, and the extraction takes a
 * {@link TransactionContext} rather than performing the move immediately -- the caller decides whether it
 * sticks by committing or rolling back.
 */
public interface IFlowRedstoneFlux extends IFlowPowerLike {
    /** Makes this pipe reconfigure itself, possibly due to the addition of new modules. */
    @Override
    void reconfigure();

    /**
     * Attempts to extract power from the {@link EnergyHandler} connected to this pipe on the given side.
     *
     * @param maxPower The maximum amount of power that can be extracted.
     * @param from The side of this pipe to take power from.
     * @param transaction The enclosing transaction. This method never commits.
     * @return The amount of power extracted.
     */
    int tryExtractPower(int maxPower, Direction from, TransactionContext transaction);
}
