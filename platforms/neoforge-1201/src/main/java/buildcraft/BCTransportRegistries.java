/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

import buildcraft.api.transport.IInjectable;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFlowType;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.registry.BCRegistry;

import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.pipe.PipeRegistry;
import buildcraft.transport.pipe.behaviour.PipeBehaviourClay;
import buildcraft.transport.pipe.behaviour.PipeBehaviourCobble;
import buildcraft.transport.pipe.behaviour.PipeBehaviourGold;
import buildcraft.transport.pipe.behaviour.PipeBehaviourIron;
import buildcraft.transport.pipe.behaviour.PipeBehaviourQuartz;
import buildcraft.transport.pipe.behaviour.PipeBehaviourSandstone;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStone;
import buildcraft.transport.pipe.behaviour.PipeBehaviourVoid;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWood;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Registrations belonging to the old {@code buildcrafttransport} module -- the first ones, and the first pipe
 * batch. Mirrors {@code BCFactoryRegistries}/{@code BCEnergyRegistries}' exact established structure. See the
 * 26.x copy of this class for the full account of the one-block-many-items architecture, why
 * {@code canBeColoured} is {@code false} despite the real 1.12.2 cobblestone pipe being colourable, and why the
 * id/texture-prefix naming deliberately diverges from 1.12.2's own {@code "cobblestone_item"} -- unchanged here.
 *
 * <p>Unlike 26.x, 1.20.1 still has {@code ICapabilityProvider}, so {@code TilePipeHolder} exposes its own
 * capabilities directly (see that class's own javadoc) rather than through a
 * {@code RegisterCapabilitiesEvent}-registered lambda here. What this target still needs declaring, because
 * 1.20.1 lost {@code @CapabilityInject}, is the four {@link PipeApi} capability tokens themselves
 * ({@link #registerCapabilities}) -- matching {@code MjCapabilities}/{@code TilesAPI}'s own precedent.
 */
public final class BCTransportRegistries {

    private BCTransportRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    static {
        PipeApi.pipeRegistry = PipeRegistry.INSTANCE;
        PipeApi.flowItems = new PipeFlowType(PipeFlowItems::new, PipeFlowItems::new);
        PipeApi.flowFluids = new PipeFlowType(PipeFlowFluids::new, PipeFlowFluids::new);
    }

    /** The cobblestone pipe's own {@link PipeDefinition} -- see the 26.x copy of this class's own javadoc for
     * the {@code canBeColoured}/naming notes. */
    public static final PipeDefinition PIPE_COBBLESTONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("cobblestone")
        .logic(PipeBehaviourCobble::new, PipeBehaviourCobble::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The wooden pipe's own {@link PipeDefinition} -- see the 26.x copy of this class's own javadoc for the
     * full account of why no new block/tile code was needed for this, and why {@code canBeColoured} is
     * {@code false}. */
    public static final PipeDefinition PIPE_WOOD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("wood")
        .logic(PipeBehaviourWood::new, PipeBehaviourWood::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The stone pipe's own {@link PipeDefinition} -- see the 26.x copy of this class's own javadoc for the
     * full account of the speed-modifier constants and why {@code canBeColoured} is {@code false}. */
    public static final PipeDefinition PIPE_STONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("stone")
        .logic(PipeBehaviourStone::new, PipeBehaviourStone::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The sandstone pipe's own {@link PipeDefinition} -- the pipe-to-pipe-only, never-to-an-inventory
     * material (see {@link PipeBehaviourSandstone}'s own javadoc). */
    public static final PipeDefinition PIPE_SANDSTONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("sandstone")
        .logic(PipeBehaviourSandstone::new, PipeBehaviourSandstone::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The quartz pipe's own {@link PipeDefinition} -- the gentlest speed modifier of this batch. */
    public static final PipeDefinition PIPE_QUARTZ = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("quartz")
        .logic(PipeBehaviourQuartz::new, PipeBehaviourQuartz::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The golden pipe's own {@link PipeDefinition} -- the speed-boost material. See the 26.x copy of this class for the
     * real 1.12.2 builder call this mirrors and why {@code canBeColoured} is {@code false}. */
    public static final PipeDefinition PIPE_GOLD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("gold")
        .logic(PipeBehaviourGold::new, PipeBehaviourGold::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The iron pipe's own {@link PipeDefinition} -- the one-way, wrench-selected output valve. See the 26.x copy of this class for the
     * real 1.12.2 builder call this mirrors and why {@code canBeColoured} is {@code false}. */
    public static final PipeDefinition PIPE_IRON = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("iron")
        .logic(PipeBehaviourIron::new, PipeBehaviourIron::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The clay pipe's own {@link PipeDefinition} -- prefers inventories over pipes. See the 26.x copy of this class for the
     * real 1.12.2 builder call this mirrors and why {@code canBeColoured} is {@code false}. */
    public static final PipeDefinition PIPE_CLAY = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("clay")
        .logic(PipeBehaviourClay::new, PipeBehaviourClay::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The void pipe's own {@link PipeDefinition} -- destroys items. See the 26.x copy of this class for the
     * real 1.12.2 builder call this mirrors and why {@code canBeColoured} is {@code false}. */
    public static final PipeDefinition PIPE_VOID = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("void")
        .logic(PipeBehaviourVoid::new, PipeBehaviourVoid::new)
        .flowItem()
        .disableColouring()
        .define();

    // The fluid pipes -- see the 26.x copy of this class for the 1.12.2 builder calls these mirror. No capability
    // registration is needed on this target: TilePipeHolder#getCapability already falls through to
    // PipeFlowFluids#getCapability, which answers FLUID_HANDLER.

    public static final PipeDefinition PIPE_COBBLESTONE_FLUID =
        fluidPipe("cobblestone_fluid", PipeBehaviourCobble::new, PipeBehaviourCobble::new);
    public static final PipeDefinition PIPE_WOOD_FLUID =
        fluidPipe("wood_fluid", PipeBehaviourWood::new, PipeBehaviourWood::new);
    public static final PipeDefinition PIPE_STONE_FLUID =
        fluidPipe("stone_fluid", PipeBehaviourStone::new, PipeBehaviourStone::new);
    public static final PipeDefinition PIPE_SANDSTONE_FLUID =
        fluidPipe("sandstone_fluid", PipeBehaviourSandstone::new, PipeBehaviourSandstone::new);
    public static final PipeDefinition PIPE_QUARTZ_FLUID =
        fluidPipe("quartz_fluid", PipeBehaviourQuartz::new, PipeBehaviourQuartz::new);
    public static final PipeDefinition PIPE_GOLD_FLUID =
        fluidPipe("gold_fluid", PipeBehaviourGold::new, PipeBehaviourGold::new);
    public static final PipeDefinition PIPE_IRON_FLUID =
        fluidPipe("iron_fluid", PipeBehaviourIron::new, PipeBehaviourIron::new);
    public static final PipeDefinition PIPE_CLAY_FLUID =
        fluidPipe("clay_fluid", PipeBehaviourClay::new, PipeBehaviourClay::new);
    public static final PipeDefinition PIPE_VOID_FLUID =
        fluidPipe("void_fluid", PipeBehaviourVoid::new, PipeBehaviourVoid::new);

    /** 1.12.2's {@code BCTransportConfig#baseFlowRate} default (mB per tick). */
    private static final int BASE_FLOW_RATE = 10;

    /** 1.12.2's {@code BCTransportConfig} {@code fluidTransfer(...)} calls, verbatim -- see the 26.x copy. */
    static {
        fluidTransfer(PIPE_COBBLESTONE_FLUID, BASE_FLOW_RATE, 10);
        fluidTransfer(PIPE_WOOD_FLUID, BASE_FLOW_RATE, 10);
        fluidTransfer(PIPE_STONE_FLUID, BASE_FLOW_RATE * 2, 10);
        fluidTransfer(PIPE_SANDSTONE_FLUID, BASE_FLOW_RATE * 2, 10);
        fluidTransfer(PIPE_CLAY_FLUID, BASE_FLOW_RATE * 4, 10);
        fluidTransfer(PIPE_IRON_FLUID, BASE_FLOW_RATE * 4, 10);
        fluidTransfer(PIPE_QUARTZ_FLUID, BASE_FLOW_RATE * 4, 10);
        fluidTransfer(PIPE_GOLD_FLUID, BASE_FLOW_RATE * 8, 2);
        fluidTransfer(PIPE_VOID_FLUID, BASE_FLOW_RATE * 8, 10);
    }

    private static PipeDefinition fluidPipe(
        String id, PipeDefinition.IPipeCreator creator, PipeDefinition.IPipeLoader loader
    ) {
        return new PipeDefinition.PipeDefinitionBuilder()
            .idTexPrefix(id)
            .logic(creator, loader)
            .flowFluid()
            .disableColouring()
            .define();
    }

    private static void fluidTransfer(PipeDefinition def, int rate, int delay) {
        PipeApi.fluidTransferData.put(def, new PipeApi.FluidTransferInfo(rate, delay));
    }

    /** Default properties match what {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft
     * block -- see {@code BCFactoryRegistries#CHUTE}'s own javadoc. A plain full cube, matching
     * {@code BlockTank}/{@code BlockPump}'s own "no renderer yet" precedent. No {@code addBlockAndItem}: this
     * block's item is the pipe-specific {@link ItemPipeHolder} below. */
    public static final RegistryObject<BlockPipeHolder> PIPE_HOLDER = REGISTRY.addBlock(
        "pipe_holder",
        () -> new BlockPipeHolder(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(0.25F, 3.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
                .requiresCorrectToolForDrops()
        )
    );

    public static final RegistryObject<BlockEntityType<TilePipeHolder>> PIPE_HOLDER_TYPE =
        REGISTRY.addBlockEntity("pipe_holder", TilePipeHolder::new, PIPE_HOLDER);

    /** The one item registered in this batch: the cobblestone pipe's own placeable item, tagged with
     * {@link #PIPE_COBBLESTONE}. Referencing {@code PIPE_HOLDER.get()} inside this factory is safe for the same
     * reason it is on 26.x -- see that class's own javadoc. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_COBBLESTONE = REGISTRY.addItem(
        "pipe_item_cobblestone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_COBBLESTONE)
    );

    /** The wooden pipe's own placeable item, tagged with {@link #PIPE_WOOD}. No capability-registration changes
     * were needed for this on 1.20.1 -- {@code TilePipeHolder#getCapability} already falls through generically
     * to {@code pipe.getCapability}, which is how {@code PipeBehaviourWood}'s own {@code MjCapabilityHelper}
     * gets reached with zero changes here. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_WOOD = REGISTRY.addItem(
        "pipe_item_wood",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_WOOD)
    );

    /** The stone pipe's own placeable item, tagged with {@link #PIPE_STONE}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_STONE = REGISTRY.addItem(
        "pipe_item_stone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_STONE)
    );

    /** The sandstone pipe's own placeable item, tagged with {@link #PIPE_SANDSTONE}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_SANDSTONE = REGISTRY.addItem(
        "pipe_item_sandstone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_SANDSTONE)
    );

    /** The quartz pipe's own placeable item, tagged with {@link #PIPE_QUARTZ}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_QUARTZ = REGISTRY.addItem(
        "pipe_item_quartz",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_QUARTZ)
    );

    /** The golden pipe's own placeable item, tagged with {@link #PIPE_GOLD}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_GOLD = REGISTRY.addItem(
        "pipe_item_gold",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_GOLD)
    );

    /** The iron pipe's own placeable item, tagged with {@link #PIPE_IRON}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_IRON = REGISTRY.addItem(
        "pipe_item_iron",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_IRON)
    );

    /** The clay pipe's own placeable item, tagged with {@link #PIPE_CLAY}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_CLAY = REGISTRY.addItem(
        "pipe_item_clay",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_CLAY)
    );

    /** The void pipe's own placeable item, tagged with {@link #PIPE_VOID}. */
    public static final RegistryObject<ItemPipeHolder> PIPE_ITEM_VOID = REGISTRY.addItem(
        "pipe_item_void",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_VOID)
    );

    // The fluid pipes' placeable items -- "pipe_fluid_<material>", alongside the "pipe_item_<material>" ones.

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_COBBLESTONE = REGISTRY.addItem(
        "pipe_fluid_cobblestone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_COBBLESTONE_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_WOOD = REGISTRY.addItem(
        "pipe_fluid_wood",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_WOOD_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_STONE = REGISTRY.addItem(
        "pipe_fluid_stone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_STONE_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_SANDSTONE = REGISTRY.addItem(
        "pipe_fluid_sandstone",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_SANDSTONE_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_QUARTZ = REGISTRY.addItem(
        "pipe_fluid_quartz",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_QUARTZ_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_GOLD = REGISTRY.addItem(
        "pipe_fluid_gold",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_GOLD_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_IRON = REGISTRY.addItem(
        "pipe_fluid_iron",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_IRON_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_CLAY = REGISTRY.addItem(
        "pipe_fluid_clay",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_CLAY_FLUID)
    );

    public static final RegistryObject<ItemPipeHolder> PIPE_FLUID_VOID = REGISTRY.addItem(
        "pipe_fluid_void",
        () -> new ItemPipeHolder(PIPE_HOLDER.get(), new Item.Properties(), PIPE_VOID_FLUID)
    );

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCTransportRegistries::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IPipeHolder.class);
        event.register(IPipe.class);
        event.register(PipePluggable.class);
        event.register(IInjectable.class);
    }

    /** Looks up the placeable {@link ItemPipeHolder} for whatever {@link PipeDefinition} is actually stamped
     * onto a given {@code TilePipeHolder}'s own {@code Pipe} -- see the 26.x copy of this class's own javadoc
     * for the full account of why this fixes the {@code pipe_holder} loot table's real "always drops
     * cobblestone" bug, and why {@link PipeApi#pipeRegistry}'s existing {@code getItemForPipe} needs no new map
     * here. */
    @Nullable
    public static ItemPipeHolder getItemForPipe(PipeDefinition definition) {
        IItemPipe item = PipeApi.pipeRegistry.getItemForPipe(definition);
        return item instanceof ItemPipeHolder holder ? holder : null;
    }
}
