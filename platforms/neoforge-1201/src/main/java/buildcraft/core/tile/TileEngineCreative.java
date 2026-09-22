/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.tile;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.BCCoreRegistries;
import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.tools.IToolWrench;

import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase;
import buildcraft.lib.misc.MathUtil;

/**
 * Ported unchanged in shape from 1.12.2 -- an unlimited, GUI-free power source used for testing, whose output
 * level cycles through {@link #outputs} on a wrench right-click. Mirrors the 26.x class of the same name -- see
 * that one for why {@link #onActivated} is reached from {@link buildcraft.core.block.BlockEngineCreative} directly
 * rather than a vanilla activation hook. The one real difference is the status message: 1.20.1's
 * {@code Player#displayClientMessage(Component, boolean)} still takes the action-bar boolean 1.12.2's
 * {@code sendStatusMessage} did, where 26.x's replacement dropped it in favour of a separate method
 * ({@code sendOverlayMessage}).
 */
public class TileEngineCreative extends TileEngineBase {
    public static final long[] outputs = { 1, 2, 4, 8, 16, 32, 64, 128, 256 };
    public int currentOutputIndex = 0;

    public TileEngineCreative(BlockPos pos, BlockState state) {
        super(BCCoreRegistries.ENGINE_CREATIVE_TYPE.get(), pos, state);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        currentOutputIndex = MathUtil.clamp(nbt.getInt("currentOutputIndex"), 0, outputs.length - 1);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putInt("currentOutputIndex", currentOutputIndex);
    }

    @Override
    protected void engineUpdate() {
        if (isBurning()) {
            power += getCurrentOutput();
            long max = getMaxPower();
            if (power > max) {
                power = getMaxPower();
            }
        } else {
            power = 0;
        }
    }

    @NotNull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(false);
    }

    @Override
    public boolean isBurning() {
        return isRedstonePowered;
    }

    @Override
    public double getPistonSpeed() {
        final double max = 0.08;
        final double min = 0.01;
        double interp = currentOutputIndex / (double) (outputs.length - 1);
        return MathUtil.interp(interp, min, max);
    }

    @Override
    protected EnumPowerStage computePowerStage() {
        return EnumPowerStage.BLACK;
    }

    @Override
    public long getMaxPower() {
        return getCurrentOutput() * 10_000;
    }

    @Override
    public long maxPowerReceived() {
        return 2_000 * MjAPI.MJ;
    }

    @Override
    public long maxPowerExtracted() {
        return 20 * getCurrentOutput();
    }

    @Override
    public float explosionRange() {
        return 0;
    }

    @Override
    public long getCurrentOutput() {
        return outputs[MathUtil.clamp(currentOutputIndex, 0, outputs.length - 1)] * MjAPI.MJ;
    }

    /** Cycles the output level on a wrench right-click. See the class javadoc for why this is reached from the
     * block rather than a vanilla activation hook. */
    public boolean onActivated(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && stack.getItem() instanceof IToolWrench) {
            if (level != null && !level.isClientSide()) {
                currentOutputIndex++;
                currentOutputIndex %= outputs.length;
                player.displayClientMessage(
                    Component.translatable("chat.pipe.power.iron.mode", outputs[currentOutputIndex]), true);
                markDirtyAndSync();
            }
            return true;
        }
        return false;
    }
}
