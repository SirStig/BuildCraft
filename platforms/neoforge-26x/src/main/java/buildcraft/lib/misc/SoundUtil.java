/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;

/** {@code IBlockState#getSoundType(world, pos, entity)} lost its three context parameters -- {@code
 * BlockState#getSoundType()} takes none now. {@code EnumActionResult} is {@link InteractionResult}, a sealed
 * interface on this target rather than the plain enum 1.20.1 still has: a {@code PASS} check is {@code instanceof
 * InteractionResult.Pass}, and a {@code SUCCESS} check is {@code instanceof InteractionResult.Success} (covering
 * both {@code SUCCESS} and the client/server-split {@code SUCCESS_SERVER}). Per-fluid bucket sounds move from
 * {@code Fluid#getEmptySound}/{@code getFillSound} to {@code Fluid#getFluidType()#getSound(FluidStack,
 * SoundAction)}, Forge's generic fluid-property system that both targets already share. */
public class SoundUtil {
    public static void playBlockPlace(Level world, BlockPos pos) {
        playBlockPlace(world, pos, world.getBlockState(pos));
    }

    public static void playBlockPlace(Level world, BlockPos pos, BlockState state) {
        SoundType soundType = state.getSoundType();
        float volume = (soundType.getVolume() + 1.0F) / 2.0F;
        float pitch = soundType.getPitch() * 0.8F;
        world.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, volume, pitch);
    }

    public static void playBlockBreak(Level world, BlockPos pos) {
        playBlockBreak(world, pos, world.getBlockState(pos));
    }

    public static void playBlockBreak(Level world, BlockPos pos, BlockState state) {
        SoundType soundType = state.getSoundType();
        float volume = (soundType.getVolume() + 1.0F) / 2.0F;
        float pitch = soundType.getPitch() * 0.8F;
        world.playSound(null, pos, soundType.getBreakSound(), SoundSource.BLOCKS, volume, pitch);
    }

    public static void playLeverSwitch(Level world, BlockPos pos, boolean isNowOn) {
        float pitch = isNowOn ? 0.6f : 0.5f;
        SoundEvent soundEvent = SoundEvents.LEVER_CLICK;
        world.playSound(null, pos, soundEvent, SoundSource.BLOCKS, 0.2f, pitch);
    }

    public static void playChangeColour(Level world, BlockPos pos, @Nullable DyeColor colour) {
        SoundType soundType = SoundType.SLIME_BLOCK;
        final SoundEvent soundEvent;
        if (colour == null) {
            soundEvent = SoundEvents.BUCKET_EMPTY;
        } else {
            // FIXME: is this a good sound? Idk tbh.
            // TODO: Look into configuring this kind of stuff.
            soundEvent = SoundEvents.SLIME_SQUISH;
        }
        float volume = (soundType.getVolume() + 1.0F) / 2.0F;
        float pitch = soundType.getPitch() * 0.8F;
        world.playSound(null, pos, soundEvent, SoundSource.BLOCKS, volume, pitch);
    }

    public static void playSlideSound(Level world, BlockPos pos) {
        playSlideSound(world, pos, world.getBlockState(pos));
    }

    public static void playSlideSound(Level world, BlockPos pos, InteractionResult result) {
        playSlideSound(world, pos, world.getBlockState(pos), result);
    }

    public static void playSlideSound(Level world, BlockPos pos, BlockState state) {
        playSlideSound(world, pos, state, InteractionResult.SUCCESS);
    }

    public static void playSlideSound(Level world, BlockPos pos, BlockState state, InteractionResult result) {
        if (result instanceof InteractionResult.Pass) return;
        SoundType soundType = state.getSoundType();
        SoundEvent event;
        if (result instanceof InteractionResult.Success) {
            event = SoundEvents.PISTON_CONTRACT;
        } else {
            event = SoundEvents.PISTON_EXTEND;
        }
        float volume = (soundType.getVolume() + 1.0F) / 2.0F;
        float pitch = soundType.getPitch() * 0.8F;
        world.playSound(null, pos, event, SoundSource.BLOCKS, volume, pitch);
    }

    public static void playBucketEmpty(Level world, BlockPos pos, FluidStack moved) {
        SoundEvent sound = moved.getFluid().getFluidType().getSound(moved, SoundActions.BUCKET_EMPTY);
        world.playSound(null, pos, sound, SoundSource.PLAYERS, 1, 1);
    }

    public static void playBucketFill(Level world, BlockPos pos, FluidStack moved) {
        SoundEvent sound = moved.getFluid().getFluidType().getSound(moved, SoundActions.BUCKET_FILL);
        world.playSound(null, pos, sound, SoundSource.PLAYERS, 1, 1);
    }
}
