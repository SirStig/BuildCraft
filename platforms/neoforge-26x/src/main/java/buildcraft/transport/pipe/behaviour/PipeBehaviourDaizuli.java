/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;

import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * The daizuli pipe (a BuildCraft 8-specific material, not present in older BuildCraft versions -- ported strictly
 * from reading {@code PipeBehaviourDaizuli} itself, never assumed): a directional colour filter. It picks one of
 * 16 {@link DyeColor}s (wrench-cycled, like {@link PipeBehaviourLapis}) and one active face (wrench-selected, like
 * {@link PipeBehaviourDirectional}). An item carrying the matching colour (set upstream by a {@link
 * PipeBehaviourLapis}, in 1.12.2's design) is forced out through the active face and nowhere else; anything else
 * is simply barred from leaving that way, free to go anywhere else the network offers.
 *
 * <p>Clicking the pipe's centre, or its own active face, cycles the colour (wrench); clicking any other face
 * re-picks the active face -- both straight from 1.12.2's own {@code onPipeActivate}. {@code canFaceDirection}
 * always returns {@code true}: unlike {@link PipeBehaviourWood}/{@link PipeBehaviourIron}, a daizuli pipe's active
 * face is not tied to a neighbouring inventory at all.
 *
 * <p>1.12.2's per-face, per-colour texture set ({@code getTextureIndex}, 17 texture suffixes: 16 colours plus a
 * "filled" arm) has no equivalent in this port's rendering model -- {@code TilePipeHolder} only pushes the
 * {@code active} face onto the blockstate (which this class gets for free, being a {@link
 * PipeBehaviourDirectional}), not an arbitrary per-instance colour. The pipe renders with its single default
 * texture regardless of colour; a documented rendering scope cut, not a behavioural one.
 */
public class PipeBehaviourDaizuli extends PipeBehaviourDirectional {
    private DyeColor colour = DyeColor.WHITE;

    public PipeBehaviourDaizuli(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourDaizuli(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
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

    @Override
    protected boolean canFaceDirection(@Nullable Direction dir) {
        return true;
    }

    @Override
    public boolean onPipeActivate(Player player, BlockHitResult trace, EnumPipePart part) {
        if (part != EnumPipePart.CENTER && part != currentDir) {
            // Activating the centre of a pipe always falls back to changing the colour, and so does clicking on
            // the current facing side -- 1.12.2's own comment, verbatim.
            return super.onPipeActivate(player, trace, part);
        }
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
    public void sideCheck(PipeEventItem.SideCheck sideCheck) {
        if (colour == sideCheck.colour) {
            sideCheck.disallowAllExcept(currentDir.face);
        } else {
            sideCheck.disallow(currentDir.face);
        }
    }
}
