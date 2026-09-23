/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import net.minecraftforge.common.capabilities.Capability;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.mj.MjEffects;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.fake.FakePlayerBC;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * The stripes pipe's own real 1.12.2 mechanic -- see the 26.x copy of this file for the full account (confirmed
 * by reading {@code PipeBehaviourStripes} directly): an MJ-powered pipe that mines the block beyond its single
 * open face, and offers any item about to leave that same face to {@link PipeApi#stripeRegistry} first (a hoe
 * tills farmland, and so on). Block-break progress overlay is dropped (cosmetic only, keyed by a breaker entity
 * id a pipe behaviour has none of). {@code BuildCraftAPI.fakePlayerProvider} is wired in
 * {@code BCTransportRegistries#register} -- confirmed unassigned anywhere else in this port before this batch.
 */
public class PipeBehaviourStripes extends PipeBehaviour implements IStripesActivator, IMjRedstoneReceiver {
    private final MjBattery battery = new MjBattery(256 * MjAPI.MJ);

    @Nullable
    public Direction direction = null;
    private long progress;

    public PipeBehaviourStripes(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourStripes(IPipe pipe, CompoundTag nbt, HolderLookup.Provider registries) {
        super(pipe, nbt, registries);
        battery.setStored(nbt.getLong("battery"));
        direction = NBTUtilBC.readEnum(nbt.get("direction"), Direction.class);
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.putLong("battery", battery.getStored());
        nbt.put("direction", NBTUtilBC.writeEnum(direction));
        return nbt;
    }

    private void setDirection(@Nullable Direction newValue) {
        if (direction != newValue) {
            direction = newValue;
            if (!pipe.getHolder().getPipeLevel().isClientSide()) {
                pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
            }
        }
    }

    // IMjRedstoneReceiver

    @Override
    public boolean canConnect(@NotNull IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        return battery.getCapacity() - battery.getStored();
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        return battery.addPowerChecking(microJoules, simulate);
    }

    // Stripes

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourStripes);
    }

    @Override
    public void onTick() {
        Level level = pipe.getHolder().getPipeLevel();
        BlockPos pos = pipe.getHolder().getPipePos();
        if (level.isClientSide()) {
            return;
        }
        if (direction == null || pipe.isConnected(direction)) {
            int sides = 0;
            Direction dir = null;
            for (Direction face : Direction.values()) {
                if (pipe.isConnected(face)) {
                    sides++;
                    dir = face;
                }
            }
            if (sides == 1) {
                setDirection(dir.getOpposite());
            } else {
                setDirection(null);
            }
        }
        MjEffects.tick(level, pos, battery);
        if (direction != null && level instanceof ServerLevel serverLevel) {
            BlockPos offset = pos.relative(direction);
            long target = BlockUtil.computeBlockBreakPower(level, offset);
            if (target > 0) {
                if (progress < target) {
                    progress += battery.extractPower(0, Math.min(target - progress, MjAPI.MJ * 10));
                } else {
                    BlockUtil.breakBlockAndGetDrops(serverLevel, offset, new ItemStack(Items.DIAMOND_PICKAXE))
                        .ifPresent(stacks -> stacks.forEach(stack -> sendItem(stack, direction)));
                    progress = 0;
                }
            }
        } else {
            progress = 0;
        }
    }

    @PipeEventHandler
    public void onDrop(PipeEventItem.Drop event) {
        Direction dir = direction;
        if (dir == null) {
            return;
        }
        IPipeHolder holder = pipe.getHolder();
        Level level = holder.getPipeLevel();
        BlockPos pos = holder.getPipePos();
        if (!(level instanceof ServerLevel serverLevel) || BuildCraftAPI.fakePlayerProvider == null) {
            return;
        }
        FakePlayerBC player = (FakePlayerBC) BuildCraftAPI.fakePlayerProvider.getFakePlayer(serverLevel, holder.getOwner(), pos);
        player.getInventory().clearContent();
        player.getInventory().setItem(player.getInventory().selected, event.getStack());
        if (PipeApi.stripeRegistry.handleItem(level, pos, dir, event.getStack(), player, this)) {
            event.setStack(ItemStack.EMPTY);
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().removeItemNoUpdate(i);
                if (stack != null && !stack.isEmpty()) {
                    sendItem(stack, dir);
                }
            }
        }
    }

    @Override
    public void dropItem(@NotNull ItemStack stack, Direction direction) {
        InventoryUtil.drop(pipe.getHolder().getPipeLevel(), pipe.getHolder().getPipePos(), stack);
    }

    @Override
    public boolean sendItem(@NotNull ItemStack stack, Direction from) {
        PipeFlow flow = pipe.getFlow();
        if (flow instanceof IFlowItems flowItems) {
            flowItems.insertItemsForce(stack, from, null, 0.02);
            return true;
        }
        return false;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable Direction facing) {
        if (capability == MjCapabilities.CONNECTOR || capability == MjCapabilities.RECEIVER
            || capability == MjCapabilities.REDSTONE_RECEIVER) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }
}
