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
 * A captured rectangular region of blocks, ready to be rebuilt elsewhere by a {@link buildcraft.builders.tile.
 * TileBuilder}. The 1.12.2/{@code common} original ({@code buildcraft.builders.snapshot.Blueprint}) stored a
 * palette of {@code ISchematicBlock} -- a full rule-driven object carrying required-block-offset checks, an
 * NBT-rule-matched "what block/state should actually be placed here" indirection ({@code RulesLoader}/
 * {@code JsonRule}/{@code JsonSelector}), tile-entity NBT capture, and a rotation transform.
 *
 * <p><b>This is a deliberately simplified port for this round</b> (see PORTING.md's {@code buildcraft.builders}
 * entry): the palette element is a plain {@link BlockState} rather than an {@code ISchematicBlock}. That drops,
 * relative to the original: tile-entity NBT capture/restore (a captured chest builds back empty), the JSON rule
 * system entirely ({@code NbtPath}/{@code NbtRef}/{@code JsonRule}/{@code JsonSelector}/{@code RulesLoader} are
 * not ported), entity capture, and rotation (a blueprint always rebuilds in the orientation it was captured in --
 * see {@code TileBuilder}'s own javadoc). What is kept, faithfully: the palette-plus-flat-index-array shape
 * itself (unchanged from the original {@code Blueprint}), and per-block required-item computation falling back to
 * "one of whatever item this block drops as" -- exactly {@code RequiredExtractorItemFromBlock}, the original's
 * own always-present fallback extractor for a block with no rule-driven cost override.
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

    /** Captures every block state in the box {@code [min, min + size)}, deduplicating into {@link #palette} the
     * same way the original {@code TileArchitectTable#scanSingleBlock} built up its palette one block at a time. */
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

    /** One combined shopping list for the whole blueprint -- unlike the original's per-block
     * {@code toPlaceRequiredItems}, this is not recomputed as the build progresses (no fluid costs, no entity
     * costs to merge alongside it either), so a single up-front total is enough for this round's simplified
     * builder. A palette entry with no obtainable item (air, or a block whose item form is {@link Items#AIR}, e.g.
     * fluids) is skipped entirely -- treated as free, matching the original's own
     * {@code ItemStack::isEmpty}-filtered fallback. */
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
        blueprint.data = nbt.getIntArray("data").orElse(new int[0]);
        return blueprint;
    }

    public static HolderGetter<Block> blockLookup(Level level) {
        return level.registryAccess().lookupOrThrow(Registries.BLOCK);
    }

    @Nullable
    public static Blueprint readFromStack(ItemStack stack, Level level) {
        CompoundTag data = NBTUtilBC.getItemData(stack).getCompound("blueprint").orElse(null);
        return data == null ? null : deserializeNBT(data, blockLookup(level));
    }

    public static void writeToStack(ItemStack stack, Blueprint blueprint) {
        NBTUtilBC.updateItemData(stack, nbt -> nbt.put("blueprint", blueprint.serializeNBT()));
    }
}
