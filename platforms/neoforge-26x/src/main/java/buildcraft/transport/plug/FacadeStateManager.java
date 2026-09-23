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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.BCLog;
import buildcraft.api.facades.FacadeAPI;
import buildcraft.api.facades.IFacade;
import buildcraft.api.facades.IFacadePhasedState;
import buildcraft.api.facades.IFacadeRegistry;
import buildcraft.api.facades.IFacadeState;

import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.world.SingleBlockAccess;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.plug.FacadeStateManager}: scans every registered block once, at
 * {@link #init()}, for every {@link BlockState} that is safe to disguise a pipe as -- no block entity, a normal
 * cube model, a real item to craft it from.
 *
 * <p><b>Scope cuts from 1.12.2, all deliberate:</b>
 * <ul>
 * <li>The {@link net.minecraftforge.fml.common.event.FMLInterModComms}-driven {@code disabledBlocks}/
 *     {@code customBlocks} maps ({@code receiveInterModComms}, reading {@link FacadeAPI#IMC_FACADE_DISABLE}/
 *     {@link FacadeAPI#IMC_FACADE_CUSTOM}) are not ported: nothing anywhere in this port's own
 *     {@code InterModComms} handling (there isn't any yet, confirmed by a repo-wide search) ever calls it, so
 *     wiring the consumption side without a dispatcher would be dead code. {@link FacadeAPI}'s send-side helpers
 *     stay real for any other mod that wants to call them ahead of that dispatcher landing.</li>
 * <li>The elaborate {@code varyingProperties}/{@code doesPropertyConform} scan (working around real, reported
 *     bugs in specific 1.12.2-era mods' {@code IProperty} implementations, and the metadata-subtype tooltip they
 *     fed) has nothing left to work around -- see {@link FacadeBlockStateInfo}'s own javadoc.</li>
 * <li>The self-test NBT/buffer round-trip {@code init()} used to run per state is dropped along with the buffer
 *     path itself -- see {@link FacadePhasedState}'s own javadoc for why no facade ever serialises to a network
 *     buffer on this port.</li>
 * <li>{@code previewState} (a guide-book-only "what facade item icon to show before any are unlocked" stand-in)
 *     has no consumer on this port (no guide book yet), so it is dropped along with the early-return it gated.</li>
 * </ul>
 *
 * <p>{@link #validFacadeStates} is a {@link LinkedHashMap}, not 1.12.2's {@code TreeMap} -- {@code BlockState}
 * has no natural ordering on this target (no {@code BlockUtil.blockStateComparator()} equivalent was ported; see
 * that class's own trimmed javadoc), and nothing here actually needs sorted iteration, only a stable one
 * (insertion order, i.e. {@link BuiltInRegistries#BLOCK}'s own registration order) for reproducible scan logs.
 */
public enum FacadeStateManager implements IFacadeRegistry {
    INSTANCE;

    public static final boolean DEBUG = BCLog.logger.isDebugEnabled();
    public static final Map<BlockState, FacadeBlockStateInfo> validFacadeStates = new LinkedHashMap<>();
    public static final Map<ItemStackKey, List<FacadeBlockStateInfo>> stackFacades = new LinkedHashMap<>();
    public static FacadeBlockStateInfo defaultState;

    /** Populates {@link #validFacadeStates}/{@link #stackFacades} from every currently-registered block. Called
     * once, from {@code BCTransportRegistries}' {@code FMLCommonSetupEvent} listener -- late enough that every
     * mod's blocks (not just this one's) are already in {@link BuiltInRegistries#BLOCK}, matching 1.12.2's own
     * post-init timing. */
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

    /** @return True if {@code state} is safe to disguise a pipe segment as: no fluid, no block entity, a plain
     *         model (not invisible/special), and -- unless it is glass, kept for 1.12.2's own reason: panes and
     *         thin glass blocks are useful facades despite not filling their bounding box -- a genuine full
     *         cube. {@link StainedGlassBlock}/{@link TransparentBlock} are the modern renames of 1.12.2's
     *         {@code BlockStainedGlass}/{@code BlockGlass} (confirmed via {@code javap}: 1.12.2's plain
     *         {@code BlockGlass} has no direct modern namesake, but {@code Blocks.GLASS} is a
     *         {@link TransparentBlock} on this target). */
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
        if (block instanceof StainedGlassBlock || block instanceof TransparentBlock) {
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
