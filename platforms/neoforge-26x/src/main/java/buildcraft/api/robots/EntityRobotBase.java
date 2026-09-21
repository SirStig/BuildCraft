/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.robots;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.core.IZone;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

/*
 * Port notes:
 *
 * EntityLiving is Mob, and Mob's constructor now takes its EntityType as well as the Level, so subclasses
 * have to pass one up.
 *
 * A robot used to implement IItemHandler and BuildCraft's IFluidHandlerAdv directly. Both are gone, and their
 * replacement -- ResourceHandler -- is generic, so a robot cannot implement it twice for two resource types.
 * The two handlers are therefore exposed as separate objects, which is also what the capability system wants:
 * Capabilities.Item.ENTITY and Capabilities.Fluid.ENTITY are registered against these rather than against the
 * robot itself.
 */
public abstract class EntityRobotBase extends Mob {

    /** The robot's inventory, as the item capability exposes it. Was {@code implements IItemHandler}. */
    public abstract ResourceHandler<ItemResource> getItemHandler();

    /** The robot's tank, as the fluid capability exposes it. Was {@code implements IFluidHandlerAdv}. */
    public abstract ResourceHandler<FluidResource> getFluidHandler();

    public static final long MAX_POWER =  5000 * MjAPI.MJ;
    public static final long SAFETY_POWER = MAX_POWER / 5;
    public static final long SHUTDOWN_POWER = 0;
    public static final long NULL_ROBOT_ID = Long.MAX_VALUE;

    protected EntityRobotBase(net.minecraft.world.entity.EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    public abstract void setItemInUse(ItemStack stack);

    public abstract void setItemActive(boolean b);

    public abstract boolean isMoving();

    public abstract DockingStation getLinkedStation();

    public abstract RedstoneBoardRobot getBoard();

    public abstract void aimItemAt(float yaw, float pitch);

    public abstract void aimItemAt(BlockPos pos);

    public abstract float getAimYaw();

    public abstract float getAimPitch();

    public long getPower() {
        return getBattery().getStored();
    }

    public abstract MjBattery getBattery();

    public abstract DockingStation getDockingStation();

    public abstract void dock(DockingStation station);

    public abstract void undock();

    public abstract IZone getZoneToWork();

    public abstract IZone getZoneToLoadUnload();

    public abstract boolean containsItems();

    public abstract boolean hasFreeSlot();

    public abstract void unreachableEntityDetected(Entity entity);

    public abstract boolean isKnownUnreachable(Entity entity);

    public abstract long getRobotId();

    public abstract IRobotRegistry getRegistry();

    public abstract void releaseResources();

    public abstract void onChunkUnload();

    public abstract ItemStack receiveItem(BlockEntity tile, ItemStack stack);

    public abstract void setMainStation(DockingStation station);
}
