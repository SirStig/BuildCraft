/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.BuildCraft;

/**
 * Mirrors the 26.x class of the same name -- see that one's javadoc for exactly what this drops relative to the
 * original {@code common}/1.12.2 {@code Blueprint} (a plain {@link BlockState} palette rather than a full
 * {@code ISchematicBlock}), and what it now lands relative to this port's own earlier, more-simplified version
 * of this class (rotation, tile-entity NBT capture/restore, and a minimal real slice of the JSON rule system --
 * see {@link #facing}/{@link #tileData}/{@link #IGNORED}).
 *
 * <p>The only 1.20.1-specific differences from the 26.x file are NBT shape ({@link CompoundTag} getters return
 * values directly here rather than {@code Optional} (1.20.1 kept the old NBT API), and
 * {@link NBTUtilBC#getItemData} hands back a live, mutable tag rather than a detached copy -- see
 * {@code buildcraft.builders.item.ItemBlueprint}'s own javadoc for how that changes {@link #writeToStack}) and
 * the {@code BlockState#is(TagKey)} overload used for {@link #IGNORED} (1.20.1 has the plain one-argument
 * overload directly; 26.x only exposes the {@code (tag, predicate)} one).
 */
public class Blueprint {
    /** See the 26.x class's own javadoc on this field -- ported from 1.12.2's own default blueprint rule file. */
    public static final TagKey<Block> IGNORED =
        TagKey.create(Registries.BLOCK, new ResourceLocation(BuildCraft.MOD_ID, "blueprint_ignore"));

    public BlockPos size = BlockPos.ZERO;
    public final List<BlockState> palette = new ArrayList<>();
    public int[] data = new int[0];
    /** See the 26.x class's own javadoc on this field. */
    public Direction facing = Direction.NORTH;
    /** See the 26.x class's own javadoc on this field. */
    public final Map<Integer, CompoundTag> tileData = new HashMap<>();

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

    /** Captures every block state (and, per {@link #tileData}, block entity) in the box {@code [min, min + size)}
     * as seen from {@code facing}. A block in {@link #IGNORED} is captured as air, matching {@code
     * JsonRule#ignore}. */
    public static Blueprint capture(Level level, BlockPos min, BlockPos size, Direction facing) {
        Blueprint blueprint = new Blueprint();
        blueprint.size = size;
        blueprint.facing = facing;
        blueprint.data = new int[size.getX() * size.getY() * size.getZ()];
        for (int z = 0; z < size.getZ(); z++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) {
                    BlockPos worldPos = min.offset(x, y, z);
                    BlockState state = level.getBlockState(worldPos).is(IGNORED)
                        ? Blocks.AIR.defaultBlockState()
                        : level.getBlockState(worldPos);
                    if (!state.isAir()) {
                        BlockEntity blockEntity = level.getBlockEntity(worldPos);
                        if (blockEntity != null) {
                            CompoundTag tileNbt = blockEntity.saveWithoutMetadata();
                            if (!tileNbt.isEmpty()) {
                                blueprint.tileData.put(blueprint.index(x, y, z), tileNbt);
                            }
                        }
                    }
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
        nbt.putString("facing", facing.getSerializedName());
        CompoundTag tileDataTag = new CompoundTag();
        tileData.forEach((index, tag) -> tileDataTag.put(String.valueOf(index), tag));
        nbt.put("tileData", tileDataTag);
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
        Direction facing = Direction.byName(nbt.contains("facing") ? nbt.getString("facing") : "north");
        blueprint.facing = facing == null ? Direction.NORTH : facing;
        CompoundTag tileDataTag = nbt.getCompound("tileData");
        for (String key : tileDataTag.getAllKeys()) {
            try {
                blueprint.tileData.put(Integer.parseInt(key), tileDataTag.getCompound(key));
            } catch (NumberFormatException ignored) {
                // Not one of ours -- skip rather than fail the whole blueprint load.
            }
        }
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
