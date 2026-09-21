/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Presents an MJ machine to the rest of the world as a Forge energy handler.
 *
 * <p>In 1.12.2 this was an anonymous {@code IEnergyStorage} inside {@code MjCapabilityHelper}, and it could
 * simply pass {@code simulate} straight through to the MJ side, because both APIs spelled "don't really do it"
 * the same way.
 *
 * <p>26.x does not. Energy moves inside a {@link net.neoforged.neoforge.transfer.transaction.Transaction} that
 * may still be rolled back after {@link #insert} returns, and MJ's {@code receivePower}/{@code extractPower}
 * have no rollback of their own -- once a machine has taken power there is no un-taking it. Passing the
 * transaction's simulate flag through would therefore duplicate energy on every rolled-back transaction.
 *
 * <p>So this participates in the transaction properly, as a {@link SnapshotJournal}: each call simulates
 * against MJ, accumulates what it would move, and the real MJ call happens once, in
 * {@link #onRootCommit}. A rollback restores the accumulator and the machine is never touched.
 *
 * <p>Receive and extract are counted separately because each is monotonic on its own within a transaction --
 * which is what lets a second call in the same transaction simulate the running total and subtract, rather than
 * simulating against a machine state that has not moved yet and over-accepting.
 */
public class MjToRfAutoConvertor extends SnapshotJournal<long[]> implements EnergyHandler {

    private final IMjConnector connector;

    @Nullable
    private final IMjReceiver receiver;

    @Nullable
    private final IMjReadable readable;

    @Nullable
    private final IMjPassiveProvider provider;

    /** Micro MJ this transaction would push into the machine. */
    private long pendingReceive;

    /** Micro MJ this transaction would pull out of the machine. */
    private long pendingExtract;

    public MjToRfAutoConvertor(IMjConnector connector) {
        this.connector = connector;
        this.receiver = connector instanceof IMjReceiver r ? r : null;
        this.readable = connector instanceof IMjReadable r ? r : null;
        this.provider = connector instanceof IMjPassiveProvider p ? p : null;
    }

    private static long mjPerRf() {
        return IMjToRfStatus.get().getConversion().mjPerRf;
    }

    // ###############
    //
    // Transaction participation
    //
    // ###############

    @Override
    protected long[] createSnapshot() {
        return new long[] { pendingReceive, pendingExtract };
    }

    @Override
    protected void revertToSnapshot(long[] snapshot) {
        pendingReceive = snapshot[0];
        pendingExtract = snapshot[1];
    }

    @Override
    protected void onRootCommit(long[] snapshot) {
        long toReceive = pendingReceive;
        long toExtract = pendingExtract;
        pendingReceive = 0;
        pendingExtract = 0;
        if (toReceive > 0 && receiver != null) {
            receiver.receivePower(toReceive, false);
        }
        if (toExtract > 0 && provider != null) {
            provider.extractPower(0, toExtract, false);
        }
    }

    // ###############
    //
    // EnergyHandler
    //
    // ###############

    @Override
    public long getAmountAsLong() {
        IMjReadable read = readable;
        return read == null ? 0 : read.getStored() / mjPerRf();
    }

    @Override
    public long getCapacityAsLong() {
        IMjReadable read = readable;
        return read == null ? 0 : read.getCapacity() / mjPerRf();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        IMjReceiver rec = receiver;
        if (rec == null || amount <= 0 || !rec.canReceive()) {
            return 0;
        }
        long microJoules = amount * mjPerRf();
        // Simulate the running total, not just this call: the machine has not moved yet, so simulating the
        // new amount alone would let a second call in the same transaction accept the same room twice.
        long wanted = pendingReceive + microJoules;
        long accepted = wanted - rec.receivePower(wanted, true);
        long newlyAccepted = accepted - pendingReceive;
        if (newlyAccepted <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        pendingReceive = accepted;
        return (int) (newlyAccepted / mjPerRf());
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        IMjPassiveProvider prov = provider;
        if (prov == null || amount <= 0) {
            return 0;
        }
        long microJoules = amount * mjPerRf();
        long wanted = pendingExtract + microJoules;
        long available = prov.extractPower(0, wanted, true);
        long newlyAvailable = available - pendingExtract;
        if (newlyAvailable <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        pendingExtract = available;
        return (int) (newlyAvailable / mjPerRf());
    }

    /** The {@link IMjConnector} this wraps, for anything that wants the MJ side back. */
    public IMjConnector getConnector() {
        return connector;
    }
}
