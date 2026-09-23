/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.flow;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraftforge.common.capabilities.Capability;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjPassiveProvider;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.IFlowPower;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.PowerTransferInfo;
import buildcraft.api.transport.pipe.PipeEventPower;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.misc.MathUtil;

/**
 * The power-routing simulation -- see the 26.x copy of this file's own javadoc for the full account of the
 * algorithm and what was dropped (both copies port the identical 1.12.2 arithmetic; only the capability shape
 * differs). This copy's platform-specific notes:
 *
 * <p><b>1.20.1 keeps the classic per-tile capability path</b> ({@code ICapabilityProvider}/{@link Capability}/
 * {@code LazyOptional}), just like 1.12.2 itself did -- {@code TilePipeHolder#getCapability} already falls through
 * generically to {@code pipe.getCapability} -> {@code Pipe#getCapability} -> here (behaviour first, then flow),
 * wrapping whatever non-null value comes back in a {@code LazyOptional}, exactly the same route
 * {@code PipeBehaviourWood}'s own {@code MjCapabilityHelper}-based capability already uses on this target. No
 * change to {@code BCTransportRegistries} is needed for this, unlike the 26.x copy.
 *
 * <p><b>{@code MjCapabilityHelper} (the instance-based, per-object registration used elsewhere on this platform)
 * is deliberately not used here</b>, even though it would be the natural choice for a single-object capability
 * provider: it hands out exactly one canonical object for every capability/side combination, but this class needs
 * a *different* {@link Section} per face (an engine plugged into the north face must not see the south face's
 * queued power). {@link #getCapability} therefore dispatches by hand, the same shape 1.12.2's own
 * {@code PipeFlowPower#getCapability} already used (an unchecked {@code Capability<T>.cast}-equivalent) --
 * confirmed via {@code javap} against this target's real {@code forge-1.20.1-47.1.106.jar} that
 * {@code Capability<T>} has no {@code cast(Object)} method any more (1.12.2's own method of that name is gone), so
 * this uses the same plain unchecked {@code (T)} cast {@code PipeBehaviourWood}/{@code PipeFlowFluids} already use
 * elsewhere in this port for the identical reason.
 */
public class PipeFlowPower extends PipeFlow implements IFlowPower, IDebuggable {

    private static final long DEFAULT_MAX_POWER = MjAPI.MJ * 10;

    private long maxPower = -1;
    private long powerLoss = -1;
    private long powerResistance = -1;
    private boolean disabled = false;

    /** See the 26.x copy of this field's own javadoc. */
    private long currentWorldTime = -1;

    private boolean isReceiver = false;
    private final EnumMap<Direction, Section> sections;

    public PipeFlowPower(IPipe pipe) {
        super(pipe);
        sections = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            sections.put(face, new Section(face));
        }
    }

    public PipeFlowPower(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        isReceiver = nbt.getBoolean("isReceiver");
        sections = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            sections.put(face, new Section(face));
        }
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.putBoolean("isReceiver", isReceiver);
        return nbt;
    }

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof PipeFlowPower;
    }

    /** 1.12.2: {@code oTile.getCapability(MjAPI.CAP_CONNECTOR, face.getOpposite())}, plus a passive-provider check
     * when this pipe is itself a receiver. */
    @Override
    public boolean canConnect(Direction face, BlockEntity oTile) {
        if (isReceiver) {
            IMjPassiveProvider provider =
                oTile.getCapability(MjCapabilities.PASSIVE_PROVIDER, face.getOpposite()).orElse(null);
            if (provider != null) {
                return true;
            }
        }
        IMjConnector connector = oTile.getCapability(MjCapabilities.CONNECTOR, face.getOpposite()).orElse(null);
        return connector != null && connector.canConnect(sections.get(face));
    }

    @Override
    public void reconfigure() {
        PipeEventPower.Configure configure = new PipeEventPower.Configure(pipe.getHolder(), this);
        PowerTransferInfo pti = PipeApi.getPowerTransferInfo(pipe.getDefinition());
        configure.setReceiver(pti.isReceiver);
        configure.setMaxPower(pti.transferPerTick);
        configure.setPowerLoss(pti.lossPerTick);
        configure.setPowerResistance(pti.resistancePerTick);
        pipe.getHolder().fireEvent(configure);
        isReceiver = configure.isReceiver();
        maxPower = configure.getMaxPower();
        disabled = configure.isTransferDisabled();
        if (maxPower <= 0) {
            maxPower = DEFAULT_MAX_POWER;
        }
        powerLoss = MathUtil.clamp(configure.getPowerLoss(), -1, maxPower);
        powerResistance = MathUtil.clamp(configure.getPowerResistance(), -1, MjAPI.MJ);

        if (powerLoss < 0) {
            if (powerResistance < 0) {
                // 1% resistance
                powerResistance = MjAPI.MJ / 100;
            }
            powerLoss = maxPower * powerResistance / MjAPI.MJ;
        } else if (powerResistance < 0) {
            powerResistance = powerLoss * MjAPI.MJ / maxPower;
        }
    }

    /** See the 26.x copy of this method's own javadoc. */
    @Override
    public long tryExtractPower(long maxExtracted, Direction from) {
        return 0;
    }

    public Section getSection(Direction side) {
        return sections.get(side);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable Direction facing) {
        if (facing == null) {
            return null;
        } else if (capability == MjCapabilities.RECEIVER) {
            return isReceiver ? (T) sections.get(facing) : null;
        } else if (capability == MjCapabilities.CONNECTOR) {
            return (T) sections.get(facing);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("maxPower = " + MjAPI.formatMj(maxPower));
        left.add("isReceiver = " + isReceiver);
        left.add("internalPower = " + arrayToString(s -> s.internalPower));
        left.add("powerQuery = " + arrayToString(s -> s.powerQuery));
    }

    private String arrayToString(ToLongFunction<Section> getter) {
        StringBuilder sb = new StringBuilder("[");
        for (Direction face : Direction.values()) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(getter.applyAsLong(sections.get(face)) / MjAPI.MJ);
        }
        return sb.append(']').toString();
    }

    @Override
    public void onTick() {
        if (maxPower == -1) {
            reconfigure();
        }

        step();

        for (Direction face : Direction.values()) {
            Section s = sections.get(face);
            if (s.internalPower <= 0) {
                continue;
            }
            long totalPowerQuery = 0;
            for (Direction face2 : Direction.values()) {
                if (face != face2) {
                    totalPowerQuery += sections.get(face2).powerQuery;
                }
            }

            boolean returnPower = false;
            if (totalPowerQuery <= 0 && s.powerQuery > 0) {
                totalPowerQuery = s.powerQuery;
                returnPower = true;
            }

            if (totalPowerQuery <= 0) {
                continue;
            }

            long unusedPowerQuery = totalPowerQuery;
            for (Direction face2 : Direction.values()) {
                if (face == face2 && !returnPower) {
                    continue;
                }
                Section s2 = sections.get(face2);
                if (s2.powerQuery <= 0) {
                    continue;
                }
                long watts = Math.min(
                    BigInteger.valueOf(s.internalPower).multiply(BigInteger.valueOf(s2.powerQuery))
                        .divide(BigInteger.valueOf(unusedPowerQuery)).longValue(),
                    s.internalPower
                );
                unusedPowerQuery -= s2.powerQuery;
                IPipe neighbour = pipe.getConnectedPipe(face2);
                long leftover = watts;
                if (
                    neighbour != null && neighbour.getFlow() instanceof PipeFlowPower oFlow
                        && neighbour.isConnected(face2.getOpposite())
                ) {
                    leftover = oFlow.sections.get(face2.getOpposite()).receivePowerInternal(watts);
                } else {
                    IMjReceiver receiver = getReceiver(face2);
                    if (receiver != null && receiver.canReceive()) {
                        leftover = receiver.receivePower(watts, false);
                    }
                }
                long used = watts - leftover;
                s.internalPower -= used;
            }
        }

        // Compute the tiles requesting power that are not power pipes.
        for (Direction face : Direction.values()) {
            if (pipe.getConnectedType(face) != ConnectedType.TILE) {
                continue;
            }
            IMjReceiver recv = getReceiver(face);
            if (recv != null && recv.canReceive()) {
                long requested = recv.getPowerRequested();
                if (requested > 0) {
                    requestPower(face, requested);
                }
            }
        }

        // Sum the amount of power requested on each side.
        Map<Direction, Long> transferQuery = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            if (!pipe.isConnected(face)) {
                continue;
            }
            long query = 0;
            for (Direction face2 : Direction.values()) {
                if (face != face2) {
                    query += sections.get(face2).powerQuery;
                }
            }
            transferQuery.put(face, query);
        }

        // Transfer requested power to neighbouring pipes.
        for (Direction face : Direction.values()) {
            if (disabled || !pipe.isConnected(face)) {
                continue;
            }
            long query = transferQuery.get(face);
            if (query <= 0) {
                continue;
            }
            IPipe oPipe = pipe.getHolder().getNeighbourPipe(face);
            if (oPipe == null || !(oPipe.getFlow() instanceof PipeFlowPower oFlow)) {
                continue;
            }
            oFlow.requestPower(face.getOpposite(), query);
        }
    }

    @Nullable
    private IMjReceiver getReceiver(Direction side) {
        return pipe.getHolder().getCapabilityFromPipe(side, MjCapabilities.RECEIVER);
    }

    private void step() {
        long now = pipe.getHolder().getPipeLevel().getGameTime();
        if (currentWorldTime != now) {
            currentWorldTime = now;
            sections.values().forEach(Section::step);
        }
    }

    private void requestPower(Direction from, long amount) {
        step();

        Section s = sections.get(from);
        if (pipe.getBehaviour() instanceof IPipeTransportPowerHook hook) {
            s.nextPowerQuery += hook.requestPower(from, amount);
        } else {
            s.nextPowerQuery += amount;
        }
        s.nextPowerQuery = Math.min(s.nextPowerQuery, maxPower);
    }

    public long getPowerRequested(@Nullable Direction side) {
        long req = 0;
        for (Direction face : Direction.values()) {
            if (side == null || face != side) {
                req += sections.get(face).powerQuery;
            }
        }
        return req;
    }

    public class Section implements IMjReceiver {
        public final Direction side;

        long powerQuery;
        long nextPowerQuery;
        long internalPower;
        long internalNextPower;

        public Section(Direction side) {
            this.side = side;
        }

        void step() {
            powerQuery = nextPowerQuery;
            nextPowerQuery = 0;

            internalPower += internalNextPower;
            internalNextPower = 0;
        }

        @Override
        public boolean canConnect(@NotNull IMjConnector other) {
            return true;
        }

        @Override
        public long getPowerRequested() {
            return PipeFlowPower.this.getPowerRequested(side);
        }

        long receivePowerInternal(long sent) {
            if (sent > 0) {
                PipeFlowPower.this.step();
                internalNextPower += sent;
                return 0;
            }
            return sent;
        }

        @Override
        public long receivePower(long microJoules, boolean simulate) {
            if (isReceiver) {
                if (!simulate) {
                    return this.receivePowerInternal(microJoules);
                }
                return 0;
            }
            return microJoules;
        }

        @Override
        public boolean canReceive() {
            return isReceiver;
        }
    }
}
