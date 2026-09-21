/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.tile;

import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import org.jetbrains.annotations.NotNull;

import buildcraft.BCCoreRegistries;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.ITileAreaProvider;
import buildcraft.api.tiles.TilesAPI;

import buildcraft.lib.marker.MarkerSubCache;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.tile.TileMarker;

import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.VolumeConnection;

/** One corner marker of a volume box.
 *
 * <p>Mirrors the 26.x class of the same name -- see that one for the full account of what changed from 1.12.2
 * ({@code IDS}/{@code NET_SIGNALS_ON}/{@code NET_SIGNALS_OFF} dropped outright, {@code getRenderBoundingBox}/
 * {@code getMaxRenderDistanceSquared} gone with nothing to replace them, and {@link #refreshActiveState()}
 * replacing {@code getActualState} -- all identical reasoning here). Two things differ on this target:
 *
 * <ul>
 * <li>NBT is still {@code CompoundTag}, so the hooks are {@code load}/{@code saveAdditional} rather than
 *     {@code loadAdditional}/{@code saveAdditional} over {@code ValueInput}/{@code ValueOutput}.</li>
 * <li>{@link ITileAreaProvider} is exposed the 1.20.1 way -- through this tile's own {@code getCapability},
 *     returning a {@link LazyOptional}, matching {@code TilePowerConsumerTester}'s precedent for
 *     {@code IMjReceiver}/{@code IMjConnector} -- rather than 26.x's per-block-entity-type registration.</li>
 * </ul> */
public class TileMarkerVolume extends TileMarker<VolumeConnection> implements ITileAreaProvider {

    private final LazyOptional<ITileAreaProvider> areaProviderCap = LazyOptional.of(() -> this);

    private boolean showSignals = false;

    public TileMarkerVolume(BlockPos pos, BlockState state) {
        super(BCCoreRegistries.MARKER_VOLUME_TYPE.get(), pos, state);
    }

    public boolean isShowingSignals() {
        return showSignals;
    }

    @Override
    public VolumeCache getCache() {
        return VolumeCache.INSTANCE;
    }

    @Override
    public boolean isActiveForRender() {
        return showSignals || getCurrentConnection() != null;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putBoolean("showSignals", showSignals);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        showSignals = nbt.getBoolean("showSignals");
    }

    public void switchSignals() {
        if (level != null && !level.isClientSide()) {
            showSignals = !showSignals;
            markDirtyAndSync();
            refreshActiveState();
        }
    }

    /** Pushes {@link BuildCraftProperties#ACTIVE} to the real blockstate if it no longer matches
     * {@link #isActiveForRender()}. See the class javadoc for why this replaces {@code getActualState}, and
     * the 26.x copy of this class for the one known gap it leaves. */
    private void refreshActiveState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        boolean active = isActiveForRender();
        if (state.getValue(BuildCraftProperties.ACTIVE) != active) {
            level.setBlock(getBlockPos(), state.setValue(BuildCraftProperties.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    public void onManualConnectionAttempt(Player player) {
        MarkerSubCache<VolumeConnection> cache = this.getLocalCache();
        for (BlockPos other : cache.getValidConnections(getBlockPos())) {
            cache.tryConnect(getBlockPos(), other);
        }
        VolumeConnection c = getCurrentConnection();
        if (c != null) {
            for (BlockPos corner : PositionUtil.getCorners(c.getBox().min(), c.getBox().max())) {
                if (!c.getMarkerPositions().contains(corner) && cache.hasLoadedOrUnloadedMarker(corner)) {
                    c.addMarker(corner);
                }
            }
        }
        refreshActiveState();
    }

    /** Was {@code TileEntity#onPlacedBy} in 1.12.2; {@code BlockEntity} has no such hook any more, so
     * {@code BlockMarkerVolume} calls this explicitly from its own {@code setPlacedBy} override instead. */
    public void onPlacedBy(LivingEntity placer, ItemStack stack) {
        // Check if we are the corner of an existing box
        MarkerSubCache<VolumeConnection> cache = this.getLocalCache();
        for (BlockPos other : cache.getValidConnections(getBlockPos())) {
            VolumeConnection c = cache.getConnection(other);
            if (c != null && c.getBox().isCorner(getBlockPos())) {
                if (c.addMarker(getBlockPos())) {
                    // In theory we can't be the corner for multiple boxes
                    break;
                }
            }
        }
        refreshActiveState();
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        super.getDebugInfo(left, right, side);
        left.add("Min = " + min());
        left.add("Max = " + max());
        left.add("Signals = " + showSignals);
    }

    // ITileAreaProvider

    @Override
    public BlockPos min() {
        VolumeConnection connection = getCurrentConnection();
        return connection == null ? getBlockPos() : connection.getBox().min();
    }

    @Override
    public BlockPos max() {
        VolumeConnection connection = getCurrentConnection();
        return connection == null ? getBlockPos() : connection.getBox().max();
    }

    @Override
    public void removeFromWorld() {
        if (level == null || level.isClientSide()) {
            return;
        }
        VolumeConnection connection = getCurrentConnection();
        if (connection != null) {
            // Copy the list over because the iterator doesn't like it if you change the connection while using it
            List<BlockPos> allPositions = ImmutableList.copyOf(connection.getMarkerPositions());
            for (BlockPos p : allPositions) {
                level.destroyBlock(p, true, null, Block.UPDATE_LIMIT);
            }
        }
    }

    @Override
    public boolean isValidFromLocation(BlockPos pos) {
        VolumeConnection connection = getCurrentConnection();
        if (connection == null) {
            return false;
        }
        Box box = connection.getBox();
        if (box.contains(pos)) {
            return false;
        }
        for (BlockPos p : PositionUtil.getCorners(box.min(), box.max())) {
            if (PositionUtil.isNextTo(p, pos)) {
                return true;
            }
        }
        return false;
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == TilesAPI.TILE_AREA_PROVIDER) {
            return areaProviderCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        areaProviderCap.invalidate();
    }
}
