/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.item;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.transport.plug.PluggablePulsar;

import buildcraft.BCTransportRegistries;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.item.ItemPluggablePulsar}: unlike every other simple pluggable item
 * in this batch (see {@code ItemPluggableSimple}), this one cannot be a plain factory lambda -- placement is
 * gated on the pipe's own behaviour actually being an {@link IMjRedstoneReceiver}, exactly as the original
 * checked before ever constructing a {@link PluggablePulsar}. The original was itself marked {@code @Deprecated}
 * (its own comment gives no reason); ported as-is regardless, since it is still the real placement path for this
 * accessory and nothing in this batch replaces it.
 */
public class ItemPluggablePulsar extends Item implements IItemPluggable {
    public ItemPluggablePulsar(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public PipePluggable onPlace(
        @NotNull ItemStack stack, IPipeHolder holder, Direction side, Player player, InteractionHand hand
    ) {
        IPipe pipe = holder.getPipe();
        if (pipe == null) {
            return null;
        }
        PipeBehaviour behaviour = pipe.getBehaviour();
        if (behaviour instanceof IMjRedstoneReceiver) {
            return new PluggablePulsar(BCTransportRegistries.PLUGGABLE_DEF_PULSAR, holder, side);
        }
        return null;
    }
}
