/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

import org.jetbrains.annotations.Nullable;

import net.minecraftforge.energy.IEnergyStorage;

/**
 * Presents an MJ machine to the rest of the world as a Forge energy handler.
 *
 * <p>This is the 1.20.1 copy, and it is the straightforward one: {@link IEnergyStorage} still spells
 * "don't really do it" as a {@code boolean simulate}, exactly as MJ does, so the flag passes straight through.
 *
 * <p>The 26.x copy cannot do that. There, energy moves inside a transaction that may be rolled back after the
 * call returns, and MJ has no rollback, so it has to accumulate and apply the MJ side once at commit. See the
 * 26.x class for the detail.
 */
public class MjToRfAutoConvertor implements IEnergyStorage {

    private final IMjConnector connector;

    @Nullable
    private final IMjReceiver receiver;

    @Nullable
    private final IMjReadable readable;

    @Nullable
    private final IMjPassiveProvider provider;

    public MjToRfAutoConvertor(IMjConnector connector) {
        this.connector = connector;
        this.receiver = connector instanceof IMjReceiver r ? r : null;
        this.readable = connector instanceof IMjReadable r ? r : null;
        this.provider = connector instanceof IMjPassiveProvider p ? p : null;
    }

    private static long mjPerRf() {
        return IMjToRfStatus.get().getConversion().mjPerRf;
    }

    @Override
    public int getEnergyStored() {
        IMjReadable read = readable;
        return read == null ? 0 : (int) (read.getStored() / mjPerRf());
    }

    @Override
    public int getMaxEnergyStored() {
        IMjReadable read = readable;
        return read == null ? 0 : (int) (read.getCapacity() / mjPerRf());
    }

    @Override
    public boolean canReceive() {
        return receiver != null && receiver.canReceive();
    }

    @Override
    public boolean canExtract() {
        return provider != null;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        IMjReceiver rec = receiver;
        if (rec == null || maxReceive <= 0 || !rec.canReceive()) {
            return 0;
        }
        long microJoules = maxReceive * mjPerRf();
        long leftOver = rec.receivePower(microJoules, simulate);
        return (int) ((microJoules - leftOver) / mjPerRf());
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        IMjPassiveProvider prov = provider;
        if (prov == null || maxExtract <= 0) {
            return 0;
        }
        long microJoules = maxExtract * mjPerRf();
        return (int) (prov.extractPower(0, microJoules, simulate) / mjPerRf());
    }

    /** The {@link IMjConnector} this wraps, for anything that wants the MJ side back. */
    public IMjConnector getConnector() {
        return connector;
    }
}
