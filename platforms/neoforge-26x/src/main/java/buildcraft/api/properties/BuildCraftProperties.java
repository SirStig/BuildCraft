/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.properties;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import buildcraft.api.enums.EnumDecoratedBlock;
import buildcraft.api.enums.EnumEngineType;
import buildcraft.api.enums.EnumLaserTableType;
import buildcraft.api.enums.EnumMachineState;
import buildcraft.api.enums.EnumOptionalSnapshotType;
import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.enums.EnumSpring;

/**
 * Every blockstate property BuildCraft's blocks share.
 *
 * <p>The type names all moved: {@code IProperty} is {@link Property}, and {@code PropertyEnum}/{@code PropertyBool}/
 * {@code PropertyInteger} are {@link EnumProperty}/{@link BooleanProperty}/{@link IntegerProperty}. The enum
 * property factory now requires {@code StringRepresentable} rather than {@code IStringSerializable}, and the value
 * it serialises has to be lower case, which is why the property enums return a lower-cased name.
 *
 * <p>Note {@link #GENERIC_PIPE_DATA} was a stand-in for block metadata, which no longer exists. It is kept as an
 * ordinary property for now so the pipe port has somewhere to land, but pipes should end up with real properties
 * rather than a packed integer -- see the note on metadata in PORTING.md.
 */
public final class BuildCraftProperties {

    public static final EnumProperty<Direction> BLOCK_FACING =
        EnumProperty.create("facing", Direction.class, Direction.Plane.HORIZONTAL.stream().toList());
    public static final EnumProperty<Direction> BLOCK_FACING_6 =
        EnumProperty.create("facing", Direction.class);

    public static final EnumProperty<DyeColor> BLOCK_COLOR =
        EnumProperty.create("color", DyeColor.class);
    public static final EnumProperty<EnumSpring> SPRING_TYPE =
        EnumProperty.create("type", EnumSpring.class);
    public static final EnumProperty<EnumEngineType> ENGINE_TYPE =
        EnumProperty.create("type", EnumEngineType.class);
    public static final EnumProperty<EnumLaserTableType> LASER_TABLE_TYPE =
        EnumProperty.create("type", EnumLaserTableType.class);
    public static final EnumProperty<EnumMachineState> MACHINE_STATE =
        EnumProperty.create("state", EnumMachineState.class);
    public static final EnumProperty<EnumPowerStage> ENERGY_STAGE =
        EnumProperty.create("stage", EnumPowerStage.class);
    public static final EnumProperty<EnumOptionalSnapshotType> SNAPSHOT_TYPE =
        EnumProperty.create("snapshot_type", EnumOptionalSnapshotType.class);
    public static final EnumProperty<EnumDecoratedBlock> DECORATED_BLOCK =
        EnumProperty.create("decoration_type", EnumDecoratedBlock.class);

    public static final IntegerProperty GENERIC_PIPE_DATA = IntegerProperty.create("pipe_data", 0, 15);
    public static final IntegerProperty LED_POWER = IntegerProperty.create("led_power", 0, 3);

    public static final BooleanProperty JOINED_BELOW = BooleanProperty.create("joined_below");
    public static final BooleanProperty MOVING = BooleanProperty.create("moving");
    public static final BooleanProperty LED_DONE = BooleanProperty.create("led_done");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty VALID = BooleanProperty.create("valid");

    public static final BooleanProperty CONNECTED_UP = BooleanProperty.create("connected_up");
    public static final BooleanProperty CONNECTED_DOWN = BooleanProperty.create("connected_down");
    public static final BooleanProperty CONNECTED_EAST = BooleanProperty.create("connected_east");
    public static final BooleanProperty CONNECTED_WEST = BooleanProperty.create("connected_west");
    public static final BooleanProperty CONNECTED_NORTH = BooleanProperty.create("connected_north");
    public static final BooleanProperty CONNECTED_SOUTH = BooleanProperty.create("connected_south");

    public static final Map<Direction, BooleanProperty> CONNECTED_MAP;

    // ###############
    //
    // Block state setting flags
    //
    // ###############

    /*
     * 1.12.2 defined its own copies of these because vanilla did not name them. Vanilla does now, on Block, so
     * these are aliases rather than literals -- with one correction. BuildCraft's UPDATE_EVEN_CLIENT was
     * "4 + MARK_BLOCK_FOR_UPDATE" and documented as "mark for update even on a client world". Flag 4 has never
     * meant that: it was, and still is, "do not re-render", now spelled Block.UPDATE_INVISIBLE. The old comment
     * was wrong, so the constant is not carried over under a name that would keep the mistake alive. Anything
     * that used it wants UPDATE_CLIENTS.
     */

    public static final int UPDATE_NONE = Block.UPDATE_NONE;

    /**
     * Updates the neighbouring blocks that the new block is set. It also updates the comparator output of this
     * block.
     */
    public static final int UPDATE_NEIGHBOURS = Block.UPDATE_NEIGHBORS;

    /** Sends the change to every client tracking the chunk. */
    public static final int MARK_BLOCK_FOR_UPDATE = Block.UPDATE_CLIENTS;

    /** Suppresses the client-side re-render, for a change that cannot be seen. */
    public static final int SUPPRESS_RERENDER = Block.UPDATE_INVISIBLE;

    /** Does what both {@link #UPDATE_NEIGHBOURS} and {@link #MARK_BLOCK_FOR_UPDATE} do. */
    public static final int MARK_THIS_AND_NEIGHBOURS = Block.UPDATE_ALL;

    /** @deprecated Use {@link #MARK_THIS_AND_NEIGHBOURS}; vanilla's UPDATE_ALL is already both flags. */
    @Deprecated
    public static final int UPDATE_ALL = Block.UPDATE_ALL;

    static {
        Map<Direction, BooleanProperty> map = new EnumMap<>(Direction.class);
        map.put(Direction.DOWN, CONNECTED_DOWN);
        map.put(Direction.UP, CONNECTED_UP);
        map.put(Direction.EAST, CONNECTED_EAST);
        map.put(Direction.WEST, CONNECTED_WEST);
        map.put(Direction.NORTH, CONNECTED_NORTH);
        map.put(Direction.SOUTH, CONNECTED_SOUTH);
        CONNECTED_MAP = Map.copyOf(map);
    }

    /** Deactivate constructor */
    private BuildCraftProperties() {
    }

    /** The six connection properties in {@link Direction} order, for iterating without the map lookup. */
    public static List<BooleanProperty> connectedProperties() {
        return List.of(
            CONNECTED_DOWN, CONNECTED_UP, CONNECTED_NORTH, CONNECTED_SOUTH, CONNECTED_WEST, CONNECTED_EAST
        );
    }
}
