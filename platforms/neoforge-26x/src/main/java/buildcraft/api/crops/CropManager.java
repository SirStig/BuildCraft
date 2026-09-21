/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the
 * license, which should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.crops;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dispatches planting and harvesting to whichever {@link ICropHandler} claims the crop.
 *
 * <p>Two changes from 1.12.2, both about not falling over. The handler list is a
 * {@link CopyOnWriteArrayList} because mod loading is parallel now, so two mods registering at once is
 * ordinary. And every use of the default handler is null-checked: 1.12.2 dereferenced it unconditionally, which
 * was safe only because BuildCraft always set it during its own preInit, before anything could call in. With
 * parallel loading that ordering is no longer guaranteed.
 */
public final class CropManager {

    private static final List<ICropHandler> handlers = new CopyOnWriteArrayList<>();

    @Nullable
    private static volatile ICropHandler defaultHandler;

    private CropManager() {
    }

    public static void registerHandler(ICropHandler cropHandler) {
        handlers.add(cropHandler);
    }

    public static void setDefaultHandler(ICropHandler cropHandler) {
        defaultHandler = cropHandler;
    }

    @Nullable
    public static ICropHandler getDefaultHandler() {
        return defaultHandler;
    }

    public static boolean isSeed(ItemStack stack) {
        for (ICropHandler cropHandler : handlers) {
            if (cropHandler.isSeed(stack)) {
                return true;
            }
        }
        ICropHandler fallback = defaultHandler;
        return fallback != null && fallback.isSeed(stack);
    }

    public static boolean canSustainPlant(Level level, ItemStack seed, BlockPos pos) {
        for (ICropHandler cropHandler : handlers) {
            if (cropHandler.isSeed(seed) && cropHandler.canSustainPlant(level, seed, pos)) {
                return true;
            }
        }
        ICropHandler fallback = defaultHandler;
        return fallback != null && fallback.isSeed(seed) && fallback.canSustainPlant(level, seed, pos);
    }

    /**
     * Attempts to plant the crop given by the seed into the level, checking {@link ICropHandler#isSeed} and
     * {@link ICropHandler#canSustainPlant} first.
     */
    public static boolean plantCrop(Level level, Player player, ItemStack seed, BlockPos pos) {
        for (ICropHandler cropHandler : handlers) {
            if (cropHandler.isSeed(seed)
                && cropHandler.canSustainPlant(level, seed, pos)
                && cropHandler.plantCrop(level, player, seed, pos)) {
                return true;
            }
        }
        ICropHandler fallback = defaultHandler;
        if (fallback != null && fallback.isSeed(seed) && fallback.canSustainPlant(level, seed, pos)) {
            return fallback.plantCrop(level, player, seed, pos);
        }
        return false;
    }

    public static boolean isMature(BlockGetter blockAccess, BlockState state, BlockPos pos) {
        for (ICropHandler cropHandler : handlers) {
            if (cropHandler.isMature(blockAccess, state, pos)) {
                return true;
            }
        }
        ICropHandler fallback = defaultHandler;
        return fallback != null && fallback.isMature(blockAccess, state, pos);
    }

    public static boolean harvestCrop(Level level, BlockPos pos, NonNullList<ItemStack> drops) {
        BlockState state = level.getBlockState(pos);
        for (ICropHandler cropHandler : handlers) {
            if (cropHandler.isMature(level, state, pos)) {
                return cropHandler.harvestCrop(level, pos, drops);
            }
        }
        ICropHandler fallback = defaultHandler;
        return fallback != null && fallback.isMature(level, state, pos) && fallback.harvestCrop(level, pos, drops);
    }
}
