/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.DyeColor;

import buildcraft.api.enums.EnumPowerStage;

import buildcraft.lib.expression.DefaultContexts;
import buildcraft.lib.expression.FunctionContext;
import buildcraft.lib.expression.api.NodeType;
import buildcraft.lib.expression.api.NodeTypes;

/** A special class dedicated to adding support to minecraft-specific types to "buildcraft.lib.expression". This isn't
 * part of that package as then we can safely distribute it separately.
 *
 * <p>This is a partial port. 1.12.2's version also registered a {@code Controllable Mode} node type
 * ({@code buildcraft.api.tiles.IControllable.Mode}, not ported yet on this target) and two GUI node types
 * ({@code GuiPosition}/{@code GuiArea}, backed by {@code buildcraft.lib.gui.pos}, the GUI positioning package,
 * also not ported -- deferred with the rest of GUI, see PORTING.md's dependency ordering). Both pieces are
 * omitted here rather than guessed at; add them back once their backing types land. The 1.12.2 version also
 * wrapped the {@code Controllable Mode} registration in a try/catch working around an obfuscation-mapping bug
 * specific to 1.12.2's tooling ({@code BCLib.throwBadClass}) -- {@code buildcraft.lib.BCLib} is not ported
 * either, and the bug it worked around has no equivalent under Mojang mappings, so there is nothing left for
 * that workaround to do even once {@code IControllable} lands. */
public class ExpressionCompat {

    public static final FunctionContext RENDERING = DefaultContexts.RENDERING;

    // Minecraft Types
    public static final NodeType<Axis> ENUM_AXIS;
    public static final NodeType<Direction> ENUM_FACING;
    public static final NodeType<DyeColor> ENUM_DYE_COLOUR;

    // BuildCraft API types
    public static final NodeType<EnumPowerStage> ENUM_POWER_STAGE;

    static {
        ENUM_AXIS = new NodeType<>("Axis", Axis.X);
        NodeTypes.addType("Axis", ENUM_AXIS);
        for (Axis a : Axis.values()) {
            ENUM_AXIS.putConstant("" + a, a);
        }

        ENUM_FACING = new NodeType<>("Facing", Direction.UP);
        NodeTypes.addType("Facing", ENUM_FACING);
        ENUM_FACING.put_t_t("getOpposite", Direction::getOpposite);
        ENUM_FACING.put_t_o("getAxis", Axis.class, Direction::getAxis);
        ENUM_FACING.put_t_o("(string)", String.class, Direction::getName);
        for (Direction f : Direction.values()) {
            ENUM_FACING.putConstant("" + f, f);
        }

        ENUM_DYE_COLOUR = new NodeType<>("Dye Colour", DyeColor.WHITE);
        NodeTypes.addType("DyeColor", ENUM_DYE_COLOUR);
        NodeTypes.addType("DyeColour", ENUM_DYE_COLOUR);
        ENUM_DYE_COLOUR.put_t_l("to_argb", c -> 0xFF_00_00_00 | ColourUtil.getLightHex(c));
        ENUM_DYE_COLOUR.put_t_o("(string)", String.class, DyeColor::getName);
        for (DyeColor c : DyeColor.values()) {
            ENUM_DYE_COLOUR.putConstant("" + c, c);
        }

        ENUM_POWER_STAGE = new NodeType<>("Engine Power Stage", EnumPowerStage.BLUE);
        NodeTypes.addType("EnginePowerStage", ENUM_POWER_STAGE);
        ENUM_POWER_STAGE.put_t_o("(string)", String.class, EnumPowerStage::getSerializedName);
        for (EnumPowerStage stage : EnumPowerStage.VALUES) {
            ENUM_POWER_STAGE.putConstant("" + stage, stage);
        }

        RENDERING.put_s_l("convertColourToAbgr", ExpressionCompat::convertColourToAbgr);
        RENDERING.put_s_l("convertColourToArgb", ExpressionCompat::convertColourToArgb);
    }

    public static void setup() {
        // Just to call the above static initializer
    }

    private static long convertColourToAbgr(String c) {
        DyeColor colour = ColourUtil.parseColourOrNull(c);
        if (colour == null) return 0xFF_FF_FF_FF;
        return 0xFF_00_00_00 | ColourUtil.swapArgbToAbgr(ColourUtil.getLightHex(colour));
    }

    private static long convertColourToArgb(String c) {
        DyeColor colour = ColourUtil.parseColourOrNull(c);
        if (colour == null) return 0xFF_FF_FF_FF;
        return 0xFF_00_00_00 | ColourUtil.getLightHex(colour);
    }
}
