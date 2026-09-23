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
import net.minecraft.resources.Identifier;
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
 * A captured rectangular region of blocks, ready to be rebuilt elsewhere by a {@link buildcraft.builders.tile.
 * TileBuilder}. The 1.12.2/{@code common} original ({@code buildcraft.builders.snapshot.Blueprint}) stored a
 * palette of {@code ISchematicBlock} -- a full rule-driven object carrying required-block-offset checks, an
 * NBT-rule-matched "what block/state should actually be placed here" indirection ({@code RulesLoader}/
 * {@code JsonRule}/{@code JsonSelector}), tile-entity NBT capture, and a rotation transform.
 *
 * <p><b>This is a deliberately simplified port</b> (see PORTING.md's {@code buildcraft.builders} entry): the
 * palette element is a plain {@link BlockState} rather than an {@code ISchematicBlock}. That still drops,
 * relative to the original: entity capture/spawning, fluid capture (see {@code TileBuilder}'s own javadoc for
 * why -- both need the unported {@code FakeWorld}/entity-schematic machinery to be correct rather than merely
 * approximate), and the JSON rule system's larger features (required-item overrides, block substitution on
 * placement, dependency ordering via {@code requiredBlockOffsets}, ignored-property matching -- see
 * {@code RulesLoader}/{@code JsonRule}/{@code JsonSelector}, not ported).
 *
 * <p><b>Landed this round, real:</b>
 * <ul>
 * <li><b>Rotation</b>: {@link #facing} records the direction {@code TileArchitectTable} itself faced at capture
 * time, exactly like the original {@code Snapshot.facing}. {@code TileBuilder} compares this against its own
 * facing to compute a {@link net.minecraft.world.level.block.Rotation}, the same {@code Rotation.values()}
 * lookup as the original {@code common} {@code TileBuilder#updateSnapshot}, and {@link BlueprintBuilder} applies
 * it to both the target {@link BlockState} ({@link BlockState#rotate}, the modern equivalent of the original's
 * {@code IBlockState#withRotation}) and the placement position ({@link BlockPos#rotate}) at build time -- the
 * palette/data themselves stay unrotated, matching the original's own "rotate lazily in {@code BuildingInfo}"
 * design rather than baking rotation into the captured data.</li>
 * <li><b>Tile-entity NBT capture/restore</b>: {@link #tileData} captures each position's {@link BlockEntity} data
 * (custom-only, no id/position metadata) keyed by the same flat index as {@link #data}, and {@link
 * BlueprintBuilder} merges it into the freshly placed block's own block entity. This is a verbatim capture/
 * restore -- unlike the original, no NBT field is rotated or rewritten (the original's {@code JsonRule#replaceNbt}/
 * {@code NbtRef} indirection is not ported), so a block entity whose saved data itself encodes a direction (rare)
 * will not be re-oriented to match a rotated rebuild.</li>
 * <li><b>A minimal, real slice of the JSON rule system</b>: {@link #IGNORED}, a block tag capturing blocks in it
 * as air rather than themselves, ported directly from 1.12.2's own default rule file ({@code
 * buildcraft_resources/assets/buildcraftbuilders/compat/buildcraft/builders/vanilla/ignored.json}, which lists
 * exactly {@code minecraft:fire}/{@code minecraft:ender_chest}) rather than the general {@code RulesLoader}/
 * {@code JsonRule} engine that file was loaded through.</li>
 * </ul>
 */
public class Blueprint {
    /** Mirrors 1.12.2's own default blueprint rule (see this class's javadoc) as a plain block tag rather than
     * the unported {@code RulesLoader}/{@code JsonRule} engine -- see {@code data/buildcraft/tags/block(s)/
     * blueprint_ignore.json} on this platform for the actual block list. */
    public static final TagKey<Block> IGNORED =
        TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(BuildCraft.MOD_ID, "blueprint_ignore"));

    public BlockPos size = BlockPos.ZERO;
    public final List<BlockState> palette = new ArrayList<>();
    public int[] data = new int[0];
    /** The direction {@code TileArchitectTable} faced when it captured this blueprint -- see this class's own
     * javadoc for how {@code TileBuilder}/{@link BlueprintBuilder} use it to rotate a rebuild. */
    public Direction facing = Direction.NORTH;
    /** Captured {@link BlockEntity} data, keyed by the same flat index as {@link #data} -- see this class's own
     * javadoc. Empty for a blueprint with no captured tile entities (including every blueprint captured before
     * this field existed, via {@link #deserializeNBT}'s missing-key default). */
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
     * as seen from {@code facing}, deduplicating states into {@link #palette} the same way the original
     * {@code TileArchitectTable#scanSingleBlock} built up its palette one block at a time. A block in {@link
     * #IGNORED} is captured as air, matching {@code JsonRule#ignore}. */
    public static Blueprint capture(Level level, BlockPos min, BlockPos size, Direction facing) {
        Blueprint blueprint = new Blueprint();
        blueprint.size = size;
        blueprint.facing = facing;
        blueprint.data = new int[size.getX() * size.getY() * size.getZ()];
        for (int z = 0; z < size.getZ(); z++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) {
                    BlockPos worldPos = min.offset(x, y, z);
                    // BlockStateBase only exposes the (tag, predicate) overload on this target, not a plain
                    // (tag) one -- an always-true predicate is the equivalent of a plain tag check.
                    BlockState state = level.getBlockState(worldPos).is(IGNORED, s -> true)
                        ? Blocks.AIR.defaultBlockState()
                        : level.getBlockState(worldPos);
                    if (!state.isAir()) {
                        BlockEntity blockEntity = level.getBlockEntity(worldPos);
                        if (blockEntity != null) {
                            CompoundTag tileNbt = blockEntity.saveCustomOnly(level.registryAccess());
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
        blueprint.data = nbt.getIntArray("data").orElse(new int[0]);
        Direction facing = Direction.byName(nbt.getStringOr("facing", "north"));
        blueprint.facing = facing == null ? Direction.NORTH : facing;
        CompoundTag tileDataTag = nbt.getCompoundOrEmpty("tileData");
        for (String key : tileDataTag.keySet()) {
            try {
                blueprint.tileData.put(Integer.parseInt(key), tileDataTag.getCompoundOrEmpty(key));
            } catch (NumberFormatException ignored) {
                // Not one of ours -- skip rather than fail the whole blueprint load.
            }
        }
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
