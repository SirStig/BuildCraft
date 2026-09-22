/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjEffects;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.inventory.ItemHandlerWrapper;
import buildcraft.lib.inventory.ItemTransactorHelper;
import buildcraft.lib.inventory.NoSpaceTransactor;
import buildcraft.lib.inventory.TransactorEntityItem;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.BoundingBoxUtil;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.BCFactoryRegistries;
import buildcraft.factory.block.BlockChute;

/**
 * A 4-slot item buffer that periodically scans a small area above its open face for dropped items, pulls them
 * in, and pushes its own contents into whatever neighbouring inventory or entity will accept them -- the first
 * real, working item inventory in this port, and the first thing to actually call
 * {@link ItemHandlerManager#getHandlerForFace(Direction)} (see that class's own javadoc, which named this exact
 * use case).
 *
 * <p>{@code TileBC_Neptune}/{@code ITickable}/{@code update()} become {@link TileBC}/{@code serverTick()}, driven
 * by {@link BlockChute#getTicker}, exactly like {@code TilePowerConsumerTester}/{@code TileEngineWood}; the
 * client-side {@code world.isRemote} guard 1.12.2's {@code update()} opened with is redundant here, since
 * {@code getTicker} already returns {@code null} on the client and {@link #serverTick()} is simply never called
 * there.
 *
 * <p>{@code hasInventoryAtPosition} is dropped -- its only 1.12.2 caller was {@code BlockChute#getActualState}'s
 * {@code CONNECTED_MAP} synthesis, itself dropped as purely cosmetic (see {@link BlockChute}'s own javadoc).
 *
 * <p>{@code getOwner().getId()} (used to grant the "put the chute back to work" advancement) was part of the much
 * larger {@code TileBC_Neptune}, which this port's slimmer {@link TileBC} doesn't carry forward. {@link #owner}
 * is a small UUID field local to this tile instead, set from {@code setPlacedBy}'s {@code placer} and persisted --
 * exactly the pattern {@code TileEngineWood} already established for its own "free power" advancement, down to
 * keeping the advancement id under the old {@code buildcraftfactory} namespace rather than the collapsed
 * {@code buildcraft} mod id, since the advancement JSON itself is not ported (see
 * {@link AdvancementUtil#unlockAdvancement}'s own javadoc: an unregistered advancement id is a harmless one-time
 * warning, the same precedent {@code TileEngineWood}/{@code ItemWrench} already rely on). Unlike
 * {@code TileEngineWood}, there is no separate {@code givenAdvancement} guard -- 1.12.2's chute never had one
 * either, relying on {@code PlayerAdvancements#award} already being a no-op for an advancement a player has.
 */
public class TileChute extends TileBC implements IDebuggable {
    private static final Identifier ADVANCEMENT_DID_INSERT =
        Identifier.fromNamespaceAndPath("buildcraftfactory", "retired_hopper");

    private static final int PICKUP_MAX = 3;

    public final ItemHandlerManager itemManager = new ItemHandlerManager((handler, slot, before, after) -> markDirtyAndSync());
    public final ItemHandlerSimple inv = itemManager.addInvHandler("inv", 4, EnumAccess.INSERT, EnumPipePart.VALUES);
    /** {@link ItemHandlerSimple} on this target is a plain {@code ResourceHandler<ItemResource>}, not an
     * {@link IItemTransactor} itself (contrast 1.20.1, where {@code ItemHandlerSimple} still implements it
     * directly) -- so every {@link ItemTransactorHelper} call below moves through this wrapper instead of
     * {@link #inv} directly. */
    private final IItemTransactor invTransactor = new ItemHandlerWrapper(inv);

    private final MjBattery battery = new MjBattery(MjAPI.MJ);
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);
    private int progress = 0;

    @Nullable
    private UUID owner;

    public TileChute(BlockPos pos, BlockState state) {
        super(BCFactoryRegistries.CHUTE_TYPE.get(), pos, state);
    }

    public void onPlacedBy(@Nullable LivingEntity placer) {
        owner = placer == null ? null : placer.getUUID();
    }

    private void pickupItems(Direction currentSide) {
        AABB aabb = BoundingBoxUtil.extrudeFace(worldPosition, currentSide, 0.25);
        int count = PICKUP_MAX;
        for (ItemEntity entity : level.getEntities(EntityTypeTest.forClass(ItemEntity.class), aabb, EntitySelector.ENTITY_STILL_ALIVE)) {
            int moved = ItemTransactorHelper.move(new TransactorEntityItem(entity), invTransactor, count);
            count -= moved;
            if (count <= 0) {
                return;
            }
        }
    }

    private void putInNearInventories(Direction currentSide) {
        boolean didWork = false;
        List<Direction> sides = new ArrayList<>(Arrays.asList(Direction.values()));
        Collections.shuffle(sides, new Random());
        sides.removeIf(Predicate.isEqual(currentSide));

        for (Direction side : sides) {
            var transactor = ItemTransactorHelper.getTransactor(level, worldPosition.relative(side), side.getOpposite());
            if (transactor != NoSpaceTransactor.INSTANCE && ItemTransactorHelper.move(invTransactor, transactor, 1) > 0) {
                didWork = true;
            }
        }
        for (Direction side : sides) {
            AABB aabb = new AABB(worldPosition.relative(side));
            for (Entity entity : level.getEntities((Entity) null, aabb, e -> !(e instanceof LivingEntity))) {
                var transactor = ItemTransactorHelper.getTransactor(entity, side.getOpposite());
                if (transactor != NoSpaceTransactor.INSTANCE && ItemTransactorHelper.move(invTransactor, transactor, 1) > 0) {
                    didWork = true;
                }
            }
        }

        if (didWork && owner != null) {
            AdvancementUtil.unlockAdvancement(owner, ADVANCEMENT_DID_INSERT);
        }
    }

    /** Driven by {@link BlockChute#getTicker}; was {@code ITickable.update()}. */
    public void serverTick() {
        if (!(level.getBlockState(worldPosition).getBlock() instanceof BlockChute)) {
            return;
        }

        MjEffects.tick(level, worldPosition, battery);

        Direction currentSide = level.getBlockState(worldPosition).getValue(BuildCraftProperties.BLOCK_FACING_6);

        int target = 100000;
        if (currentSide == Direction.UP) {
            progress += 1000; // can be free because of gravity
        }
        progress += battery.extractPower(0, target - progress);

        if (progress >= target) {
            progress = 0;
            pickupItems(currentSide);
        }

        putInNearInventories(currentSide);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        itemManager.deserialize(input);
        progress = input.getIntOr("progress", 0);
        battery.setStored(input.getLongOr("battery", 0));
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemManager.serialize(output);
        output.putInt("progress", progress);
        output.putLong("battery", battery.getStored());
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
        }
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("progress = " + progress);
    }
}
