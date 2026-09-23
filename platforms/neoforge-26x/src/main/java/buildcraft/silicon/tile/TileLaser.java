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

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.mj.ILaserTarget;
import buildcraft.api.mj.ILaserTargetBlock;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
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
 * The laser-beam emitter: finds nearby {@link ILaserTarget}s (a laser table's own tile, via
 * {@link ILaserTargetBlock}) within a 6-block cone in front of whichever way this block faces, and feeds them MJ
 * drawn from its own input battery. Ported from 1.12.2's {@code TileLaser} -- this is what actually lets the four
 * laser tables (already ported, see {@code TileLaserTableBase}) receive power at all.
 *
 * <p><b>Ticking.</b> {@code ITickable#update()}'s two branches split the same way every other tile in this port's
 * already split: the server branch below is {@link #serverTick()}, driven by {@link BlockLaser}'s
 * {@code getTicker} (only ever installed server-side); the client branch (random beam-endpoint jitter, purely
 * cosmetic) moved into {@link buildcraft.silicon.client.render.RenderLaser#extractRenderState}, since nothing
 * ticks a block entity client-side any more -- see {@link #laserPos}'s own javadoc.
 *
 * <p><b>Retargeting, simplified.</b> 1.12.2 rescanned its full targeting cone ({@link #findPossibleTargets()}) only
 * when {@code buildcraft.lib.block.LocalBlockUpdateNotifier} told it a nearby block had changed, and otherwise just
 * reselected among the already-known list every {@code serverTargetMoveInterval} (10-20 ticks). That notifier is a
 * world-wide "every block change, notify every registered listener" broadcaster with no consumer anywhere else in
 * this port yet ({@link buildcraft.lib.block.ILocalBlockUpdateSubscriber} exists as an interface stub but nothing
 * implements or drives it) -- standing up that whole singleton-per-level system for this one caller was judged not
 * worth it this round. Instead, {@link #retargetInterval} drives a full {@link #findPossibleTargets()} +
 * {@link #randomlyChooseTargetPos()} pass together, on the same cadence 1.12.2 used for reselection alone (plus
 * immediately whenever the current target stops needing power, exactly as before) -- a real cone rescan every
 * 10-20 ticks rather than only after a notified change, which costs a little more CPU but needs no new
 * infrastructure and is never a hot path (it is not run every tick).
 *
 * <p><b>Network sync, simplified.</b> 1.12.2 pushed a small id-tagged {@code NET_RENDER_DATA} payload (battery,
 * optional target pos, one average-power long) unconditionally every server tick. {@link TileBC} has no such
 * payload system -- {@link #markDirtyAndSync()} syncs this tile's entire saved NBT state instead (see that
 * method's own javadoc) -- so this tile deliberately keeps its saved state tiny (battery, target pos, one
 * precomputed average long; see {@link #saveAdditional}) rather than persisting {@link #avgPower}'s internal
 * sliding window, and still calls {@link #markDirtyAndSync()} every tick a target exists, matching 1.12.2's own
 * "always sync while relevant" behaviour without the bandwidth cost a synced {@link AverageLong} array would add
 * (this mirrors {@code TileLaserTableBase}'s own choice not to persist its {@code avgPower} either).
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

    /** The beam's jittering "business end" inside the target table, purely a render-side flourish -- recomputed
     * by {@code RenderLaser} on a client-side {@link SafeTimeTracker} of its own, not ticked or persisted here.
     * 1.12.2 computed this in {@code TileLaser#update()}'s {@code world.isRemote} branch; see this class's own
     * javadoc for why that branch moved to the renderer instead. */
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

    /** Driven by {@link BlockLaser}'s {@code getTicker}; was {@code ITickable#update()}'s server branch. See this
     * class's own javadoc for why retargeting and network sync are both simplified from 1.12.2. */
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

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        battery.setStored(input.getLongOr("battery", 0));
        targetPos = input.read("target_pos", BlockPos.CODEC).orElse(null);
        averageClient = input.getLongOr("average_power", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("battery", battery.getStored());
        output.storeNullable("target_pos", BlockPos.CODEC, targetPos);
        output.putLong("average_power", averageClient);
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("target = " + targetPos);
        left.add("average = " + LocaleUtil.localizeMjFlow(averageClient));
    }
}
