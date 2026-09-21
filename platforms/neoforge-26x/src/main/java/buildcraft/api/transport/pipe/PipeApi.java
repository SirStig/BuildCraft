/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.transport.pipe;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.capabilities.BlockCapability;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.IInjectable;
import buildcraft.api.transport.IStripesRegistry;
import buildcraft.api.transport.pluggable.IPluggableRegistry;
import buildcraft.api.transport.pluggable.PipePluggable;

/**
 * The central holding class for all pipe related registries and methods.
 *
 * <p>The four capabilities were built by {@code CapabilitiesHelper.registerCapability(Class)}, which is not
 * ported -- see {@code buildcraft.api.tiles.TilesAPI} for why. They are {@link BlockCapability}s created the
 * same way the MJ ones are, and like those they are sided, because a pipe exposes different behaviour per face.
 *
 * <p>The three {@code transferData} maps are wrapped in {@link Collections#synchronizedMap}: they are written
 * during mod loading, which is parallel now, and read on the server thread afterwards. An
 * {@link IdentityHashMap} is kept underneath because the keys are definition instances compared by identity.
 */
public final class PipeApi {

    /** Kept local rather than referencing the mod class, so the api package stays self-contained. */
    private static final String NAMESPACE = "buildcraft";

    public static IPipeRegistry pipeRegistry;
    public static IPluggableRegistry pluggableRegistry;
    public static IStripesRegistry stripeRegistry;
    public static IPipeExtensionManager extensionManager;

    public static PipeFlowType flowStructure;
    public static PipeFlowType flowItems;
    public static PipeFlowType flowFluids;
    public static PipeFlowType flowPower;
    public static PipeFlowType flowRf;

    /**
     * The default transfer information used if a pipe definition has not been registered. Replaced by
     * BuildCraft Transport with config-defined values.
     */
    public static FluidTransferInfo fluidInfoDefault = new FluidTransferInfo(20, 10);

    /** As {@link #fluidInfoDefault}, for power. */
    public static PowerTransferInfo powerInfoDefault =
        PowerTransferInfo.createFromResistance(8 * MjAPI.MJ, MjAPI.MJ / 32, false);

    /** As {@link #fluidInfoDefault}, for Forge energy. */
    public static RedstoneFluxTransferInfo rfInfoDefault = new RedstoneFluxTransferInfo(80, false);

    public static final Map<PipeDefinition, FluidTransferInfo> fluidTransferData =
        Collections.synchronizedMap(new IdentityHashMap<>());
    public static final Map<PipeDefinition, PowerTransferInfo> powerTransferData =
        Collections.synchronizedMap(new IdentityHashMap<>());
    public static final Map<PipeDefinition, RedstoneFluxTransferInfo> rfTransferData =
        Collections.synchronizedMap(new IdentityHashMap<>());

    public static final BlockCapability<IPipeHolder, Direction> CAP_PIPE_HOLDER =
        createSided("pipe_holder", IPipeHolder.class);

    public static final BlockCapability<IPipe, Direction> CAP_PIPE =
        createSided("pipe", IPipe.class);

    public static final BlockCapability<PipePluggable, Direction> CAP_PLUG =
        createSided("pipe_plug", PipePluggable.class);

    public static final BlockCapability<IInjectable, Direction> CAP_INJECTABLE =
        createSided("injectable", IInjectable.class);

    private PipeApi() {
    }

    private static <T> BlockCapability<T, Direction> createSided(String path, Class<T> type) {
        return BlockCapability.createSided(Identifier.fromNamespaceAndPath(NAMESPACE, path), type);
    }

    public static FluidTransferInfo getFluidTransferInfo(PipeDefinition def) {
        FluidTransferInfo info = fluidTransferData.get(def);
        return info == null ? fluidInfoDefault : info;
    }

    public static PowerTransferInfo getPowerTransferInfo(PipeDefinition def) {
        PowerTransferInfo info = powerTransferData.get(def);
        return info == null ? powerInfoDefault : info;
    }

    public static RedstoneFluxTransferInfo getRfTransferInfo(PipeDefinition def) {
        RedstoneFluxTransferInfo info = rfTransferData.get(def);
        return info == null ? rfInfoDefault : info;
    }

    public static class FluidTransferInfo {
        /**
         * The maximum amount of fluid that can be transferred around and out of a pipe per tick. This does not
         * affect the flow rate coming into the pipe.
         */
        public final int transferPerTick;

        /**
         * How long the pipe should delay incoming fluids by. The minimum is 1, because of the way fluids are
         * handled internally. Multiplied by the fluid's viscosity and divided by 100 to give the actual delay.
         */
        public final double transferDelayMultiplier;

        public FluidTransferInfo(int transferPerTick, int transferDelay) {
            this.transferPerTick = transferPerTick;
            this.transferDelayMultiplier = transferDelay <= 0 ? 1 : transferDelay;
        }
    }

    public static class PowerTransferInfo {
        public final long transferPerTick;
        public final long lossPerTick;
        /** The percentage resistance per tick. Should be between 0 and {@link MjAPI#MJ}. */
        public final long resistancePerTick;
        public final boolean isReceiver;

        /**
         * Sets resistancePerTick equal to lossPerTick when full power is being transferred, scaling down to 0.
         */
        public static PowerTransferInfo createFromLoss(
            long transferPerTick,
            long lossPerTick,
            boolean isReceiver
        ) {
            return new PowerTransferInfo(
                transferPerTick, lossPerTick, lossPerTick * MjAPI.MJ / transferPerTick, isReceiver
            );
        }

        /** Sets lossPerTick equal to resistancePerTick when full power is being transferred. */
        public static PowerTransferInfo createFromResistance(
            long transferPerTick,
            long resistancePerTick,
            boolean isReceiver
        ) {
            return new PowerTransferInfo(
                transferPerTick, resistancePerTick, resistancePerTick * transferPerTick / MjAPI.MJ, isReceiver
            );
        }

        public PowerTransferInfo(
            long transferPerTick,
            long lossPerTick,
            long resistancePerTick,
            boolean isReceiver
        ) {
            this.transferPerTick = Math.max(transferPerTick, 10);
            this.lossPerTick = lossPerTick;
            this.resistancePerTick = resistancePerTick;
            this.isReceiver = isReceiver;
        }
    }

    public static class RedstoneFluxTransferInfo {
        public final int transferPerTick;
        public final boolean isReceiver;

        public RedstoneFluxTransferInfo(int transferPerTick, boolean isReceiver) {
            this.transferPerTick = transferPerTick;
            this.isReceiver = isReceiver;
        }
    }
}
