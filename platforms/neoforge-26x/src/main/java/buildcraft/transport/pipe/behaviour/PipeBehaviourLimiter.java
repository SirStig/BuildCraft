/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IFlowPowerLike;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.PowerTransferInfo;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventPower;

import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.MathUtil;

/**
 * The power limiter -- the iron/diamond/diamond_wood kinesis pipes' own behaviour, an N-step (0..
 * {@link #MAX_SHIFT}) wrench-cycled throttle on a power pipe's own transfer rate, halving it (a right bit-shift)
 * on every step until the final step disables transfer entirely. A close port of 1.12.2's own
 * {@code PipeBehaviourLimiter}, with two changes:
 *
 * <ul>
 * <li><b>No Forge-energy (RF) branch.</b> 1.12.2 supported both an MJ ({@link PipeEventPower.Configure}) and an
 * RF ({@code PipeEventRedstoneFlux.Configure}) power pipe sharing this one behaviour, picking the chat message
 * and limit maths at runtime via {@code pipe.getFlow() instanceof PipeFlowRedstoneFlux}. This port never
 * registers an RF power pipe at all -- {@code PipeApi.flowRf} is never assigned in {@code BCTransportRegistries}
 * (confirmed by reading that class), so every power pipe on this port carries {@link IFlowPowerLike} through the
 * MJ path only, and the RF {@code Configure} handler/branch is dropped as genuinely unreachable rather than kept
 * as dead code.</li>
 * <li><b>{@code onActionActivate}/{@code ActionPowerLimit} stay dropped.</b> Gates/statements are out of scope
 * for this whole module (matching {@code PipeBehaviourDirectional}'s own {@code addActions} drop, for the
 * identical reason) -- a limiter placed by a gate action is not reachable on this port at all, so only the
 * wrench-cycle path survives.</li>
 * </ul>
 *
 * <p><b>No {@code writePayload}/{@code readPayload}.</b> Matching {@code PipeBehaviourDirectional}'s own already-
 * established pattern (see that class's own javadoc): {@link #limitShift} reaches the client through whole-tile
 * NBT resync (this class's own {@link #writeToNbt}/NBT constructor pair) rather than a dedicated payload message,
 * triggered by {@link #requestReconfigure}'s {@code scheduleNetworkUpdate} call.
 */
public class PipeBehaviourLimiter extends PipeBehaviour {

    public static final int MAX_SHIFT = 6;

    private int limitShift = 0;

    public PipeBehaviourLimiter(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourLimiter(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        limitShift = MathUtil.clamp(nbt.getIntOr("limitShift", 0), 0, MAX_SHIFT);
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.putInt("limitShift", limitShift);
        return nbt;
    }

    @PipeEventHandler
    public void configurePower(PipeEventPower.Configure event) {
        if (limitShift == MAX_SHIFT) {
            event.disableTransfer();
        } else {
            event.setMaxPower(event.getMaxPower() >> limitShift);
        }
    }

    @Override
    public boolean onPipeActivate(Player player, BlockHitResult trace, EnumPipePart part) {
        if (EntityUtil.getWrenchHand(player) == null) {
            return false;
        }

        if (!player.level().isClientSide()) {
            EntityUtil.activateWrench(player, trace);
            limitShift++;
            if (limitShift > MAX_SHIFT) {
                limitShift = 0;
            }

            final int limit;
            if (limitShift == MAX_SHIFT) {
                limit = 0;
            } else {
                PowerTransferInfo transferInfo = PipeApi.getPowerTransferInfo(pipe.getDefinition());
                limit = (int) ((transferInfo.transferPerTick >> limitShift) / MjAPI.MJ);
            }
            player.sendOverlayMessage(Component.translatable("chat.pipe.power.iron.mode", limit));

            requestReconfigure();
        }
        return true;
    }

    private void requestReconfigure() {
        if (pipe.getFlow() instanceof IFlowPowerLike powerLike) {
            powerLike.reconfigure();
            pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
    }

    @Override
    public int getTextureIndex(Direction face) {
        return MAX_SHIFT - limitShift;
    }
}
