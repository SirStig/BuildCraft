/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.BCLog;
import buildcraft.api.facades.IFacade;
import buildcraft.api.facades.IFacadePhasedState;
import buildcraft.api.facades.IFacadeRegistry;
import buildcraft.api.facades.IFacadeState;

import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.world.SingleBlockAccess;

/** Port of 1.12.2's {@code buildcraft.silicon.plug.FacadeStateManager} -- see the 26.x copy of this class for the
 * full account of the IMC-consumption/varying-properties/self-test scope cuts.
 *
 * <p>The one real per-platform divergence, found by {@code javap} rather than assumed: 1.20.1's plain glass
 * block class is {@code GlassBlock}, and both it and {@code StainedGlassBlock} share a common
 * {@link AbstractGlassBlock} base -- so this copy checks {@code instanceof AbstractGlassBlock} alone. On 26.x,
 * `javap` against the real merged jar found no such shared base any more (no {@code GlassBlock}/
 * {@code AbstractGlassBlock} class exists there at all); the modern rename of plain glass is
 * {@code TransparentBlock}, unrelated by inheritance to {@code StainedGlassBlock}, so that copy checks both
 * types explicitly instead. */
public enum FacadeStateManager implements IFacadeRegistry {
    INSTANCE;

    public static final boolean DEBUG = BCLog.logger.isDebugEnabled();
    public static final Map<BlockState, FacadeBlockStateInfo> validFacadeStates = new LinkedHashMap<>();
    public static final Map<ItemStackKey, List<FacadeBlockStateInfo>> stackFacades = new LinkedHashMap<>();
    public static FacadeBlockStateInfo defaultState;

    public static void init() {
        defaultState = new FacadeBlockStateInfo(Blocks.AIR.defaultBlockState(), ItemStack.EMPTY);
        validFacadeStates.clear();
        stackFacades.clear();
        for (Block block : BuiltInRegistries.BLOCK) {
            scanBlock(block);
        }
        if (DEBUG) {
            BCLog.logger.info("[transport.facade] Found " + validFacadeStates.size() + " valid facade states across "
                + BuiltInRegistries.BLOCK.size() + " registered blocks.");
        }
    }

    private static void scanBlock(Block block) {
        try {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!isValidFacadeState(block, state)) {
                    continue;
                }
                ItemStack requiredStack = getRequiredStack(state);
                FacadeBlockStateInfo info = new FacadeBlockStateInfo(state, requiredStack);
                validFacadeStates.put(state, info);
                if (info.isVisible) {
                    ItemStackKey stackKey = new ItemStackKey(info.requiredStack);
                    stackFacades.computeIfAbsent(stackKey, k -> new ArrayList<>()).add(info);
                }
                if (DEBUG) {
                    BCLog.logger.info("[transport.facade] Added " + info);
                }
            }
        } catch (RuntimeException e) {
            BCLog.logger.warn("[transport.facade] Skipping " + block + " as something about it threw an exception!", e);
        }
    }

    private static boolean isValidFacadeState(Block block, BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.hasBlockEntity()) {
            return false;
        }
        if (state.getRenderShape() != RenderShape.MODEL) {
            return false;
        }
        if (block instanceof AbstractGlassBlock) {
            return true;
        }
        return state.isCollisionShapeFullBlock(new SingleBlockAccess(state), SingleBlockAccess.POS);
    }

    private static ItemStack getRequiredStack(BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    // IFacadeRegistry

    @Override
    public Collection<? extends IFacadeState> getValidFacades() {
        return validFacadeStates.values();
    }

    @Override
    public IFacadePhasedState createPhasedState(IFacadeState state, DyeColor activeColor) {
        return new FacadePhasedState((FacadeBlockStateInfo) state, activeColor);
    }

    @Override
    public IFacade createPhasedFacade(IFacadePhasedState[] states, boolean isHollow) {
        FacadePhasedState[] realStates = new FacadePhasedState[states.length];
        for (int i = 0; i < states.length; i++) {
            realStates[i] = (FacadePhasedState) states[i];
        }
        return new FacadeInstance(realStates, isHollow);
    }
}
