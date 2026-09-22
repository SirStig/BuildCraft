/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import buildcraft.api.mj.MjCapabilities;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFlowType;

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
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Registrations belonging to the old {@code buildcrafttransport} module -- the first ones, and the first pipe
 * batch. Mirrors {@code BCFactoryRegistries}/{@code BCEnergyRegistries}' exact established structure: its own
 * private {@link BCRegistry}, block/item/block-entity registration, a {@code registerCapabilities}.
 *
 * <p><b>The one-block-many-items architecture, made concrete.</b> There is exactly one block
 * ({@link #PIPE_HOLDER}) and one block entity type ({@link #PIPE_HOLDER_TYPE}) here, matching 1.12.2's own real
 * design (see {@code common/buildcraft/transport/pipe/PipeRegistry.java}/{@code Pipe.java}) -- a future pipe
 * material is just another {@link PipeDefinition} plus another {@link ItemPipeHolder} instance registered here,
 * never a new block or tile class.
 *
 * <p>Static init order matters in this class and is deliberate: {@link #PIPE_API_INIT} (a static initializer
 * block, not a field) wires {@link PipeApi#pipeRegistry}/{@link PipeApi#flowItems} <em>before</em>
 * {@link #PIPE_COBBLESTONE} is built, because {@code PipeDefinition.PipeDefinitionBuilder#define()} calls
 * {@code PipeApi.pipeRegistry.registerPipe(...)} immediately, and {@code .flowItem()} reads
 * {@code PipeApi.flowItems} immediately -- both would NPE if this ran in the other order. Static fields and
 * blocks run top-to-bottom in one class, so the ordering below is load-bearing, not decorative.
 *
 * <p><b>{@code canBeColoured} is {@code false} on {@link #PIPE_COBBLESTONE}, a deliberate scope choice, not a
 * faithful copy of the original's own value.</b> Verified, not guessed: 1.12.2's real
 * {@code common/buildcraft/transport/BCTransportPipes.java} calls {@code builder.builder.enableColouring()}
 * once, right after defining the structure pipe, and that flag then stays set on the shared builder for every
 * pipe defined afterwards -- wood, stone, cobblestone, quartz, gold, and so on -- so the real 1.12.2 cobblestone
 * pipe <em>is</em> colourable. This batch disables it anyway: colouring a pipe means applying a dye, which needs
 * {@code CustomPaintHelper}-style GUI/interaction plumbing this batch does not add for pipes specifically (dye
 * colours on a pipe are explicitly out of scope -- see this module's own scope notes), so leaving
 * {@code canBeColoured} true would advertise a feature ({@code IPipe#setColour}) nothing in this batch can ever
 * legitimately trigger.
 *
 * <p>The id/texture-prefix naming also deliberately diverges from 1.12.2's own {@code "cobblestone_item"} (which
 * needed the {@code _item} suffix to stay unique alongside sibling {@code cobblestone_fluid}/
 * {@code cobblestone_power}/{@code cobblestone_rf} definitions sharing the same material prefix). This batch
 * registers exactly one cobblestone pipe kind, so the plain {@code "cobblestone"} id has nothing to collide
 * with.
 */
public final class BCTransportRegistries {

    private BCTransportRegistries() {}

    private static final BCRegistry REGISTRY = new BCRegistry(BuildCraft.MOD_ID);

    static {
        PipeApi.pipeRegistry = PipeRegistry.INSTANCE;
        PipeApi.flowItems = new PipeFlowType(PipeFlowItems::new, PipeFlowItems::new);
    }

    /** The cobblestone pipe's own {@link PipeDefinition} -- see this class's own javadoc for the
     * {@code canBeColoured}/naming notes. */
    public static final PipeDefinition PIPE_COBBLESTONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("cobblestone")
        .logic(PipeBehaviourCobble::new, PipeBehaviourCobble::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The wooden pipe's own {@link PipeDefinition} -- this batch's proof of this class's own "a future pipe
     * material is just another {@code PipeDefinition} plus another {@code ItemPipeHolder} instance" claim above.
     * {@code canBeColoured} is {@code false} for the identical reason {@link #PIPE_COBBLESTONE} already gives. */
    public static final PipeDefinition PIPE_WOOD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("wood")
        .logic(PipeBehaviourWood::new, PipeBehaviourWood::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The stone pipe's own {@link PipeDefinition} -- a fast, constant-crawl speed modifier
     * ({@code PipeBehaviourStone.SPEED_DELTA} of {@code 0.008}). {@code canBeColoured} is {@code false} for the
     * identical reason {@link #PIPE_COBBLESTONE} already gives -- confirmed against the real 1.12.2
     * {@code BCTransportPipes#preInit} that the shared builder's {@code enableColouring()} call (set once,
     * before wood, and never unset) would otherwise have made the real stone pipe colourable too. */
    public static final PipeDefinition PIPE_STONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("stone")
        .logic(PipeBehaviourStone::new, PipeBehaviourStone::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The sandstone pipe's own {@link PipeDefinition} -- the pipe-to-pipe-only, never-to-an-inventory
     * material (see {@link PipeBehaviourSandstone}'s own javadoc). Same speed modifier as {@link #PIPE_STONE}
     * (its behaviour reuses {@code PipeBehaviourStone}'s own constants directly). {@code canBeColoured} is
     * {@code false} for the same reason as every other material in this batch. */
    public static final PipeDefinition PIPE_SANDSTONE = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("sandstone")
        .logic(PipeBehaviourSandstone::new, PipeBehaviourSandstone::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The quartz pipe's own {@link PipeDefinition} -- the gentlest speed modifier of this batch
     * ({@code PipeBehaviourQuartz.SPEED_DELTA} of {@code 0.002}). {@code canBeColoured} is {@code false} for
     * the same reason as every other material in this batch. */
    public static final PipeDefinition PIPE_QUARTZ = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("quartz")
        .logic(PipeBehaviourQuartz::new, PipeBehaviourQuartz::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The golden pipe's own {@link PipeDefinition} -- the speed-boost material (see {@link PipeBehaviourGold}).
     * 1.12.2's real builder call is a plain {@code builder.idTex("gold_item").flowItem().define()} after
     * {@code logic(PipeBehaviourGold::new, ...)}: no texture suffixes, default item texture -- so this is the same
     * shape as every other single-texture material here. {@code canBeColoured} is {@code false} for the same
     * reason as every other material (the real 1.12.2 golden pipe inherits the shared builder's
     * {@code enableColouring()} and <em>is</em> colourable -- see this class's own javadoc). */
    public static final PipeDefinition PIPE_GOLD = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("gold")
        .logic(PipeBehaviourGold::new, PipeBehaviourGold::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The iron pipe's own {@link PipeDefinition} -- the one-way, wrench-selected output valve (see
     * {@link PipeBehaviourIron}). 1.12.2's real builder call is wood's exact shape:
     * {@code texSuffixes("_clear", "_filled")} plus {@code itemTex(0, 0, 1)}, then
     * {@code idTexPrefix("iron_item")}. The two-texture split is reproduced by the {@code active} blockstate
     * property and the {@code pipe_holder_arm_iron}/{@code pipe_holder_arm_iron_filled} model pair rather than
     * by {@code PipeDefinition}'s own texture-suffix fields, which nothing in this port reads (the same reason
     * {@link #PIPE_WOOD} never set them either). {@code canBeColoured} is {@code false} for the same reason as
     * every other material. */
    public static final PipeDefinition PIPE_IRON = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("iron")
        .logic(PipeBehaviourIron::new, PipeBehaviourIron::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The clay pipe's own {@link PipeDefinition} -- prefers inventories over pipes (see
     * {@link PipeBehaviourClay}). 1.12.2: {@code builder.idTex("clay_item").flowItem().define()}, no suffixes.
     * {@code canBeColoured} is {@code false} for the same reason as every other material. */
    public static final PipeDefinition PIPE_CLAY = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("clay")
        .logic(PipeBehaviourClay::new, PipeBehaviourClay::new)
        .flowItem()
        .disableColouring()
        .define();

    /** The void pipe's own {@link PipeDefinition} -- destroys items (see {@link PipeBehaviourVoid}). 1.12.2:
     * {@code builder.idTex("void_item").flowItem().define()}, no suffixes; its {@code void_fluid} sibling is not
     * registered, since no fluid flow exists in this port. {@code canBeColoured} is {@code false} for the same
     * reason as every other material. */
    public static final PipeDefinition PIPE_VOID = new PipeDefinition.PipeDefinitionBuilder()
        .idTexPrefix("void")
        .logic(PipeBehaviourVoid::new, PipeBehaviourVoid::new)
        .flowItem()
        .disableColouring()
        .define();

    /** Default properties match what {@code BlockBCTile_Neptune}'s constructor gave every 1.12.2 BuildCraft
     * block, the same reasoning already worked out for {@code BlockDecoration}/{@code BlockEngineWood}/
     * {@code BlockChute} -- see {@code BCFactoryRegistries#CHUTE}'s own javadoc. A plain full cube, matching
     * {@code BlockTank}/{@code BlockPump}'s own "no renderer yet" precedent -- see {@link BlockPipeHolder}'s own
     * javadoc. No {@code addBlockAndItem}: this block's item is the pipe-specific {@link ItemPipeHolder} below,
     * not an auto-generated plain {@code BlockItem}. */
    public static final DeferredBlock<BlockPipeHolder> PIPE_HOLDER = REGISTRY.addBlock(
        "pipe_holder", BlockPipeHolder::new,
        properties -> properties
            .mapColor(MapColor.STONE)
            .strength(0.25F, 3.0F)
            .sound(SoundType.STONE)
            .noOcclusion()
            .requiresCorrectToolForDrops()
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TilePipeHolder>> PIPE_HOLDER_TYPE =
        REGISTRY.addBlockEntity("pipe_holder", TilePipeHolder::new, PIPE_HOLDER);

    /** The one item registered in this batch: the cobblestone pipe's own placeable item, tagged with
     * {@link #PIPE_COBBLESTONE}. Referencing {@code PIPE_HOLDER.get()} inside this factory is safe despite
     * running before {@link #PIPE_HOLDER} is actually bound: {@link DeferredRegister.Items}' own factory is not
     * invoked until the item registry event fires, by which point the block registry (registered first, same
     * pattern {@code BCRegistry#addBlockAndItem}'s own {@code registerSimpleBlockItem} already relies on) has
     * already bound every {@code DeferredBlock}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_COBBLESTONE = REGISTRY.addItem(
        "pipe_item_cobblestone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_COBBLESTONE)
    );

    /** The wooden pipe's own placeable item, tagged with {@link #PIPE_WOOD}. No new block/tile code was needed
     * for this -- see this class's own javadoc. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_WOOD = REGISTRY.addItem(
        "pipe_item_wood",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_WOOD)
    );

    /** The stone pipe's own placeable item, tagged with {@link #PIPE_STONE}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_STONE = REGISTRY.addItem(
        "pipe_item_stone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_STONE)
    );

    /** The sandstone pipe's own placeable item, tagged with {@link #PIPE_SANDSTONE}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_SANDSTONE = REGISTRY.addItem(
        "pipe_item_sandstone",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_SANDSTONE)
    );

    /** The quartz pipe's own placeable item, tagged with {@link #PIPE_QUARTZ}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_QUARTZ = REGISTRY.addItem(
        "pipe_item_quartz",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_QUARTZ)
    );

    /** The golden pipe's own placeable item, tagged with {@link #PIPE_GOLD}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_GOLD = REGISTRY.addItem(
        "pipe_item_gold",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_GOLD)
    );

    /** The iron pipe's own placeable item, tagged with {@link #PIPE_IRON}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_IRON = REGISTRY.addItem(
        "pipe_item_iron",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_IRON)
    );

    /** The clay pipe's own placeable item, tagged with {@link #PIPE_CLAY}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_CLAY = REGISTRY.addItem(
        "pipe_item_clay",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_CLAY)
    );

    /** The void pipe's own placeable item, tagged with {@link #PIPE_VOID}. */
    public static final DeferredItem<ItemPipeHolder> PIPE_ITEM_VOID = REGISTRY.addItem(
        "pipe_item_void",
        properties -> new ItemPipeHolder(PIPE_HOLDER.get(), properties, PIPE_VOID)
    );

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
        modBus.addListener(BCTransportRegistries::registerCapabilities);
    }

    /** Looks up the placeable {@link ItemPipeHolder} for whatever {@link PipeDefinition} is actually stamped
     * onto a given {@code TilePipeHolder}'s own {@code Pipe} -- used by {@link BlockPipeHolder#getDrops} to
     * drop the correct material instead of the static {@code pipe_holder} loot table's single hard-coded
     * {@code pipe_item_cobblestone} entry (a real bug, on file in PORTING.md since the wood-pipe batch, now
     * fixed because a third material -- and a fourth, fifth, sixth here -- makes it actually wrong in practice,
     * not just theoretically). Delegates to {@link PipeApi#pipeRegistry}'s own {@code getItemForPipe}, which
     * every {@link ItemPipeHolder} already self-registers into from its own constructor -- no separate map
     * needed here, since {@code PipeRegistry} already keeps exactly this association for every registered
     * pipe material, present and future, with zero extra bookkeeping in this class. */
    @Nullable
    public static ItemPipeHolder getItemForPipe(PipeDefinition definition) {
        IItemPipe item = PipeApi.pipeRegistry.getItemForPipe(definition);
        return item instanceof ItemPipeHolder holder ? holder : null;
    }

    /** Wires the vanilla-interop item capability so a neighbouring machine (a real hopper, an already-ported
     * chute, ...) can push/pull items through the pipe's own ends -- matching
     * {@code BCFactoryRegistries#registerCapabilities}'s own {@code CHUTE_TYPE} precedent. The actual
     * {@code ResourceHandler<ItemResource>} implementation lives on {@code PipeFlowItems} itself (see that
     * class's own {@code getCapability} javadoc for why), reached generically through
     * {@code TilePipeHolder#getCapability} -> {@code Pipe#getCapability} -> {@code PipeBehaviour}/
     * {@code PipeFlow#getCapability}, the same dispatch chain {@link buildcraft.api.transport.pipe.IPipe}'s own
     * javadoc describes. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Item.BLOCK, PIPE_HOLDER_TYPE.get(),
            (tile, side) -> tile.getCapability(Capabilities.Item.BLOCK, side)
        );

        // PipeApi.CAP_PIPE_HOLDER/CAP_PIPE/CAP_PLUG expose the tile/pipe/pluggable objects themselves -- this
        // is what IPipe#getConnectedPipe/IPipeHolder#getNeighbourPipe (via Level#getCapability) actually rely
        // on to detect a *neighbouring pipe* as a pipe (as opposed to a plain tile), which in turn is what makes
        // Pipe#canPipesConnect ever get a chance to run between two adjacent pipe segments at all. Direct
        // 26.x-shaped equivalent of 1.12.2's own TilePipeHolder constructor
        // (caps.addCapabilityInstance(CAP_PIPE_HOLDER, this, ...); caps.addCapability(CAP_PIPE, this::getPipe,
        // ...); caps.addCapability(CAP_PLUG, this::getPluggable, ...)).
        event.registerBlockEntity(PipeApi.CAP_PIPE_HOLDER, PIPE_HOLDER_TYPE.get(), (tile, side) -> tile);
        event.registerBlockEntity(PipeApi.CAP_PIPE, PIPE_HOLDER_TYPE.get(), (tile, side) -> tile.getPipe());
        event.registerBlockEntity(PipeApi.CAP_PLUG, PIPE_HOLDER_TYPE.get(), (tile, side) -> tile.getPluggable(side));

        // The wooden pipe's own MJ capabilities -- see PipeBehaviourWood's own javadoc for why this batch has to
        // register these here by hand rather than through MjCapabilityHelper.registerAll (which is keyed by
        // BlockEntityType and cannot distinguish "this particular pipe happens to be wood" on the one shared
        // TilePipeHolder type). Registered unconditionally on the shared tile type, the same as the item
        // capability above -- TilePipeHolder#getCapability already falls through to null for every other pipe
        // material's behaviour, exactly like it does for a capability token no behaviour/flow answers at all.
        event.registerBlockEntity(
            MjCapabilities.CONNECTOR, PIPE_HOLDER_TYPE.get(),
            (tile, side) -> tile.getCapability(MjCapabilities.CONNECTOR, side)
        );
        event.registerBlockEntity(
            MjCapabilities.RECEIVER, PIPE_HOLDER_TYPE.get(),
            (tile, side) -> tile.getCapability(MjCapabilities.RECEIVER, side)
        );
        event.registerBlockEntity(
            MjCapabilities.REDSTONE_RECEIVER, PIPE_HOLDER_TYPE.get(),
            (tile, side) -> tile.getCapability(MjCapabilities.REDSTONE_RECEIVER, side)
        );
    }
}
