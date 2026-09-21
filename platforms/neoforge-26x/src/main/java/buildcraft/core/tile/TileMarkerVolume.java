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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.BCCoreRegistries;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.ITileAreaProvider;

import buildcraft.lib.marker.MarkerSubCache;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.tile.TileMarker;

import buildcraft.core.marker.VolumeCache;
import buildcraft.core.marker.VolumeConnection;

/** One corner marker of a volume box.
 *
 * <p>1.12.2's {@code IDS}/{@code NET_SIGNALS_ON}/{@code NET_SIGNALS_OFF} ({@code IdAllocator}-tagged network
 * payloads) are dropped outright, not ported -- {@code buildcraft.lib.misc.data.IdAllocator} itself isn't
 * ported either, which is further confirmation nothing needs it. {@link TileBC}'s
 * {@code markDirtyAndSync()} replaces the whole id-tagged payload system with "sync the entire saved state",
 * which is all {@link #showSignals} ever needed: it is now just an ordinary persisted field, written and read
 * through {@link #saveAdditional}/{@link #loadAdditional} like everything else, and {@link #switchSignals()}
 * calls {@code markDirtyAndSync()} to push the change to clients. This is a genuine simplification versus
 * 1.12.2, not a compromise -- there is no more per-field network payload to design around.
 *
 * <p>{@code getRenderBoundingBox()}/{@code getMaxRenderDistanceSquared()} (both {@code @SideOnly(Side.CLIENT)}
 * 1.12.2 render-distance hints on {@code TileEntity}) are gone from {@code BlockEntity} entirely -- confirmed via
 * {@code javap}, neither survives in any form on this target, and vanilla's own render-distance model has moved
 * on since 1.12.2 regardless. Dropped rather than forced into a non-existent equivalent.
 *
 * <p>{@code IBlockState#getActualState} is gone (see {@code BlockMarkerBase}'s javadoc), so
 * {@link BuildCraftProperties#ACTIVE} has to be pushed to a real blockstate explicitly rather than computed at
 * render time. {@link #refreshActiveState()} is the one place that happens: it compares
 * {@link #isActiveForRender()} against the currently-stored blockstate value and, if they differ, writes the new
 * value with {@code Block.UPDATE_CLIENTS} (sync only -- deliberately not a flag that would re-notify neighbours,
 * since none of this class' own neighbour-facing behaviour depends on {@code ACTIVE}, so there is nothing for a
 * flag-3 write to loop back into anyway). It is called from every place in this class that can change whether a
 * connection exists or whether signals are showing: {@link #switchSignals()}, {@link #onPlacedBy}, and
 * {@link #onManualConnectionAttempt}. It is deliberately <em>not</em> called from connections formed through
 * {@code ItemMarkerConnector}'s generic marker-line interaction (which calls {@code cache.tryConnect} directly,
 * bypassing this tile's own methods entirely) -- that class refreshes the blockstate for both interacted markers
 * itself, see its own javadoc. One known gap: a marker whose connection is invalidated by a *different* marker
 * being removed (not this one) never gets its own {@code ACTIVE} refreshed, since that path runs entirely inside
 * {@code MarkerSubCache}/{@code MarkerConnection}, both out of scope for this pass. Currently harmless: nothing
 * renders {@code ACTIVE} yet (no block model differentiates it, matching {@code MarkerConnection#renderInWorld}'s
 * own deferral), so a stale value has no visible effect until the rendering pass lands.
 *
 * <p>{@link ITileAreaProvider} is exposed as a real capability, matching 1.12.2's
 * {@code caps.addCapabilityInstance(TilesAPI.CAP_TILE_AREA_PROVIDER, this, EnumPipePart.VALUES)}, but the wiring
 * moved: capabilities are bound to a block entity *type* in {@code BCCoreRegistries#registerCapabilities} now,
 * not attached per-instance in the constructor, so this class does not need to touch
 * {@link buildcraft.api.tiles.TilesAPI} at all. */
public class TileMarkerVolume extends TileMarker<VolumeConnection> implements ITileAreaProvider {

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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("showSignals", showSignals);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        showSignals = input.getBooleanOr("showSignals", false);
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
     * for the one known gap it leaves. */
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

    /** Was {@code TileEntity#onPlacedBy} in 1.12.2; {@link net.minecraft.world.level.block.entity.BlockEntity}
     * has no such hook any more, so {@code BlockMarkerVolume} calls this explicitly from its own
     * {@code setPlacedBy} override instead. */
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
}
