/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.blocks;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;

/**
 * Maps a block to the same block in another dye colour -- white wool to red wool, blue concrete to green concrete.
 *
 * <p>This exists because Forge's {@code Block.recolorBlock(world, pos, side, colour)} hook, which 1.12.2's paint
 * helper fell back on for every block it had no handler for, was removed and has no replacement on either of our
 * targets. Without something in its place BuildCraft's paintbrush would only work on BuildCraft's own blocks.
 *
 * <p>Colour families are discovered from the block registry by the naming convention vanilla uses for every one of
 * them: {@code <colour>_<suffix>}, as in {@code red_wool}, {@code light_blue_concrete_powder},
 * {@code magenta_glazed_terracotta}. A family is only accepted once at least two colours of it have been seen, so
 * a lone block that happens to start with a colour word -- {@code black_dye}, say -- never forms one. Modded blocks
 * following the same convention are picked up for free, which is roughly the set Forge's hook covered anyway.
 *
 * <p>Blocks whose colour is not in their id, such as beds and shulker boxes on some versions, are not found this
 * way. Those need an explicit {@link ICustomPaintHandler}.
 *
 * <p>The table is built once, lazily, on first use. That has to be after block registration, which is true for
 * anything driven by a player swinging a paintbrush.
 */
public final class DyedBlockVariants {

    /** Longest colour prefixes first, so {@code light_blue_} is tried before {@code blue_} would be. */
    private static final DyeColor[] BY_PREFIX_LENGTH = sortedByPrefixLength();

    @Nullable
    private static volatile Map<Block, EnumMap<DyeColor, Block>> families;

    private DyedBlockVariants() {
    }

    /**
     * @return The same block in {@code colour}, or null if this block is not part of a discovered colour family or
     *         that family has no block in that colour.
     */
    @Nullable
    public static Block recolour(Block block, DyeColor colour) {
        EnumMap<DyeColor, Block> family = build().get(block);
        if (family == null) {
            return null;
        }
        return family.get(colour);
    }

    /** @return True if {@code block} belongs to a colour family, whether or not it has the colour asked for. */
    public static boolean isDyeable(Block block) {
        return build().containsKey(block);
    }

    private static Map<Block, EnumMap<DyeColor, Block>> build() {
        Map<Block, EnumMap<DyeColor, Block>> built = families;
        if (built != null) {
            return built;
        }
        synchronized (DyedBlockVariants.class) {
            built = families;
            if (built != null) {
                return built;
            }

            // suffix -> (colour -> block), keyed within a namespace so two mods' "_wool" never merge.
            Map<String, EnumMap<DyeColor, Block>> bySuffix = new HashMap<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                String path = id.getPath();
                for (DyeColor colour : BY_PREFIX_LENGTH) {
                    String prefix = colour.getSerializedName().toLowerCase(Locale.ROOT) + "_";
                    if (!path.startsWith(prefix)) {
                        continue;
                    }
                    String suffix = id.getNamespace() + ":" + path.substring(prefix.length());
                    bySuffix.computeIfAbsent(suffix, key -> new EnumMap<>(DyeColor.class))
                        .putIfAbsent(colour, block);
                    break;
                }
            }

            Map<Block, EnumMap<DyeColor, Block>> result = new HashMap<>();
            for (EnumMap<DyeColor, Block> family : bySuffix.values()) {
                if (family.size() < 2) {
                    // A single match is a coincidence of naming, not a colour family.
                    continue;
                }
                for (Block member : family.values()) {
                    result.put(member, family);
                }
            }

            families = result;
            return result;
        }
    }

    private static DyeColor[] sortedByPrefixLength() {
        DyeColor[] colours = DyeColor.values().clone();
        java.util.Arrays.sort(
            colours,
            (a, b) -> Integer.compare(b.getSerializedName().length(), a.getSerializedName().length())
        );
        return colours;
    }
}
