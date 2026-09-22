/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.tools.IToolWrench;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.factory.tile.TileFloodGate;

/**
 * Spreads a fluid piped into it out into the world; see {@link TileFloodGate}'s own javadoc for the search/
 * placement algorithm and every fluid-side design decision. This class is the wrench-toggle interaction and
 * registration glue around it.
 *
 * <p>1.12.2's {@code BlockFloodGate} had two other overrides beyond the wrench handler, both already covered by
 * precedent elsewhere in this pass: {@code createTileEntity}/{@code addProperties} are superseded by
 * {@link #newBlockEntity}/{@link BCFactoryRegistries}' registration, and {@code getActualState} (which
 * synthesised the purely-cosmetic {@code CONNECTED_MAP} blockstate from {@link TileFloodGate#openSides}) has no
 * modern hook to live in at all -- see {@link TileFloodGate}'s own javadoc for why that visualisation is dropped
 * while {@code openSides} itself is not.
 *
 * <p><b>Unlike {@link buildcraft.core.block.BlockEngineCreative}, this block's wrench interaction is actually
 * reachable.</b> {@code ItemWrench#useOn} intercepts a wrench click via {@code CustomRotationHelper.INSTANCE
 * .attemptRotateBlock} before the game ever reaches a block's own {@link #useItemOn}, but only when the block
 * either implements {@code ICustomRotationHandler} or has a handler registered against it -- neither is true of
 * this block (a flood gate has no facing to rotate), so {@code attemptRotateBlock} falls through to its own
 * {@code InteractionResult.PASS} default, and {@code ItemWrench#useOn} returns that same {@code PASS} without
 * ever calling {@code wrenchUsed} (no slide sound or wrench advancement from toggling a side, matching 1.12.2,
 * whose wrench never played one for this block either). A {@code PASS} from the item does not consume the
 * interaction, so it falls through to this override -- confirmed by real RCON testing, not just this reasoning;
 * see PORTING.md's progress entry for this machine.
 */
public class BlockFloodGate extends BlockBCTile {

    public BlockFloodGate(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFloodGate(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileFloodGate floodGate) {
                floodGate.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileFloodGate floodGate) {
            floodGate.onPlacedBy(placer);
        }
    }

    /** Was {@code onBlockActivated}. A wrench click on any side but {@link Direction#UP} toggles that side's
     * membership in {@link TileFloodGate#openSides}; a wrench click on top, or any non-wrench item, is left for
     * {@code super} (returning {@link InteractionResult#PASS} for the wrenched-top case, matching 1.12.2's own
     * {@code return false} there, which likewise skipped its {@code super} call). */
    @Override
    protected InteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (stack.getItem() instanceof IToolWrench) {
            Direction side = hit.getDirection();
            if (side != Direction.UP) {
                if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileFloodGate floodGate) {
                    floodGate.toggleOpenSide(side);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
