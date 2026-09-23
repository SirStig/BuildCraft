/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraftforge.network.NetworkHooks;

import buildcraft.api.enums.EnumLaserTableType;
import buildcraft.api.mj.ILaserTargetBlock;

import buildcraft.lib.block.BlockBCTile;

import buildcraft.silicon.tile.TileAdvancedCraftingTable;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.silicon.tile.TileChargingTable;
import buildcraft.silicon.tile.TileIntegrationTable;
import buildcraft.silicon.tile.TileLaserTableBase;

/** Mirrors the 26.x copy of this class -- see that one's own javadoc for the shared design and the
 * {@code TileLaser}/programming-table scope notes. The one real platform difference is opening the menu: this
 * target keeps a single {@link #use} (see {@code BlockAutoWorkbenchItems}'s own javadoc for that platform note)
 * and opens through {@link NetworkHooks#openScreen(ServerPlayer, MenuProvider, BlockPos)}, which writes the
 * {@code BlockPos} the client needs automatically -- unlike 26.x, which needs {@code writeClientSideData}. */
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

    /** See the 26.x copy of this class's own javadoc. */
    private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 9 / 16.0, 1);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MenuProvider provider)) {
            // The charging table has no GUI -- matches 1.12.2's onBlockActivated, which returned false for it too.
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, provider, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
