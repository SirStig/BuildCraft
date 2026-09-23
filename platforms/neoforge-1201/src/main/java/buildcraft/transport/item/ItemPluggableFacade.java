/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.item;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.facades.FacadeType;
import buildcraft.api.facades.IFacade;
import buildcraft.api.facades.IFacadeItem;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.SoundUtil;

import buildcraft.transport.plug.FacadeBlockStateInfo;
import buildcraft.transport.plug.FacadeInstance;
import buildcraft.transport.plug.FacadeStateManager;
import buildcraft.transport.plug.PluggableFacade;

import buildcraft.BCTransportRegistries;

/** Port of 1.12.2's {@code buildcraft.silicon.item.ItemPluggableFacade} -- see the 26.x copy of this class for
 * the full account (creative-tab/tooltip scope cuts). {@link NBTUtilBC#getItemData} on this target returns the
 * stack's live tag directly (1.20.1 still has real item NBT, see {@link ItemPluggableGate}'s own note), so
 * {@link #createItemStack} needs no separate write-back call the way 26.x's copy-on-read data components do. */
public class ItemPluggableFacade extends Item implements IItemPluggable, IFacadeItem {
    public ItemPluggableFacade(Properties properties) {
        super(properties);
    }

    @NotNull
    public ItemStack createItemStack(FacadeInstance state) {
        ItemStack stack = new ItemStack(this);
        NBTUtilBC.getItemData(stack).put("facade", state.writeToNbt());
        return stack;
    }

    public static FacadeInstance getStates(@NotNull ItemStack stack) {
        return FacadeInstance.readFromNbt(NBTUtilBC.getItemData(stack).getCompound("facade"));
    }

    @NotNull
    @Override
    public ItemStack getFacadeForBlock(BlockState state) {
        FacadeBlockStateInfo info = FacadeStateManager.validFacadeStates.get(state);
        if (info == null) {
            return ItemStack.EMPTY;
        }
        return createItemStack(FacadeInstance.createSingle(info, false));
    }

    @Override
    @Nullable
    public PipePluggable onPlace(
        @NotNull ItemStack stack, IPipeHolder holder, Direction side, Player player, InteractionHand hand
    ) {
        FacadeInstance fullState = getStates(stack);
        SoundUtil.playBlockPlace(holder.getPipeLevel(), holder.getPipePos(), fullState.phasedStates[0].stateInfo.state);
        return new PluggableFacade(BCTransportRegistries.PLUGGABLE_DEF_FACADE, holder, side, fullState);
    }

    @Override
    public Component getName(ItemStack stack) {
        FacadeInstance fullState = getStates(stack);
        Component base = super.getName(stack);
        if (fullState.type == FacadeType.BASIC) {
            ItemStack assumed = fullState.phasedStates[0].stateInfo.requiredStack;
            if (!assumed.isEmpty()) {
                return Component.translatable(
                    "buildcraft.item.plug_facade.named", base, assumed.getHoverName()
                );
            }
        }
        return base;
    }

    // IFacadeItem

    @Override
    public ItemStack createFacadeStack(IFacade facade) {
        return createItemStack((FacadeInstance) facade);
    }

    @Override
    @Nullable
    public IFacade getFacade(@NotNull ItemStack facade) {
        return getStates(facade);
    }
}
