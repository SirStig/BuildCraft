/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for exactly what this drops relative to the
 * original {@code common}/1.12.2 {@code Blueprint} (a plain {@link BlockState} palette rather than a full
 * {@code ISchematicBlock}: no tile-entity NBT, no JSON rule system, no entities, no rotation).
 *
 * <p>The only 1.20.1-specific difference from the 26.x file is NBT shape: {@link CompoundTag} getters return
 * values directly here rather than {@code Optional} (1.20.1 kept the old NBT API), and
 * {@link NBTUtilBC#getItemData} hands back a live, mutable tag rather than a detached copy -- see
 * {@code buildcraft.builders.item.ItemBlueprint}'s own javadoc for how that changes {@link #writeToStack}.
 */
public class Blueprint {
    public BlockPos size = BlockPos.ZERO;
    public final List<BlockState> palette = new ArrayList<>();
    public int[] data = new int[0];

    public int index(int x, int y, int z) {
        return ((z * size.getY()) + y) * size.getX() + x;
    }

    public BlockState get(int x, int y, int z) {
        return palette.get(data[index(x, y, z)]);
    }

    public BlockState get(int index) {
        return palette.get(data[index]);
    }

    public BlockPos posFromIndex(int index) {
        int x = index % size.getX();
        int y = (index / size.getX()) % size.getY();
        int z = index / (size.getX() * size.getY());
        return new BlockPos(x, y, z);
    }

    public boolean isEmpty() {
        return data.length == 0;
    }

    /** Captures every block state in the box {@code [min, min + size)}. */
    public static Blueprint capture(Level level, BlockPos min, BlockPos size) {
        Blueprint blueprint = new Blueprint();
        blueprint.size = size;
        blueprint.data = new int[size.getX() * size.getY() * size.getZ()];
        for (int z = 0; z < size.getZ(); z++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) {
                    BlockState state = level.getBlockState(min.offset(x, y, z));
                    int paletteIndex = blueprint.palette.indexOf(state);
                    if (paletteIndex == -1) {
                        paletteIndex = blueprint.palette.size();
                        blueprint.palette.add(state);
                    }
                    blueprint.data[blueprint.index(x, y, z)] = paletteIndex;
                }
            }
        }
        return blueprint;
    }

    /** One combined shopping list for the whole blueprint -- see the 26.x class javadoc for why this isn't
     * recomputed incrementally the way the original's per-block {@code toPlaceRequiredItems} was. */
    public List<ItemStack> computeRequiredItems() {
        int[] counts = new int[palette.size()];
        for (int paletteIndex : data) {
            counts[paletteIndex]++;
        }
        List<ItemStack> required = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            BlockState state = palette.get(i);
            if (counts[i] == 0 || state.isAir()) {
                continue;
            }
            Item item = state.getBlock().asItem();
            if (item == Items.AIR) {
                continue;
            }
            required.add(new ItemStack(item, counts[i]));
        }
        return required;
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("size", NBTUtilBC.writeBlockPos(size));
        nbt.put("palette", NBTUtilBC.writeCompoundList(palette.stream().map(NbtUtils::writeBlockState)));
        nbt.putIntArray("data", data);
        return nbt;
    }

    public static Blueprint deserializeNBT(CompoundTag nbt, HolderGetter<Block> blocks) {
        Blueprint blueprint = new Blueprint();
        BlockPos size = NBTUtilBC.readBlockPos(nbt.get("size"));
        blueprint.size = size == null ? BlockPos.ZERO : size;
        NBTUtilBC.readCompoundList(nbt.get("palette"))
            .map(paletteEntry -> NbtUtils.readBlockState(blocks, paletteEntry))
            .forEach(blueprint.palette::add);
        blueprint.data = nbt.getIntArray("data");
        return blueprint;
    }

    public static HolderGetter<Block> blockLookup(Level level) {
        return level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup();
    }

    @Nullable
    public static Blueprint readFromStack(ItemStack stack, Level level) {
        CompoundTag data = NBTUtilBC.getItemData(stack).getCompound("blueprint");
        return data.isEmpty() ? null : deserializeNBT(data, blockLookup(level));
    }

    /** 1.20.1's {@link NBTUtilBC#getItemData} hands back a live tag (the stack's own, created if absent), so --
     * unlike the 26.x version, which needs {@code updateItemData} to write the mutation back -- mutating it in
     * place is enough here. */
    public static void writeToStack(ItemStack stack, Blueprint blueprint) {
        NBTUtilBC.getItemData(stack).put("blueprint", blueprint.serializeNBT());
    }
}
