/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.tile;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.BCCoreRegistries;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase;
import buildcraft.lib.misc.AdvancementUtil;

/**
 * Renamed from {@code TileEngineRedstone_BC8} -- despite the old name, this was never a combustion/fuel engine;
 * it outputs a small constant draw of MJ for as long as it is redstone-powered, at no running cost beyond that
 * redstone signal. See the 26.x class of the same name for the full account of the rename and the owner-tracking
 * design below; this file mirrors it, differing only in NBT shape ({@code CompoundTag} rather than
 * {@code ValueInput}/{@code ValueOutput}).
 */
public class TileEngineWood extends TileEngineBase {
    private static final ResourceLocation ADVANCEMENT = new ResourceLocation("buildcraftcore", "free_power");

    @Nullable
    private UUID owner;
    private boolean givenAdvancement = false;

    public TileEngineWood(BlockPos pos, BlockState state) {
        super(BCCoreRegistries.ENGINE_WOOD_TYPE.get(), pos, state);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        owner = nbt.hasUUID("owner") ? nbt.getUUID("owner") : null;
        givenAdvancement = nbt.getBoolean("givenAdvancement");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (owner != null) {
            nbt.putUUID("owner", owner);
        }
        nbt.putBoolean("givenAdvancement", givenAdvancement);
    }

    @Override
    public void onPlacedBy(LivingEntity placer, ItemStack stack) {
        super.onPlacedBy(placer, stack);
        owner = placer == null ? null : placer.getUUID();
    }

    @NotNull
    @Override
    protected IMjConnector createConnector() {
        return new EngineConnector(true);
    }

    @Override
    public boolean isBurning() {
        return isRedstonePowered;
    }

    @Override
    protected void engineUpdate() {
        super.engineUpdate();
        if (isRedstonePowered) {
            power = getMaxPower();
            if (level != null && level.getGameTime() % 16 == 0) {
                if (getHeatLevel() < 0.8) {
                    heat += 4;
                }
                if (isPumping && !givenAdvancement) {
                    givenAdvancement = owner != null && AdvancementUtil.unlockAdvancement(owner, ADVANCEMENT);
                }
            }
        } else {
            power = 0;
        }
    }

    @Override
    public double getPistonSpeed() {
        return super.getPistonSpeed() / 2;
    }

    @Override
    public void updateHeatLevel() {
        if (heat > MIN_HEAT) {
            heat -= 0.2f;
            if (heat < MIN_HEAT) {
                heat = MIN_HEAT;
            }
        }
    }

    @Override
    protected int getMaxChainLength() {
        return 0;
    }

    @Override
    public long getMaxPower() {
        return MjAPI.MJ;
    }

    @Override
    public long minPowerReceived() {
        return MjAPI.MJ / 10;
    }

    @Override
    public long maxPowerReceived() {
        return 4 * MjAPI.MJ;
    }

    @Override
    public long maxPowerExtracted() {
        return 4 * MjAPI.MJ;
    }

    @Override
    public float explosionRange() {
        return 0;
    }

    @Override
    public long getCurrentOutput() {
        return MjAPI.MJ / 20;
    }
}
