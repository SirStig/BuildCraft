/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;

import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * The lapis pipe's real 1.12.2 mechanic -- see the 26.x copy of this file for the full account (confirmed by
 * reading {@code PipeBehaviourLapis} directly): it paints every item reaching its centre with a wrench-cycled
 * {@link DyeColor}, for a colour-sorting pipe further down the network to filter on. No colour-filtering pipe or
 * statement system exists in this port yet, so wrench cycling is the only way to set it.
 */
public class PipeBehaviourLapis extends PipeBehaviour {
    private DyeColor colour = DyeColor.WHITE;

    public PipeBehaviourLapis(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourLapis(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        DyeColor read = NBTUtilBC.readEnum(nbt.get("colour"), DyeColor.class);
        colour = read == null ? DyeColor.WHITE : read;
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.put("colour", NBTUtilBC.writeEnum(colour));
        return nbt;
    }

    public DyeColor getColour() {
        return colour;
    }

    @Override
    public boolean onPipeActivate(Player player, BlockHitResult trace, EnumPipePart part) {
        if (player.level().isClientSide()) {
            return EntityUtil.getWrenchHand(player) != null;
        }
        if (EntityUtil.getWrenchHand(player) != null) {
            EntityUtil.activateWrench(player, trace);
            int n = colour.getId() + (player.isShiftKeyDown() ? 15 : 1);
            setColour(DyeColor.byId(n & 15));
            return true;
        }
        return false;
    }

    private void setColour(DyeColor newColour) {
        if (colour != newColour) {
            colour = newColour;
            pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
    }

    @PipeEventHandler
    public void onReachCenter(PipeEventItem.ReachCenter reachCenter) {
        reachCenter.colour = colour;
    }
}
