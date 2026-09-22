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
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.BCCoreRegistries;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjAPI;

import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.engine.TileEngineBase;
import buildcraft.lib.misc.AdvancementUtil;

/**
 * Renamed from {@code TileEngineRedstone_BC8} -- despite the old name, this was never a combustion/fuel engine;
 * it outputs a small constant draw of MJ for as long as it is redstone-powered, at no running cost beyond that
 * redstone signal. It was registered in 1.12.2 under the {@code tile.engine.wood} tag ({@code EnumEngineType.WOOD}),
 * which is what the new name follows instead of the misleading class name -- see {@code BCCoreBlocks.registerEngine}
 * in the 1.12.2 source for the WOOD/{@code TileEngineRedstone_BC8} pairing this is based on.
 *
 * <p>{@code getOwner().getId()} (used to grant the "free power" advancement) was part of the much larger
 * {@code TileBC_Neptune}, which the current, slimmer {@link buildcraft.lib.tile.TileBC} doesn't carry forward.
 * {@link #owner} is a small UUID field local to this tile instead -- wood-engine-specific, not promoted to the
 * shared base, since no other engine type needs it -- set from {@code setPlacedBy}'s {@code placer} the same way
 * {@code TileMarkerVolume#onPlacedBy}/{@code BlockMarkerVolume#setPlacedBy} already set up per-tile placement
 * state, and persisted so the advancement still only fires once even across a reload. The advancement JSON itself
 * ({@code buildcraftcore:free_power}) is not ported -- {@link AdvancementUtil#unlockAdvancement} already tolerates
 * an unregistered advancement id as a harmless one-time warning (see its own javadoc, and the identical precedent
 * already established for {@code ItemWrench}'s {@code buildcraftcore:wrenched}).
 */
public class TileEngineWood extends TileEngineBase {
    private static final Identifier ADVANCEMENT = Identifier.fromNamespaceAndPath("buildcraftcore", "free_power");

    @Nullable
    private UUID owner;
    private boolean givenAdvancement = false;

    public TileEngineWood(BlockPos pos, BlockState state) {
        super(BCCoreRegistries.ENGINE_WOOD_TYPE.get(), pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        givenAdvancement = input.getBooleanOr("givenAdvancement", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
        }
        output.putBoolean("givenAdvancement", givenAdvancement);
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
