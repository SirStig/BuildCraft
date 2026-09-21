/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.mj;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Provides a quick way to return all types of a single {@link IMjConnector} for the different capabilities.
 *
 * <p>This keeps 1.12.2's shape, because 1.20.1 still has {@link ICapabilityProvider}: a machine holds one of
 * these and delegates {@code getCapability} to it, and which of the five MJ interfaces the machine implements
 * is worked out once, here, with a chain of {@code instanceof}.
 *
 * <p>26.x removed that path entirely -- capabilities are registered against a block entity type up front -- so
 * the class of the same name there is a registration helper rather than a provider. That is the single biggest
 * shape difference between the two api trees.
 *
 * <p>The one change from 1.12.2 is that the capabilities are held as {@link LazyOptional}s and invalidated by
 * {@link #invalidate()}, which the owning block entity must call from {@code invalidateCaps}. 1.12.2 had no
 * such requirement because capabilities were fetched fresh every time.
 */
public class MjCapabilityHelper implements ICapabilityProvider {

    private final LazyOptional<IMjConnector> connector;
    private final LazyOptional<IMjReceiver> receiver;
    private final LazyOptional<IMjRedstoneReceiver> rsReceiver;
    private final LazyOptional<IMjReadable> readable;
    private final LazyOptional<IMjPassiveProvider> provider;
    private final LazyOptional<IEnergyStorage> rfAutoConvert;

    public MjCapabilityHelper(@NotNull IMjConnector mj) {
        this.connector = LazyOptional.of(() -> mj);
        this.receiver = optionalOf(mj, IMjReceiver.class);
        this.rsReceiver = optionalOf(mj, IMjRedstoneReceiver.class);
        this.readable = optionalOf(mj, IMjReadable.class);
        this.provider = optionalOf(mj, IMjPassiveProvider.class);
        this.rfAutoConvert = IMjToRfStatus.get().isAutoconvertEnabled()
            ? LazyOptional.of(() -> new MjToRfAutoConvertor(mj))
            : LazyOptional.empty();
    }

    private static <T> LazyOptional<T> optionalOf(IMjConnector mj, Class<T> type) {
        if (!type.isInstance(mj)) {
            return LazyOptional.empty();
        }
        T cast = type.cast(mj);
        return LazyOptional.of(() -> cast);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == MjCapabilities.CONNECTOR) {
            return connector.cast();
        }
        if (cap == MjCapabilities.RECEIVER) {
            return receiver.cast();
        }
        if (cap == MjCapabilities.REDSTONE_RECEIVER) {
            return rsReceiver.cast();
        }
        if (cap == MjCapabilities.READABLE) {
            return readable.cast();
        }
        if (cap == MjCapabilities.PASSIVE_PROVIDER) {
            return provider.cast();
        }
        if (cap == ForgeCapabilities.ENERGY) {
            return rfAutoConvert.cast();
        }
        return LazyOptional.empty();
    }

    /** Must be called from the owning block entity's {@code invalidateCaps}. */
    public void invalidate() {
        connector.invalidate();
        receiver.invalidate();
        rsReceiver.invalidate();
        readable.invalidate();
        provider.invalidate();
        rfAutoConvert.invalidate();
    }
}
