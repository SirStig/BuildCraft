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
 * The power limiter -- see the 26.x copy of this class for the full account of the design (no RF branch, since
 * this port never registers an RF power pipe either; no {@code onActionActivate}/{@code ActionPowerLimit}, since
 * gates/statements are out of scope). {@code writePayload}/{@code readPayload} are likewise not ported, matching
 * {@code PipeBehaviourDirectional}'s own already-established "whole-tile NBT resync instead" pattern on this
 * target too. {@link #onPipeActivate} uses {@code Player#displayClientMessage(Component, boolean)}, this
 * target's own {@code sendStatusMessage} rename (see {@code TileEngineCreative}'s own javadoc for the identical
 * finding).
 */
public class PipeBehaviourLimiter extends PipeBehaviour {

    public static final int MAX_SHIFT = 6;

    private int limitShift = 0;

    public PipeBehaviourLimiter(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourLimiter(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        limitShift = MathUtil.clamp(nbt.getInt("limitShift"), 0, MAX_SHIFT);
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
            player.displayClientMessage(Component.translatable("chat.pipe.power.iron.mode", limit), true);

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
