/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjEffects;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC;

import buildcraft.BCFactoryRegistries;

/**
 * Base class for a machine that digs a vertical shaft, one block at a time, powered by MJ, laying
 * {@code buildcraft.factory.block.BlockTube} behind it to protect the shaft while it works. {@code TileMiningWell}
 * is the only concrete subclass so far.
 *
 * <p>{@code TileBC_Neptune}/{@code ITickable}/{@code update()} become {@link TileBC}/{@link #serverTick()}, driven
 * by the owning block's {@code getTicker}, exactly like {@code TileChute}/{@code TileEngineWood}. 1.12.2's
 * {@code update()} opened with a {@code world.isRemote} branch that smoothed a render-only {@code currentLength}
 * toward {@code wantedLength} for a fast renderer; that branch, {@code currentLength}, {@code lastLength},
 * {@code getLength}, {@code getPercentFilledForRender}, {@code hasFastRenderer} and the two render-distance
 * overrides are all dropped outright -- there is no renderer in this port yet to consume any of them, the same
 * "no renderer to serve it" reasoning {@code TileEngineBase} already used for its own dropped progress animation.
 * {@link #wantedLength} survives as a plain server-side field: {@link #updateLength()} still needs it to notice
 * when the dig target actually moved, it just no longer gets pushed to a client.
 *
 * <p>{@code onLoad}'s {@code offset = world.rand.nextInt(10)} (a per-tile stagger so not every miner's LED-status
 * network tick landed on the same world tick) has nothing left to stagger once the id-tagged {@code NET_LED_STATUS}
 * payload is gone, matching {@code TileEngineWood}'s own dropped {@code IDS}/{@code NET_SIGNALS_ON}/
 * {@code NET_SIGNALS_OFF}. {@code IdAllocator}/{@code TileBC_Neptune.IDS}/{@code NET_LED_STATUS}/
 * {@code NET_WANTED_Y} are dropped with it, for the same reason.
 *
 * <p>{@code migrateOldNBT} is not ported -- there are no old save files for a fresh port to migrate from.
 *
 * <p>{@code readFromNBT}/{@code writeToNBT} become {@link #loadAdditional}/{@link #saveAdditional} over
 * {@link ValueInput}/{@link ValueOutput}.
 *
 * <p>1.12.2's constructor built an {@code IMjReceiver} through an abstract {@code createMjReceiver()} factory
 * method purely so a subclass could supply a different receiver implementation; with exactly one concrete
 * subclass in this port so far and nothing about {@link MjBatteryReceiver} that would ever need to differ, the
 * indirection is dropped in favour of wiring {@link #mjReceiver} to {@link #battery} directly here, matching how
 * {@code TileChute} does it.
 *
 * <p>{@code TilesAPI.CAP_HAS_WORK}'s 1.12.2 capability instance was {@code () -> !isComplete}, reading the raw
 * {@code isComplete} <em>field</em> -- which, on the server, was never actually written anywhere except by its own
 * declaration default ({@code false}); only {@code readPayload} (a client-only network handler) ever assigned it.
 * The field was therefore permanently {@code false} server-side, making that capability lambda permanently
 * {@code true} regardless of whether the miner had actually finished digging -- almost certainly an artifact of
 * the field/method naming collision with {@link #isComplete()} (the method), which computed the real,
 * server-authoritative answer (I{@code currentPos == null}) but was never the thing the capability actually read.
 * Since this port drops the stale client-mirror field entirely (see above), {@code BCFactoryRegistries} wires
 * {@code TilesAPI.HAS_WORK} to {@code !tile.isComplete()} -- the method, so this actually reflects whether the
 * miner has more digging to do, correcting what reads like a dead-field bug rather than reproducing it.
 *
 * <p>{@code World#getTotalWorldTime()} is {@code Level#getGameTime()}; see {@link buildcraft.api.core.SafeTimeTracker}'s
 * own javadoc, already updated for this rename.
 */
public abstract class TileMiner extends TileBC implements IDebuggable {

    /** {@code BCCoreConfig.miningMaxDepth}'s unconfigured default (512): how far below itself a miner is willing
     * to dig, and how far it retracts its tube shaft. No config system is ported yet (see PORTING.md), so this
     * is the fixed default rather than a wired setting. */
    protected static final int MINING_MAX_DEPTH = 512;

    protected int progress = 0;
    @Nullable
    protected BlockPos currentPos = null;

    /** How many blocks tall the tube shaft below this tile currently is. Purely a "did the target change"
     * guard for {@link #updateLength()} now -- see the class javadoc for why this no longer also feeds a
     * client-side render length. */
    private int wantedLength = 0;

    protected final MjBattery battery = new MjBattery(getBatteryCapacity());
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);

    protected TileMiner(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract void mine();

    /** Driven by the owning block's {@code getTicker}; was {@code ITickable.update()}. */
    public void serverTick() {
        MjEffects.tick(level, worldPosition, battery);
        mine();
    }

    /** {@link BlockEntity#preRemoveSideEffects} is a genuinely new hook -- confirmed via {@code javap} and a
     * decompile of the real {@code LevelChunk#setBlockState}: it did not exist at all on 1.20.1 (see that
     * platform's copy of this class for how it reaches the same place instead), and is now the modern seat for
     * exactly what 1.12.2's {@code TileBC_Neptune#onRemove()} (driven by {@code BlockBCTile_Neptune#breakBlock})
     * used to do -- called on the tile itself, while it is still valid, immediately before the block entity is
     * actually removed. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        onMinerRemoved();
    }

    /** Clears whatever tube shaft is still standing below this tile, so removing (or replacing) the miner does
     * not leave an orphaned column behind. Was {@code TileMiner#onRemove()}; see {@link #preRemoveSideEffects}
     * for why it is reached differently here. */
    public void onMinerRemoved() {
        clearTubeShaft();
    }

    /** Redraws the tube shaft below this tile to match {@link #getTargetPos()}. Mirrors 1.12.2's own approach --
     * always clear the whole column first, then redraw down to the new target -- rather than a more surgical
     * diff; this only runs when the dig column's target position actually changes, so it is not a hot path. */
    protected void updateLength() {
        BlockPos target = getTargetPos();
        int newY = target != null ? target.getY() : worldPosition.getY();
        int newLength = worldPosition.getY() - newY;
        if (newLength != wantedLength) {
            clearTubeShaft();
            Block tube = BCFactoryRegistries.TUBE.get();
            for (int y = worldPosition.getY() - 1; y > newY; y--) {
                BlockPos shaftPos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
                level.setBlock(shaftPos, tube.defaultBlockState(), Block.UPDATE_ALL);
            }
            wantedLength = newLength;
        }
    }

    /** Clears a contiguous run of tube blocks starting immediately below this tile, stopping at the first block
     * that is not a tube (or at {@link #MINING_MAX_DEPTH}). Shared by {@link #onMinerRemoved()} and the first
     * phase of {@link #updateLength()}. */
    private void clearTubeShaft() {
        Block tube = BCFactoryRegistries.TUBE.get();
        for (int y = worldPosition.getY() - 1; y > worldPosition.getY() - MINING_MAX_DEPTH; y--) {
            BlockPos shaftPos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
            if (level.getBlockState(shaftPos).is(tube)) {
                level.removeBlock(shaftPos, false);
            } else {
                break;
            }
        }
    }

    protected BlockPos getTargetPos() {
        return currentPos;
    }

    public boolean isComplete() {
        return currentPos == null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        currentPos = input.read("currentPos", BlockPos.CODEC).orElse(null);
        wantedLength = input.getIntOr("wantedLength", 0);
        progress = input.getIntOr("progress", 0);
        battery.setStored(input.getLongOr("battery", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("currentPos", BlockPos.CODEC, currentPos);
        output.putInt("wantedLength", wantedLength);
        output.putInt("progress", progress);
        output.putLong("battery", battery.getStored());
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("current = " + currentPos);
        left.add("wantedLength = " + wantedLength);
        left.add("isComplete = " + isComplete());
        left.add("progress = " + LocaleUtil.localizeMj(progress));
    }

    protected long getBatteryCapacity() {
        return 500 * MjAPI.MJ;
    }
}
