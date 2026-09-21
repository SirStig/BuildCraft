/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.marker.MarkerSubCache;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.PositionUtil.Line;
import buildcraft.lib.misc.PositionUtil.LineSkewResult;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.tile.TileMarker;

import buildcraft.core.marker.PathSubCache;
import buildcraft.core.marker.VolumeSubCache;

/** Connects two nearby markers of the same {@link MarkerCache} type (volume or path) when the player looks
 * roughly along the line between them.
 *
 * <p><strong>Partial port.</strong> Mirrors the 26.x class of the same name -- see that one for the full
 * explanation of why {@code onItemRightClickVolumeBoxes} (the {@code buildcraft.core.marker.volume.*} addon
 * editor bundled into 1.12.2's {@code onItemRightClick}) is deliberately not ported here either: it is an
 * entirely separate feature from this class' real job (the marker-line connection logic below), and its
 * dependencies remain unported on both targets alike. */
public class ItemMarkerConnector extends Item {

    private static final ResourceLocation ADVANCEMENT_VOLUME_MARKER = new ResourceLocation("buildcraftcore", "markers");
    private static final ResourceLocation ADVANCEMENT_PATH_MARKER = new ResourceLocation("buildcraftcore", "path_markers");

    public ItemMarkerConnector(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            for (MarkerCache<?> cache : MarkerCache.CACHES) {
                if (interactCache(cache.getSubCache(level), player)) {
                    player.swing(hand);
                    return InteractionResultHolder.success(stack);
                }
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    private static <S extends MarkerSubCache<?>> boolean interactCache(S cache, Player player) {
        MarkerLineInteraction best = null;
        Vec3 playerPos = player.getEyePosition();
        Vec3 playerLook = player.getLookAngle();
        for (BlockPos marker : cache.getAllMarkers()) {
            ImmutableList<BlockPos> possibles = cache.getValidConnections(marker);
            for (BlockPos possible : possibles) {
                MarkerLineInteraction interaction = new MarkerLineInteraction(marker, possible, playerPos, playerLook);
                if (interaction.didInteract()) {
                    best = interaction.getBetter(best);
                }
            }
        }
        if (best == null) {
            return false;
        }
        if (cache.tryConnect(best.marker1, best.marker2) || cache.tryConnect(best.marker2, best.marker1)) {
            if (cache instanceof VolumeSubCache) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_VOLUME_MARKER);
            } else if (cache instanceof PathSubCache) {
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_PATH_MARKER);
            }
            refreshActiveState(cache, best.marker1);
            refreshActiveState(cache, best.marker2);
            return true;
        }
        return false;
    }

    public static boolean doesInteract(BlockPos a, BlockPos b, Player player) {
        return new MarkerLineInteraction(a, b, player.getEyePosition(), player.getLookAngle()).didInteract();
    }

    /** Pushes {@link BuildCraftProperties#ACTIVE} for a marker tile connected through this class -- see the
     * 26.x copy of this method for the full reasoning. */
    private static void refreshActiveState(MarkerSubCache<?> cache, BlockPos pos) {
        TileMarker<?> marker = cache.getMarker(pos);
        if (marker == null || marker.getLevel() == null) {
            return;
        }
        BlockState state = marker.getBlockState();
        if (!state.hasProperty(BuildCraftProperties.ACTIVE)) {
            return;
        }
        boolean active = marker.isActiveForRender();
        if (state.getValue(BuildCraftProperties.ACTIVE) != active) {
            marker.getLevel().setBlock(marker.getBlockPos(), state.setValue(BuildCraftProperties.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    @SuppressWarnings("WeakerAccess")
    private static class MarkerLineInteraction {
        public final BlockPos marker1, marker2;
        public final double distToPoint, distToLine;

        public MarkerLineInteraction(BlockPos marker1, BlockPos marker2, Vec3 playerPos, Vec3 playerEndPos) {
            this.marker1 = marker1;
            this.marker2 = marker2;
            LineSkewResult interactionPoint = PositionUtil.findLineSkewPoint(
                new Line(
                    VecUtil.convertCenter(marker1),
                    VecUtil.convertCenter(marker2)
                ),
                playerPos,
                playerEndPos
            );
            distToPoint = interactionPoint.closestPos.distanceTo(playerPos);
            distToLine = interactionPoint.distFromLine;
        }

        public boolean didInteract() {
            return distToPoint <= 3 && distToLine < 0.3;
        }

        public MarkerLineInteraction getBetter(MarkerLineInteraction other) {
            if (other == null) {
                return this;
            }
            if (other.marker1 == marker2 && other.marker2 == marker1) {
                return other;
            }
            if (other.distToLine < distToLine) {
                return other;
            }
            if (other.distToLine > distToLine) {
                return this;
            }
            if (other.distToPoint < distToPoint) {
                return other;
            }
            return this;
        }
    }
}
