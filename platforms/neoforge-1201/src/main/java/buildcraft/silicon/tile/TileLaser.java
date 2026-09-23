/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.tile;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.mj.ILaserTarget;
import buildcraft.api.mj.ILaserTargetBlock;
import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.mj.MjEffects;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.VolumeUtil;
import buildcraft.lib.misc.data.AverageLong;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC;

import buildcraft.BCSiliconRegistries;
import buildcraft.silicon.block.BlockLaser;

/**
 * The laser-beam emitter. Mirrors the 26.x class of the same name -- see that one's own javadoc for the full
 * machine logic, why retargeting no longer depends on the unported {@code LocalBlockUpdateNotifier}, and why
 * network sync deliberately keeps this tile's saved state tiny. This file differs only in the usual 1.20.1
 * places: NBT is {@code CompoundTag} ({@code load}/{@code saveAdditional}) and the MJ receiver capability is
 * answered by {@link #getCapability} directly, matching {@code TileMiner}'s (1.20.1) own precedent, rather than
 * registered against the block entity type.
 */
public class TileLaser extends TileBC implements IDebuggable {
    public static final int TARGETING_RANGE = 6;

    private final SafeTimeTracker retargetInterval = new SafeTimeTracker(10, 20);

    private final List<BlockPos> targetPositions = new ArrayList<>();
    @Nullable
    private BlockPos targetPos;

    private final AverageLong avgPower = new AverageLong(100);
    private long averageClient;

    private final MjBattery battery = new MjBattery(1024 * MjAPI.MJ);
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);
    private final LazyOptional<IMjReceiver> receiverCap = LazyOptional.of(() -> mjReceiver);
    private final LazyOptional<IMjReadable> readableCap = LazyOptional.of(() -> mjReceiver);

    /** See the 26.x copy of this class's own javadoc: purely a render-side flourish, recomputed by
     * {@code RenderLaser}, not ticked or persisted here. */
    @Nullable
    public Vec3 laserPos;

    public TileLaser(BlockPos pos, BlockState state) {
        super(BCSiliconRegistries.LASER_TYPE.get(), pos, state);
    }

    private void findPossibleTargets() {
        targetPositions.clear();
        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof BlockLaser)) {
            return;
        }
        Direction face = state.getValue(BuildCraftProperties.BLOCK_FACING_6);

        VolumeUtil.iterateCone(level, worldPosition, face, TARGETING_RANGE, true, (w, s, p, visible) -> {
            if (!visible) {
                return;
            }
            BlockState stateAt = level.getBlockState(p);
            if (stateAt.getBlock() instanceof ILaserTargetBlock && level.getBlockEntity(p) instanceof ILaserTarget) {
                targetPositions.add(p.immutable());
            }
        });
    }

    private void randomlyChooseTargetPos() {
        List<BlockPos> targetsNeedingPower = new ArrayList<>();
        for (BlockPos position : targetPositions) {
            if (isPowerNeededAt(position)) {
                targetsNeedingPower.add(position);
            }
        }
        if (targetsNeedingPower.isEmpty()) {
            targetPos = null;
            return;
        }
        targetPos = targetsNeedingPower.get(level.getRandom().nextInt(targetsNeedingPower.size()));
    }

    private boolean isPowerNeededAt(@Nullable BlockPos position) {
        if (position != null && level.getBlockEntity(position) instanceof ILaserTarget target) {
            return target.getRequiredLaserPower() > 0;
        }
        return false;
    }

    @Nullable
    private ILaserTarget getTarget() {
        if (targetPos != null && level.getBlockEntity(targetPos) instanceof ILaserTarget target) {
            return target;
        }
        return null;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return targetPos;
    }

    public long getAverageClient() {
        return averageClient;
    }

    public long getMaxPowerPerTick() {
        return 4 * MjAPI.MJ;
    }

    /** Driven by {@link BlockLaser}'s {@code getTicker}. See the 26.x copy of this class's own javadoc. */
    public void serverTick() {
        MjEffects.tick(level, worldPosition, battery);
        avgPower.tick();

        if (retargetInterval.markTimeIfDelay(level) || !isPowerNeededAt(targetPos)) {
            findPossibleTargets();
            randomlyChooseTargetPos();
        }

        ILaserTarget target = getTarget();
        if (target != null) {
            long max = getMaxPowerPerTick();
            max *= battery.getStored() + max;
            max /= battery.getCapacity() / 2;
            max = Math.min(Math.min(max, getMaxPowerPerTick()), target.getRequiredLaserPower());
            long power = battery.extractPower(0, max);
            long excess = target.receiveLaserPower(power);
            if (excess > 0) {
                battery.addPowerChecking(excess, false);
            }
            avgPower.push(power - excess);
        } else {
            avgPower.clear();
        }

        averageClient = (long) avgPower.getAverage();
        markDirtyAndSync();
    }

    /** Widens this tile's render bounding box to the full targeting cone, matching {@code TileMiner}'s (1.20.1)
     * own precedent for the same reason: the beam reaches well outside this block's default 1x1x1 box. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(TARGETING_RANGE + 1);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        battery.setStored(nbt.getLong("battery"));
        targetPos = nbt.contains("target_pos") ? NbtUtils.readBlockPos(nbt.getCompound("target_pos")) : null;
        averageClient = nbt.getLong("average_power");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putLong("battery", battery.getStored());
        if (targetPos != null) {
            nbt.put("target_pos", NbtUtils.writeBlockPos(targetPos));
        }
        nbt.putLong("average_power", averageClient);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("target = " + targetPos);
        left.add("average = " + LocaleUtil.localizeMjFlow(averageClient));
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == MjCapabilities.RECEIVER) {
            return receiverCap.cast();
        }
        if (cap == MjCapabilities.READABLE) {
            return readableCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        receiverCap.invalidate();
        readableCap.invalidate();
    }
}
