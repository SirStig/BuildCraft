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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.mj.MjEffects;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;

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
 * in, and pushes its own contents into whatever neighbouring inventory or entity will accept them. Mirrors the
 * 26.x class of the same name -- see that one's javadoc for the full account of what changed from 1.12.2. This
 * file differs only in the usual 1.20.1 places: NBT is still {@code CompoundTag} ({@code load}/
 * {@code saveAdditional} rather than {@code loadAdditional}/{@code saveAdditional} over {@code ValueInput}/
 * {@code ValueOutput}), and capabilities are exposed by the block entity itself through {@code getCapability}
 * rather than registered against the block entity type -- so, unlike 26.x, {@link #mjReceiver} and
 * {@link #itemManager} are queried here rather than in {@code BCFactoryRegistries}.
 */
public class TileChute extends TileBC implements IDebuggable {
    private static final ResourceLocation ADVANCEMENT_DID_INSERT =
        new ResourceLocation("buildcraftfactory", "retired_hopper");

    private static final int PICKUP_MAX = 3;

    public final ItemHandlerManager itemManager = new ItemHandlerManager((handler, slot, before, after) -> markDirtyAndSync());
    public final ItemHandlerSimple inv = itemManager.addInvHandler("inv", 4, EnumAccess.INSERT, EnumPipePart.VALUES);

    private final MjBattery battery = new MjBattery(MjAPI.MJ);
    public final MjBatteryReceiver mjReceiver = new MjBatteryReceiver(battery);
    private final LazyOptional<IMjReceiver> receiverCap = LazyOptional.of(() -> mjReceiver);
    private final LazyOptional<IMjReadable> readableCap = LazyOptional.of(() -> mjReceiver);
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
            int moved = ItemTransactorHelper.move(new TransactorEntityItem(entity), inv, count);
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
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
            IItemTransactor transactor = ItemTransactorHelper.getTransactor(neighbor, side.getOpposite());
            if (transactor != NoSpaceTransactor.INSTANCE && ItemTransactorHelper.move(inv, transactor, 1) > 0) {
                didWork = true;
            }
        }
        for (Direction side : sides) {
            AABB aabb = new AABB(worldPosition.relative(side));
            for (Entity entity : level.getEntities((Entity) null, aabb, e -> !(e instanceof LivingEntity))) {
                IItemTransactor transactor = ItemTransactorHelper.getTransactor(entity, side.getOpposite());
                if (transactor != NoSpaceTransactor.INSTANCE && ItemTransactorHelper.move(inv, transactor, 1) > 0) {
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
    public void load(CompoundTag nbt) {
        super.load(nbt);
        itemManager.deserializeNBT(nbt.getCompound("inv_manager"));
        progress = nbt.getInt("progress");
        battery.setStored(nbt.getLong("battery"));
        owner = nbt.hasUUID("owner") ? nbt.getUUID("owner") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("inv_manager", itemManager.serializeNBT());
        nbt.putInt("progress", progress);
        nbt.putLong("battery", battery.getStored());
        if (owner != null) {
            nbt.putUUID("owner", owner);
        }
    }

    // Capabilities

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == MjCapabilities.RECEIVER) {
            return receiverCap.cast();
        }
        if (cap == MjCapabilities.READABLE) {
            return readableCap.cast();
        }
        LazyOptional<T> itemCap = itemManager.getCapability(cap, side);
        if (itemCap.isPresent()) {
            return itemCap;
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        receiverCap.invalidate();
        readableCap.invalidate();
    }

    // IDebuggable

    @Override
    public void getDebugInfo(List<String> left, List<String> right, @Nullable Direction side) {
        left.add("battery = " + battery.getDebugString());
        left.add("progress = " + progress);
    }
}
