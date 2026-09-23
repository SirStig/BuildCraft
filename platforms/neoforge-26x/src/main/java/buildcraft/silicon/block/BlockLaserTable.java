/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.enums.EnumLaserTableType;
import buildcraft.api.mj.ILaserTargetBlock;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.silicon.tile.TileAdvancedCraftingTable;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.silicon.tile.TileChargingTable;
import buildcraft.silicon.tile.TileIntegrationTable;
import buildcraft.silicon.tile.TileLaserTableBase;

/**
 * Ported from 1.12.2's {@code BlockLaserTable}: one shared block class, parameterised by {@link EnumLaserTableType},
 * for every laser-powered table -- kept as a single class rather than one per table to match the original's own
 * design, the same way {@link EnumLaserTableType} itself is shared.
 *
 * <p><b>{@code TileLaser}, the laser emitter block that actually feeds these tables power, is not ported this
 * round.</b> Every table here still fully implements {@link buildcraft.api.mj.ILaserTarget} (see
 * {@code TileLaserTableBase}) and this block still implements {@link ILaserTargetBlock}, so a future port of
 * {@code TileLaser} can find and power them with no changes needed here -- there just isn't yet an in-game way to
 * deliver that power (short of a debug command calling {@code receiveLaserPower} directly). Flagged in PORTING.md.
 *
 * <p>1.12.2's {@code isOpaqueCube()/isFullCube() -> false} is {@code noOcclusion()} on the registered properties
 * (matching every other non-cube BuildCraft machine in this port); the {@code CUTOUT} render layer is the block
 * models' render type. 1.12.2's custom 16x9x16 bounding box (a thin table-top, not a full cube) is not reproduced --
 * this round's block models are simple full-cube placeholders (see PORTING.md), so a custom shape would just be a
 * hitbox mismatch against its own model; left as a follow-up alongside real table models.
 *
 * <p>Only {@link EnumLaserTableType#ASSEMBLY_TABLE}, {@link EnumLaserTableType#ADVANCED_CRAFTING_TABLE},
 * {@link EnumLaserTableType#INTEGRATION_TABLE} and {@link EnumLaserTableType#CHARGING_TABLE} are ever constructed
 * with this block -- {@link EnumLaserTableType#PROGRAMMING_TABLE} has no registration in
 * {@code buildcraft.BCSiliconRegistries}. 1.12.2's own {@code TileProgrammingTable_Neptune} always returned 0 from
 * {@code getTarget()} and was itself gated behind a dev-only flag never set in a real install -- confirmed dead,
 * unfinished code, not a real feature to port. {@link #newBlockEntity} and {@link #useWithoutItem} both throw on
 * that case rather than silently handling it, so a future re-addition can't be missed.
 */
public class BlockLaserTable extends BlockBCTile implements ILaserTargetBlock {
    private final EnumLaserTableType type;

    public BlockLaserTable(EnumLaserTableType type, BlockBehaviour.Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (type) {
            case ASSEMBLY_TABLE -> new TileAssemblyTable(pos, state);
            case ADVANCED_CRAFTING_TABLE -> new TileAdvancedCraftingTable(pos, state);
            case INTEGRATION_TABLE -> new TileIntegrationTable(pos, state);
            case CHARGING_TABLE -> new TileChargingTable(pos, state);
            case PROGRAMMING_TABLE -> throw new IllegalStateException(
                "The programming table is dead 1.12.2 code, never ported -- see this class's own javadoc.");
        };
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> entityType) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> {
            if (be instanceof TileLaserTableBase tile) {
                tile.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider);
            return InteractionResult.SUCCESS;
        }
        // The charging table has no GUI -- matches 1.12.2's onBlockActivated, which returned false for it too.
        return InteractionResult.PASS;
    }
}
