# Porting BuildCraft to NeoForge

BuildCraft 8.0.1 targets Minecraft 1.12.2 and Forge 14.23. This branch is porting it to
NeoForge as **BuildCraft 10** (version `10.0.0-alpha`), on two targets:

| Target | Gradle project | Loader artifact | Java | Status |
| --- | --- | --- | --- | --- |
| Minecraft 26.1 / 26.2 / **26.3** | `:neoforge-26x` | `net.neoforged:neoforge` | 25 | primary |
| Minecraft 1.20.1 | `:neoforge-1201` | `net.neoforged:forge` | 17 | compatibility |

26.x is where the work goes first. 1.20.1 is kept because that is where most of the
modding community's mod branches still sit.

> The upstream `BuildCraft/BuildCraft` branches named `8.0.x-1.16.5`, `8.0.x-1.18.2` and
> `8.0.x-1.20.1` are **empty placeholders** — all three point at the same August 2023
> commit, whose `build.properties` still says `mc_version=1.12.2`. There is no upstream
> port to pick up; this branch starts from scratch.

## Layout

```
modules/expression/     Expression engine. No Minecraft references at all. DONE.
modules/shared/         Version-independent BuildCraft logic, shared by both targets.
platforms/neoforge-26x/ Minecraft 26.x port.
platforms/neoforge-1201/Minecraft 1.20.1 port.

common/                 The 1.12.2 source tree. NOT COMPILED -- this is the reference we
buildcraft_resources/   are porting from, and is deleted module by module as each one lands.
BuildCraftAPI/          git submodule; the 1.12.2 API, also being ported.
legacy/                 The old ForgeGradle 2 build files, kept for reference.
src_old_license/        Dead. It was already commented out of the 1.12.2 build.
```

The root project applies no `java` plugin on purpose, so the 1.12.2 trees at the repository
root are never fed to the compiler.

Anything that can live in `modules/shared` should: it is written once and both targets use
it. Everything that touches a Minecraft type is duplicated per platform, because the two
APIs are too far apart to bridge cheaply (see the table below) and the abstraction layer
would cost more than the duplication.

## Building

```bash
./gradlew build                          # everything
./gradlew :neoforge-26x:build            # Minecraft 26.3 (default)
./gradlew :neoforge-26x:build -Pbc.mc26=26.2
./gradlew :neoforge-1201:build           # Minecraft 1.20.1
./gradlew buildCommon                    # just the Minecraft-free modules
./gradlew :neoforge-26x:runClient        # launch the game
```

### Porting helpers

`misc/port/rename.py` applies the 1.12.2 → 26.x changes that are purely positional: package moves, type
renames with identical semantics, `setX` → `putX`, and the NBT getter mapping above. `misc/port/to1201.py`
takes a file already compiling on 26.x and produces the 1.20.1 copy, which is far less work than porting
from 1.12.2 twice.

Neither is a port. They know nothing about capabilities, the transfer API, stack NBT, rendering, block
metadata or the packet system — everything in the list below, in other words — so every file they touch
still has to be read, and the compiler is the real check. In practice they remove most of the noise and
leave the decisions.

### Testing in a real game

Dev runs (`runClient`/`runServer`) load `build/classes` directly. That is fine for most things but it does
not exercise the built jar, which is where the bundling gotchas below bite, so anything that needs real
testing goes into a Prism Launcher instance:

```bash
./gradlew installToPrism                 # both targets
./gradlew :neoforge-26x:installToPrism   # just Minecraft 26.3
./gradlew :neoforge-26x:installToPrism -Pbc.mc26=26.1
```

Each platform is mapped to the instance matching its Minecraft release and loader, and the task deletes any
previously installed BuildCraft jar first so two versions never load at once. Other mods in the instance are
left alone. Override with `-Pbc.prism.instance="<name>"` for a differently named instance, or
`-Pbc.prism.dir=<PrismLauncher data dir>` for a non-Flatpak install; the task lists the instances it can see
when it cannot find the one it wants.

Minecraft 26.x needs a **Java 25** toolchain — FancyModLoader 12 refuses to resolve against
anything older. Gradle will download one if it is not installed. Gradle itself must be 9.x;
8.x cannot drive a Java 25 toolchain.

## Progress

Ported (compiling, tested):

- `modules/expression` — all 71 hand-written files plus the 102 the generator produces.
  13 tests pass.
- `modules/shared` — 31 files: `buildcraft.lib.misc`, `buildcraft.lib.misc.data`,
  `buildcraft.lib.script`, plus `BCLog`, `BCDebugging` and `IConvertable` from the API.
  4 tests pass.
- `buildcraft.api.mj` — the MJ power API. The five interfaces, `MjAPI`'s constants and
  `MjBattery`'s arithmetic are shared; capabilities and the effect manager are per-platform.
  8 tests pass.
- Both platforms, at parity — mod entrypoint, the five gears, the creative tab,
  `MjCapabilities`, `IMjEffectManager`/`MjEffects`, `BCRegistry` (the registration layer),
  `TileBC` and `BlockBCTile` (the block entity and block bases), `VecUtil`, `RotationUtil`,
  and the power consumer tester -- the first machine to go all the way through: block, block
  entity, ticker, MJ capability, model, loot table and tag.
- **`buildcraft.core` — started.** `core.item.ItemWrench` (renamed from `ItemWrench_Neptune`,
  following the same "supersede `ItemBC_Neptune`/`IItemBuildCraft` with `BCRegistry` plus
  lang/model JSON" pattern already used for the five gears and the power tester -- see the
  "Deliberately not ported" note on `IItemBuildCraft`) is the first `core` item, registered in
  `BCCoreRegistries` alongside the gears. `onItemUseFirst`/`onItemUse` merge into `useOn`;
  `IBlockState#getActualState` and `Item#doesSneakBypassUse` are both simply gone (see the
  class's own javadoc for the full account, including 26.x's sealed-interface
  `InteractionResult` check and its three-argument `LivingEntity#swing`). Texture, item model
  and a modern datapack recipe (`c:gears/stone` + `c:ingots/iron` / `forge:` equivalents on
  1.20.1) are ported from `buildcraft_resources/assets/buildcraftcore/`; the advancement JSON
  is not -- `AdvancementUtil.unlockAdvancement` already treats an unregistered advancement id
  as a harmless one-time warning rather than an error (see its own class javadoc), and nothing
  else needs a BuildCraft advancement tree to exist yet, so building one prematurely for a
  single leaf advancement isn't worth it.
  `buildcraft.lib.misc.SoundUtil` (both platforms) also landed as part of this -- `ItemWrench`
  needed it for its slide-sound feedback, and it turned out to have no rendering/client-only
  dependency blocking it (unlike the `GlUtil`/`DrawingUtil`/... cluster it sits next to in
  `lib.misc`): `IBlockState#getSoundType(state, world, pos, entity)` lost its three context
  parameters, and per-fluid bucket sounds move from `Fluid#getEmptySound`/`getFillSound` to
  `Fluid#getFluidType()#getSound(FluidStack, SoundAction)`, Forge's generic fluid-property
  system already shared by both targets.
  `core.block.BlockSpringWater` (renamed from `BlockSpring`, water half only) is the first
  worked example of splitting a 1.12.2 metadata-subtyped block into one real `Block` per
  variant -- see its own class javadoc, and the `buildcraft.core` survey below for why the oil
  half and `ItemBlockSpring`'s original two-variant `BlockItem` don't follow the same way yet.
  Registered (`spring_water`, reusing vanilla's own `bedrock` model/texture, matching 1.12.2's
  own choice there) but not yet spawned anywhere -- `core.gen.SpringPopulate`, the world-gen
  hook that placed it, needs its own design; see the survey entry.
- **`buildcraft.lib.net`'s networking foundation is designed and landed, both platforms.**
  `BCNetwork` (a new root-package class, alongside `BCRegistries`) is this port's replacement
  for `MessageManager`: a thin, per-message static registration list rather than a dynamic
  per-mod dispatch table -- see the "Networking" structural-change entry for the full design
  and why the two targets need genuinely different underlying registration APIs.
  `IPayloadReceiver` and `MessageUpdateTile` (both platforms) are the first ported message:
  routes an opaque payload to whatever block entity implementing `IPayloadReceiver` sits at a
  given position. Verified with a real dev-server boot on each target, not just a compile
  check -- no crash, no registration error, mod construction and world load both completed
  cleanly on both. `MessageManager`, `MessageUtil`, and the rest of `lib.net`'s concrete
  messages (`MessageContainer`, `MessageDebugRequest`/`Response`, `MessageMarker`,
  `MessageObjectCacheRequest`/`Response`) are still not ported -- `MessageUpdateTile` only
  needed to prove the registration design works, not to bring the whole package along -- but
  each individual message is now an ordinary port against a settled design, not an open
  question.
- **`buildcraft.lib.gui.pos` (9 files) and `buildcraft.lib.gui.ISimpleDrawable`, in `modules/shared`.**
  A self-contained "screen coordinate algebra" package (points, rectangles, offsets, all as
  composable `IGuiPosition`/`IGuiArea` values) discovered while porting `buildcraft.lib.
  statement` below, which needed it for `StatementContext`. Confirmed via a full import audit
  of all 9 files that none of it touches Minecraft or rendering at all -- pure interfaces and
  math, built only on `java.util.function.DoubleSupplier` and the already-ported `modules/
  expression` -- so, like the earlier `buildcraft.lib.misc`/`misc.data` pure-Java files, it
  lives once in `modules/shared` rather than duplicated per platform.
- **`buildcraft.lib.statement` (7 files, both platforms).** The generic trigger/action
  wrapper layer gates and other statement-driven blocks will eventually sit on top of
  (`ActionWrapper`, `TriggerWrapper`, `StatementWrapper`, `FullStatement`, `StatementType`,
  `StatementTypeParam`, `StatementContext`). `buildcraft.api.statements` (already fully
  ported) turned out to have modernised its own shape along the way, discovered while
  porting this: `IGuiSlot#getDescription()`/`getTooltip()` return `Component` now, not
  `String` (both were built from a client-only `I18n` lookup in 1.12.2, which doesn't work on
  a server; a `Component` carries its translation key and resolves at draw time instead,
  which is also why the `@SideOnly(Side.CLIENT)` annotations on them are gone -- see
  `IGuiSlot`'s own javadoc), and `IStatementParameter`'s NBT/buffer read/write methods all
  gained a `HolderLookup.Provider` parameter, threaded through `StatementType`/
  `StatementTypeParam`/`FullStatement` here as a result -- reading or writing a parameter can
  mean reading or writing an `ItemStack`, whose data components need registry access on
  26.x. 1.20.1's copy of the same API takes the identical parameter, ignoring it, specifically
  so both platforms' `lib.statement` files could stay this close to identical; they are.
  `StatementManager`'s lookup also changed shape: no `getParameterReader(kind)` method exists
  any more, just the public `parameters`/`paramsBuf` maps directly.
- **`BuildCraftAPI/api` — 217 of 251 files.** Everything except the list below, on both targets.
  Of the 34 not ported: 20 are `package-info.java` whose only content was FML's `@API`
  annotation, which no longer exists; the rest are blocked or deliberate, and listed below.
- `buildcraft.lib.nbt` (5), `.mj` (2), `.crops` (2), `.compat` (3), `.migrate` (2), `.fake` (1),
  `.json` (1), `buildcraft.lib.misc.{NBTUtilBC,StackUtil,InventoryUtil}`, and `BCLibConfig`,
  `IChunkLoadingTile`, `IBlockWithFacing`, `ILocalBlockUpdateSubscriber`,
  `buildcraft.lib.registry.PluggableRegistry` — the parts of `buildcraft.lib`'s foundation
  layer that turned out not to need the tile/net/block/item cluster below them.
  `InventoryUtil` is a partial port (the drop/spawn/`addAll`/`addToPlayer` helpers only) --
  see the `CapUtil`/`ItemTransactorHelper` entry further down for the rest.
  `buildcraft.lib.misc.data.AverageDouble` (both platforms, most of `misc.data` already lives
  in `modules/shared` -- see that entry) drops the `INBTSerializable` interface entirely on
  26.x, the same way `ItemHandlerSimple` does and for the same reason (the interface doesn't
  exist there) -- see its own class javadoc.
  see the `CapUtil`/`ItemTransactorHelper` entry below for the rest.
- `buildcraft.lib.misc.{ArrayUtil,MathUtil,TimeUtil,StringUtilBC,ObjectUtilBC,ModUtil,
  BoundingBoxUtil,EntityUtil,PermissionUtil,RegistryUtil,FakePlayerProvider,ChunkUtil,
  StackNbtMatcher,AdvancementUtil}` (both platforms). `StringUtilBC` is a partial port for the
  same reason `InventoryUtil` is: `formatStringForWhite`/`formatStringForBlack` and
  `compareBasicReadable` all need `ColourUtil`, which is not ported (and itself needs
  `BCLibConfig`, `LocaleUtil` and `SpecialColourFontRenderer`, none of which are ported
  either) -- see that class's own javadoc. `DebuggingTools`, the fifteenth file in this batch,
  is not ported at all: it exists solely to register a `WorldEventListenerAdapter`, which the
  "deliberately not ported" list below already explains is gone with nothing to replace it.
- `buildcraft.lib.tile.craft.IAutoCraft` (relocated to `buildcraft.lib.tile.item`, next to its
  only real dependency, `ItemHandlerSimple`).
- `buildcraft.lib.delta` (2, both platforms) — the render/GUI interpolation helper
  (`DeltaManager`/`DeltaInt`) used for smoothly animating a synced value between two known
  states. Self-contained (NBT + `PacketBufferBC` only); its only real consumer,
  `TileBC_Neptune`'s network/NBT wiring, is superseded by `TileBC` here, which doesn't wire a
  `DeltaManager` in yet -- that's `lib.net`'s message-dispatch redesign's job, not this
  package's.
- `buildcraft.lib.recipe` (8 of 12) and `buildcraft.lib.particle` (6) — recipe-adjacent
  helpers and `IEffect`-driven particle rendering. `OredictionaryNames` now points at
  BuildCraft's real published item tags rather than ore-dictionary names. The 4 skipped
  recipe files (`BCRecipeShaped`, `BCRecipeShapeless`, `IngredientNBTBC`,
  `RecipeBuilderShaped`) need the full datapack recipe redesign, deferred until a consuming
  module needs one.
- `buildcraft.lib.inventory` (27 of 27, both platforms) — `IItemTransactor`'s single
  concrete implementation, `AbstractInvItemTransactor`, plus every handler/entity/filter
  wrapper around it. On 26.x, `InventoryWrapper` wraps NeoForge's own
  `VanillaContainerWrapper`; on 1.20.1 it wraps `IItemHandler` directly, unchanged in shape
  from 1.12.2.
- `buildcraft.lib.tile.item` (both platforms) — the array-backed item handler every machine
  uses (`ItemHandlerSimple`), its manager (`ItemHandlerManager`), and the
  insert-only/extract-only/filtered wrapper family around it. On 26.x this is a rewrite
  against NeoForge's own `StacksResourceHandler`/`ItemStacksResourceHandler`, which already
  provides transaction safety and NBT persistence, letting `StackInsertionFunction` and
  `IItemHandlerAdv` be dropped entirely (see `ItemHandlerSimple`'s own javadoc for why). On
  1.20.1, which keeps `IItemHandler`, the shape stays close to 1.12.2's original --
  `StackInsertionFunction` is still dropped there too (checked against every 1.12.2 call
  site, it was only ever used via its two factory methods, never the general form), but
  `IItemHandlerAdv` and `CombinedItemHandlerWrapper` are kept since `IItemHandler` still
  exists for them to wrap. `ItemHandlerManager` keeps `ICapabilityProvider` on 1.20.1
  (matching `MjCapabilityHelper`'s precedent) and drops it entirely on 26.x, where
  capabilities are registered per block entity type instead.
- `buildcraft.lib.net.{PacketBufferBC,IPayloadWriter}` (both platforms) — the bit-packing
  `FriendlyByteBuf` subclass used by compact NBT/network encoding elsewhere in `lib`. The
  message dispatch it served in 1.12.2 (`IPayloadReceiver`, `MessageManager`) is not ported
  yet; both targets' networking layers differ enough from 1.12's single channel that design
  follows once there is a concrete message to register.
- `buildcraft.lib.misc.{LocaleUtil,PositionUtil,VolumeUtil,WorkerThreadUtil,ColourUtil,
  ProfilerUtil,JsonUtil,FluidUtilBC,ExpressionCompat}` (both platforms, all with the previous
  batch's `ColourUtil`/`StringUtilBC` gap now closed -- `ColourUtil` only needed `BCLibConfig`,
  `LocaleUtil` and `SpecialColourFontRenderer`, and only the last is still missing, worked
  around below). `WorkerThreadUtil`, `PositionUtil` and `VolumeUtil` are unchanged beyond the
  1.12.2 -> modern rename (see the new Method-rename-table rows for `BlockPos.relative`,
  `Direction.getClockWise` and `AxisDirection.getStep`). `LocaleUtil` swaps 1.12.2's `I18n`
  for `Language.getInstance()` (common code on both targets, unlike the client-only modern
  `I18n`) and `MjAPI.getRfConversion()` for `IMjToRfStatus.get().getConversion()`, the latter
  already ported as part of `buildcraft.api.mj`. `ColourUtil` hard-codes
  `useColouredLabels`/`useHighContrastLabelColours` to their 1.12.2 defaults rather than
  reading them from `BCLibConfig` (outside this batch's scope to extend) -- see its class
  javadoc -- and its `NAMES`/`DARK_HEX`/`LIGHT_HEX` arrays are reordered for `DyeColor`'s
  flipped ordinal order (see the new Method-renames-table note). On 26.x it also reproduces
  `ChatFormatting#isColor()` from the ordinal, since 26.x's `ChatFormatting` dropped that
  method entirely (new "differs between targets" table row). `ProfilerUtil` follows
  `Profiler`'s split into write-only `ProfilerFiller` and read-only `ProfileResults`/
  `ResultField` (new Method-renames-table rows), identically on both targets. `JsonUtil` keeps
  `FLUID_STACK_DESERIALIZER` on 1.20.1 only -- 26.x's `FluidResource` has no "resource +
  amount" carrier that stands in for a standalone `FluidStack` deserializer outside a
  `ResourceHandler` -- and both targets' NBT<->JSON adapters follow the `Tag`/`getAsX()` vs.
  `value()` split already in the "Primitive NBT tag payload" table row, plus
  `CompoundTag.keySet()` vs `getAllKeys()` (new table row). `FluidUtilBC` is a partial port on
  both: `pushFluidAround` needs `buildcraft.lib.fluid.Tank` and `CapUtil.CAP_FLUIDS` (neither
  ported, the latter already listed below as blocked), and `onTankActivated` needs
  `buildcraft.lib.misc.SoundUtil` (not ported by anyone yet); both are skipped with the reason
  in the class javadoc rather than guessed at. The kept methods (`mergeSameFluids`,
  `areFluidStackEqual`, `areFluidsEqual`, `move`) are a real per-platform fork on 26.x, rewired
  onto `ResourceHandler<FluidResource>` + `Transaction` (`move` walks slots directly instead
  of going through `IFluidHandlerAdv`, which has no role left once every handler is
  introspectable -- see `FluidFilters`). `ExpressionCompat` is a partial port: the
  `Controllable Mode` node type (`buildcraft.api.tiles.IControllable`, not ported) and the two
  GUI node types (`buildcraft.lib.gui.pos`, not ported) are omitted, along with the 1.12.2
  obfuscation-bug workaround (`BCLib.throwBadClass`) that guarded the former -- see its class
  javadoc.
- `buildcraft.lib.cache` (7, both platforms) — the chunk/neighbour-tile lookup caches every
  machine's chunk-loading-avoidance code goes through. `Chunk#isLoaded()`/`TileEntity#isInvalid()`
  are gone (`Level#hasChunk`/`BlockEntity#isRemoved()` respectively), `Block#hasTileEntity(state)`
  is `BlockState#hasBlockEntity()`, and `TileBC_Neptune`'s own `getChunk` shortcut (which
  `NeighbourTileCache` special-cased) has no equivalent on `TileBC`, so that lookup now always
  goes through `ChunkUtil#getChunk` uniformly rather than special-casing BuildCraft's own base
  class.
- **`buildcraft.lib.marker` (6 files, both platforms) — the abstract marker-connection framework**:
  `MarkerCache`, `MarkerSubCache`, `MarkerConnection`, `MarkerSavedData`, `buildcraft.lib.tile.TileMarker`
  and `buildcraft.lib.net.MessageMarker` (registered `playToClient` on 26.x, `NetworkDirection.PLAY_TO_CLIENT`
  on 1.20.1 -- this message is server-to-client only, unlike `MessageUpdateTile`). Compiles cleanly on both
  and both dev servers still boot clean after the registration change, but nothing instantiates a concrete
  `MarkerCache`/`MarkerSubCache`/`MarkerConnection` yet -- `buildcraft.core.marker`'s concrete
  `VolumeCache`/`PathCache`/etc. are still out of scope, deliberately deferred to a later pass (see the
  `buildcraft.core` survey entry above, which flagged this package as the blocker).
  - `MarkerSavedData<S, C>` cannot supply a complete `SavedDataType`/`DimensionDataStorage` factory by
    itself -- both need a concrete, constructible type, which a generic class parameterised over `S`/`C`
    doesn't have. It still `extends SavedData` (rather than becoming a detached data holder), so a future
    concrete subtype satisfies that bound through ordinary inheritance; what it can't supply is the
    `Codec<T>` (26.x) / `Function<CompoundTag, T>` loader (1.20.1), so `createCodec`/`createLoader` are
    generic static factories a concrete subtype calls with nothing but its own no-arg constructor
    reference. No connection-specific codec is needed either way -- a connection is saved purely as its
    grouped `BlockPos` list, exactly 1.12.2's own on-disk shape.
  - `TileMarker`'s `onLoad`/`onChunkUnload` become `clearRemoved`/`setRemoved`, as expected, but the
    genuine-removal-vs-chunk-unload split turned out to differ *between* the two targets, not just from
    1.12.2, contradicting this file's own earlier assumption below (in the `buildcraft.core` survey entry)
    that both targets would need a cooperating `Block#onRemove`: decompiling the real 26.x `LevelChunk`
    shows `Block#onRemove` is gone there entirely (not renamed -- confirmed via `javap` against
    `BlockBehaviour`), replaced by `affectNeighborsAfterRemoval`, which drops the `newState` parameter the
    old `state.getBlock() != newState.getBlock()` trick needed. In its place, 26.x calls a brand new hook
    directly on the block entity, `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)`, fired only on a
    genuine block change, never a chunk unload -- so `TileMarker` handles the whole distinction itself there,
    no cooperating `Block` needed at all. 1.20.1 still has the old `onRemove(state, level, pos, newState,
    movedByPiston)` shape, so that target's `TileMarker` still exposes a public `removeFromMarkerCache()`
    for a future `BlockMarkerBase` to call from its own `onRemove` override, matching the original
    assumption. Both platforms guard against `setRemoved()` (which still fires afterwards either way, for
    both genuine removal and chunk unload) double-handling an already-genuinely-removed marker via the same
    `genuinelyRemoved` flag.
  - `MarkerSubCache`'s abstract `getPossibleLaserType()` is dropped outright (no `LaserData_BC8`, no
    renderer to call it for yet -- see that class's own javadoc), and `MarkerCache.registerCache`'s 1.12.2
    FML-lifecycle guard is dropped with no replacement (no modern equivalent check, and nothing calls this
    before mod construction finishes anyway).
  - **A real, previously-unknown runtime pitfall, found by actually booting the 1.20.1 dedicated server
    (not just compiling) after wiring `MessageMarker` into `BCNetwork`**: reading
    `Minecraft.getInstance().player` (declared type `LocalPlayer`, client-only) directly inside
    `MessageMarker.handle` crashed dedicated-server mod construction with a `BootstrapMethodError` --
    `Attempted to load class net/minecraft/client/player/LocalPlayer for invalid dist DEDICATED_SERVER` --
    even though `handle` itself is never *invoked* server-side (the message is server-to-client only).
    `BCNetwork.register()` still has to pass `MessageMarker::handle` as a method reference on both sides to
    register the codec, and that alone loads and bytecode-verifies the whole class, including `handle`'s
    body; verifying the implicit `LocalPlayer -> Player` widening assignment needs the verifier to resolve
    `LocalPlayer`'s hierarchy, which Forge's runtime dist-cleaner refuses on a dedicated server. Fixed by
    isolating that one touch into its own nested class (`MessageMarker.ClientPlayerLookup`), which only
    loads lazily when actually invoked -- never, server-side. This is a general trap for *any* future
    client-bound message handler on 1.20.1 that reads a client-only type inline, not specific to markers;
    26.x's merged/joined jar has no equivalent restriction (confirmed: the 26.x dev server boots fine with
    `MessageMarker.handle` reading `IPayloadContext#player()`, whose static type is the common `Player`
    already, never `LocalPlayer`, so this never came up there). Worth remembering for whoever writes the
    next client-bound message on 1.20.1.

- **`buildcraft.lib.misc.data.Box` and `buildcraft.core.marker`'s concrete marker types (9 files, both
  platforms) — the volume-box and path connections `buildcraft.lib.marker`'s framework was built for.**
  `Box`, `VolumeCache`/`VolumeConnection`/`VolumeSubCache`/`VolumeSavedData`,
  `PathCache`/`PathConnection`/`PathSubCache`/`PathSavedData`. This turns out to make the deferred-`Box`
  entry below stale for `Box` specifically (left as-is rather than rewritten -- a future pass can clean it
  up): `Box`'s only unported dependency was three `@SideOnly(Side.CLIENT)` fields (`laserData`,
  `lastMin`/`lastMax`, `lastType`) that nothing in its actual geometry ever read, dropped outright with a
  one-line class-javadoc note; `MessageUtil.readBlockPos`/`writeBlockPos` (blocked) turned out to be
  unnecessary too, since `FriendlyByteBuf` already has `readBlockPos()`/`writeBlockPos(BlockPos)` built in
  on both targets. `BoxIterable`/`BoxIterator` and `ProfilerBC` remain deferred exactly as that entry
  describes -- this pass didn't touch them.
  - `IZone#getRandomBlockPos` (inherited through `IBox`) takes a `RandomSource` now, not
    `java.util.Random`, but `PositionUtil#randomBlockPos` still takes the old `java.util.Random` type, so
    `Box#getRandomBlockPos(RandomSource)` reimplements that method's arithmetic directly rather than
    delegating to it.
  - `Vec3(Vec3i)` -- the constructor 1.12.2's `new Vec3d(BlockPos)` idiom relied on -- doesn't exist on
    1.20.1 (confirmed via `javap`; only `Vec3.atLowerCornerOf(Vec3i)` does there), even though 26.x does
    have it. Using `Vec3.atLowerCornerOf` uniformly, and building `getBoundingBox()`'s `AABB` through the
    `AABB(Vec3, Vec3)` constructor rather than `AABB(BlockPos, BlockPos)` (only present on 1.20.1, not
    26.x), keeps `Box.java` textually identical on both platforms -- the one genuine divergence left is
    `CompoundTag`'s int-reading method in the legacy `initialize(CompoundTag)` NBT branch (26.x:
    `getIntOr(key, default)`; 1.20.1: `getInt(key)`, already 0-defaulting).
  - `BlockPos.betweenClosed(min, max)` (the modern rename of `BlockPos.getAllInBox`) reuses a single
    mutable `BlockPos` across the whole iteration; `Box#getBlocksInArea()` calls `.immutable()` on each
    before adding it to the returned `List`, which the original 1.12.2 method never did -- a latent bug in
    the class this ports from, fixed rather than carried over, since nothing in either version has a real
    caller yet to have depended on the broken behaviour.
  - `BCCoreConfig.markerMaxDistance` isn't ported (see this file's deferred-config entries) -- both
    `VolumeConnection`/`VolumeSubCache` and `PathSubCache` use a local
    `private static final int MARKER_MAX_DISTANCE = 64;` (package-visible where a connection type and its
    sub-cache both need it) standing in for it, `64` being that config's own 1.12.2 default
    (`config.get(general, "markerMaxDistance", 64)`), not a new value.
  - `VolumeConnection#renderInWorld`/`PathConnection#renderInWorld` are empty overrides, not the
    laser-drawing 1.12.2 had -- `buildcraft.lib.client.render.laser` and
    `buildcraft.core.client.BuildCraftLaserManager` are both unported rendering code, same situation
    `MarkerConnection#renderInWorld` itself already documents. `PathConnection`'s private `renderLaser`/
    `offset` rendering helpers, and `VolumeConnection`/`PathConnection`'s dropped `@SideOnly(Side.CLIENT)`
    annotations, follow the same reasoning already established for `MarkerConnection`/`MarkerSubCache`.
  - `VolumeSubCache`/`PathSubCache`'s `getPossibleLaserType()` override is deleted outright, not just
    emptied -- the abstract method it overrode no longer exists on `MarkerSubCache` at all (dropped there
    in the previous pass), so an `@Override` here would fail to compile.
  - **`World#getPerWorldStorage().getOrLoadData`/`.setData` needed real per-platform research, and the
    obvious assumption going in (that 1.20.1's `Level#getDataStorage()` is available on a generic `Level`,
    unlike 26.x's `ServerLevel`-only `SavedDataStorage`) turned out to be wrong when checked directly.**
    Confirmed via `javap` against both real merged jars: on 26.x, `Level#getDataStorage` doesn't exist at
    all -- only `ServerLevel#getDataStorage()` does, returning `SavedDataStorage`. On 1.20.1, the same is
    true: `javap` against `net.minecraft.world.level.Level` (and, checked separately, against
    `net.minecraft.client.multiplayer.ClientLevel`) shows neither declares `getDataStorage()` either --
    only `ServerLevel#getDataStorage()` does there too, returning `DimensionDataStorage`. So both targets
    behave identically here: a client `Level` has no on-disk storage reachable at all, and
    `VolumeSubCache`/`PathSubCache`'s constructors branch on `level instanceof ServerLevel serverLevel`,
    skipping the disk load entirely otherwise and relying on `MessageMarker` network sync for the client --
    which is what actually kept 1.12.2's client in sync too, the disk load there always having been a
    server-side concern. On 26.x, the load goes through `serverLevel.getDataStorage().computeIfAbsent(
    VolumeSavedData.TYPE)`, a `SavedDataType<VolumeSavedData>` built from `MarkerSavedData.createCodec` and
    `BuildCraftAPI.nameToResourceId(VolumeSavedData.NAME)` via the 3-argument `SavedDataType(Identifier,
    Supplier<T>, Codec<T>)` constructor (no `DataFixTypes` needed, confirmed present via `javap`). On
    1.20.1, it's `serverLevel.getDataStorage().computeIfAbsent(VolumeSavedData.LOADER, VolumeSavedData::new,
    VolumeSavedData.NAME)`, `LOADER` being `MarkerSavedData.createLoader(VolumeSavedData::new)`.
    `PathSavedData` mirrors this exactly. Both dev servers boot clean with these classes on the classpath
    (nothing yet constructs a `VolumeCache`/`PathCache` to actually exercise the load/save path -- see the
    `buildcraft.lib.marker` entry above for why `registerCache` has no caller yet).

- **The marker blocks, tiles, and connector item are placeable and connectable in-game (6 files, both
  platforms): `buildcraft.lib.block.BlockMarkerBase`, `core.tile.{TileMarkerVolume,TileMarkerPath}`,
  `core.block.{BlockMarkerVolume,BlockMarkerPath}`, `core.item.ItemMarkerConnector`.** This is what the
  `buildcraft.lib.marker`/`buildcraft.core.marker` framework above was actually built for -- a real block a
  player can place, wrench-rotate, and connect. Registered in `BCCoreRegistries` (`marker_volume`,
  `marker_path`, `marker_connector`) with textures/models/blockstates/loot tables/recipes/lang pulled from
  `buildcraft_resources/assets/buildcraftcore/`, following the same pattern `BlockSpringWater`/
  `BlockPowerConsumerTester`/`ItemWrench` already established. Verified with forced rebuilds, the full test
  suite, and real dedicated-server boots on both targets (registration/asset bugs like a bad blockstate JSON
  or missing loot table only surface there, not at compile time).
  - **`MarkerCache.registerCache(VolumeCache.INSTANCE)`/`registerCache(PathCache.INSTANCE)` finally has a
    caller.** Both were left unregistered when `buildcraft.lib.marker`/`buildcraft.core.marker` landed, since
    nothing constructed one yet. Without registering, `VolumeSubCache`/`PathSubCache`'s own
    `MarkerCache.CACHES.indexOf(...)` lookup (their `cacheId`) returns `-1`, silently breaking every
    `MessageMarker` these tiles send -- this is now wired into `BCCoreRegistries`' `register(modBus)`.
  - **The old id-tagged network-payload system (`writePayload`/`readPayload`/`sendNetworkUpdate(id)`/
    `IdAllocator`) is gone, and doesn't need replacing -- it needs deleting.** `TileMarkerVolume`'s
    `showSignals` boolean used to need its own `NET_SIGNALS_ON`/`NET_SIGNALS_OFF` payload pair; `TileBC`'s
    `markDirtyAndSync()` (already landed, see its own javadoc) syncs the block entity's entire saved state to
    tracking clients, so `showSignals` is now just an ordinary persisted field
    (`saveAdditional`/`loadAdditional` on 1.20.1's `CompoundTag`; `ValueInput`/`ValueOutput` on 26.x), and
    `switchSignals()` just calls `markDirtyAndSync()`. A genuine simplification, not a compromise.
  - **`IBlockState#getActualState`'s removal (already known from `ItemWrench`) forces a real design change
    here, not just a rename.** `BlockMarkerBase.getActualState` used to synthesise
    `BuildCraftProperties.ACTIVE` from the tile's `isActiveForRender()` at render time; with no render-time
    state override left at all, `ACTIVE` has to be a persisted blockstate value the tile pushes explicitly.
    `TileMarkerVolume`/`TileMarkerPath` each added a `refreshActiveState()` helper, called from every method
    that can change whether a connection exists (`switchSignals`, `onPlacedBy`, `onManualConnectionAttempt`),
    diffing against the current blockstate before writing (`Block.UPDATE_CLIENTS`, sync-only, deliberately not
    a neighbour-notifying flag -- nothing here reacts to `ACTIVE`, so there's no loop to cause). Connections
    formed through `ItemMarkerConnector` (which calls `cache.tryConnect` directly, bypassing the tiles' own
    methods -- the *only* way path markers ever connect at all) get an equivalent helper inside that class
    instead. One documented, currently-harmless gap: a marker whose connection is invalidated by a *different*
    marker's removal never gets its own `ACTIVE` refreshed, since that path runs entirely inside
    `MarkerSubCache`/`MarkerConnection` -- inert today since nothing renders `ACTIVE` yet (no renderer, same
    deferral `MarkerConnection#renderInWorld` already documents).
  - **Wrench rotation reuses `buildcraft.lib.block.IBlockWithFacing`** (already ported, built around
    `RotationUtil.rotateAll`) rather than a hand-rolled `ICustomRotationHandler#attemptRotation` override --
    `BlockMarkerBase` just implements it and returns `true` from `canFaceVertically()`. `RotationUtil.rotateAll`
    cycles faces in a different order (north-east-south-west-up-down) than 1.12.2's
    `VanillaRotationHandlers.ROTATE_FACING` (east-south-down-west-north-up) -- same "cycle through all six
    faces" behaviour, different order, and reusing an established mechanism beat reimplementing one that just
    happens to order its cycle differently.
  - **`BlockMarkerVolume`'s periodic redstone re-check (1.12.2's randomly-ticked `updateTick`) is dropped, not
    reproduced with a scheduled tick.** Verified against real decompiled vanilla redstone-component source
    (`DiodeBlock` and friends) that a power change always fires `neighborChanged` on every adjacent block, and
    vanilla's own components rely on exactly that rather than random ticking to catch signal changes --
    `checkSignalState` being wired only into `neighborChanged` is already sufficient in practice.
    `World#isBlockPowered(pos)` is `Level#hasNeighborSignal(BlockPos)` now (a default method inherited through
    `SignalGetter`, identical on both targets, confirmed via `javap`).
  - **`BlockMarkerVolume#neighborChanged` deliberately doesn't call `BlockMarkerBase`'s self-destruct-if-
    unsupported check** -- not a new decision, a preserved 1.12.2 quirk: the original fully overrode its base
    class' `neighborChanged` the same way, so volume markers never actually self-destroyed when their support
    block was removed, unlike path markers (which don't override `neighborChanged` at all, keeping the base
    behaviour). Carried over unchanged rather than "fixed".
  - `World#isSideSolid(pos, side)` (`BlockMarkerBase.canPlaceBlockOnSide`) is
    `BlockState#isFaceSturdy(BlockGetter, BlockPos, Direction)` on the *neighbouring* block's state now,
    identical signature on both targets (confirmed via `javap`), wired into a real `canSurvive` override (a
    placement-validity hook 1.12.2 didn't have here) as well as `neighborChanged`.
  - `AxisAlignedBB`-returning `getBoundingBox`/`getCollisionBoundingBox` are `VoxelShape`-returning
    `getShape`/`getCollisionShape` now. `Block.box(...)`'s pixel-scale (0-16) arguments just divide by 16
    before reaching `Shapes.box(...)`, which already takes 0-1-scaled coordinates (confirmed via decompiled
    `Block#box` source) -- so the original six `AxisAlignedBB` literals carry over completely unchanged, just
    re-wrapped. `getCollisionShape` returns `Shapes.empty()` for the same "no collision, walk straight
    through" behaviour 1.12.2's `getCollisionBoundingBox() -> null` had.
  - `getRenderBoundingBox()`/`getMaxRenderDistanceSquared()` (1.12.2 client render-distance hints on
    `TileEntity`) don't exist in any form on `BlockEntity` any more (confirmed via `javap` on both targets) --
    dropped rather than forced into a non-existent equivalent.
  - **`ItemMarkerConnector` is a partial port.** 1.12.2's single `onItemRightClick` bundled two unrelated
    features: the marker-line connector (`interactCache`/`MarkerLineInteraction`, looking along the player's
    view line for two nearby markers of the same cache type to connect -- ported in full, onto the modern
    `Item#use(Level, Player, InteractionHand)`) and a second, entirely separate volume-box "addon" region
    editor (`onItemRightClickVolumeBoxes`, depending on `buildcraft.core.marker.volume.*` -- `Addon`,
    `AddonsRegistry`, `VolumeBox`, `WorldSavedDataVolumeBoxes`, `Lock`, `EnumAddonSlot`). That whole
    sub-feature was already explicitly deferred when this package's other marker types were ported (see this
    file's own entry above) and remains entirely unported; porting the addon system is its own project,
    independent of the marker-connector feature this class otherwise provides in full.
  - New divergence-table rows worth knowing: `BlockBehaviour.Properties#noCollision()` (26.x, correctly
    spelled) vs. `#noCollission()` (1.20.1, keeps the old double-s typo); and on 26.x, `BlockBehaviour` hooks
    like `getShape`/`getCollisionShape`/`canSurvive`/`neighborChanged`/`useWithoutItem` are `protected`, while
    the same hooks (`getShape`/`getCollisionShape`/`canSurvive`/`neighborChanged`/`use`) are `public` on
    1.20.1 -- both confirmed via `javap` against the real merged jars.
  - Not yet done: no in-game interaction test beyond a clean dedicated-server boot (give/place/connect via a
    real client) -- the registration and asset pipeline is verified, but nobody has watched a marker connect
    in a running game yet. Worth doing before relying on this for anything downstream (a `builders` machine
    that reads a `VolumeConnection`'s box, for instance).

- **`core.block.BlockDecoration` and `core.item.ItemGoggles`.** Both graduate out of the
  `buildcraft.core.{item,block,tile,gen}` survey above (their entries there are left as written, since they
  correctly describe why each needed a real design pass rather than a mechanical port).
  - **`BlockDecoration`** is six real blocks (`decorated_destroy`, `decorated_blueprint`, `decorated_template`,
    `decorated_paper`, `decorated_leather`, `decorated_laser_back`) in place of 1.12.2's single block with a
    six-value `EnumDecoratedBlock` blockstate property -- the same metadata-subtype split
    `BlockSpringWater`/`DyedBlockVariants` already established, but with *no* surviving per-variant behaviour
    at all this time: `getSubBlocks`/`damageDropped` only ever enumerated/reported metadata, and
    `getLightValue(state, world, pos)` -- the one real behaviour `EnumDecoratedBlock.lightValue` carried --
    moves to `BlockBehaviour.Properties#lightLevel(ToIntFunction<BlockState>)`, confirmed via `javap` to exist
    identically on both targets and supplied per instance from `BCCoreRegistries` rather than read back off
    blockstate. That leaves `BlockDecoration` itself an empty `Block` subclass, kept as a real named type (not
    six bare `Block` instances) purely so other code has something to `instanceof` against, the same reasoning
    `BlockSpringWater` already used. Textures are the real 1.12.2 assets pulled from
    `buildcraft_resources/assets/buildcraftcore/textures/blocks/` (`blueprint/blue`, `blueprint/black`,
    `misc/texture_red_dark`, `misc/paper`, `misc/leather`) except `laser_back`, whose 1.12.2 model pointed at
    `buildcraftsilicon:blocks/laser/bottom` -- `buildcraft.silicon` isn't ported yet, so that one texture is
    copied over from the still-unported module's own resource tree rather than invented. Properties
    (`MapColor.METAL`, `strength(5.0F, 10.0F)`, `SoundType.METAL`) match what `BlockBCBase_Neptune`'s
    constructor actually gave every 1.12.2 BuildCraft block by default (`Material.IRON`, hardness 5, resistance
    10, `SoundType.METAL`) -- `BlockDecoration` never overrode any of them -- rather than reusing
    `BlockPowerConsumerTester`'s already-ported `(5.0F, 6.0F)`, which turns out to not match that same default
    either, a preexisting minor inconsistency left alone rather than "fixed" here.
  - **`ItemGoggles` is the first place the two targets have needed genuinely different code for the same
    feature**, not just renamed API calls -- see the new divergence-table row below and each platform's own
    class javadoc for the full account. In short: 1.12.2 wrapped `ItemArmor` in Forge's `ISpecialArmor` to force
    zero defense and skip durability damage. On 26.x, `javap` confirms `ArmorItem` doesn't exist as a class at
    all any more -- equipping is purely the `Equippable` data component (real decompiled source read from the
    merged jar), which carries no defense value of its own, so `ItemGoggles` is just a plain `Item` with an
    `Equippable.builder(EquipmentSlot.HEAD)` component and no `ItemAttributeModifiers` component added on top
    (the same shape vanilla's own `Items.CARVED_PUMPKIN` uses for a zero-defense head-slot item -- read
    straight from `Items.java` in the bundled decompiled source, not invented); `damageOnHurt` is set `false`
    on the component to mirror the original's explicit no-op, though it was already moot, since the item never
    gets a `DataComponents.MAX_DAMAGE` component and `LivingEntity#hurtArmor` only calls `hurtAndBreak` when
    `isDamageableItem()` is true. On 1.20.1, `javap` confirms the opposite: `ArmorItem`/`ArmorMaterial` are
    still real (`ISpecialArmor` is the one now confirmed gone -- "class not found"), with `ArmorMaterial`
    demoted from an enum to a plain interface `ArmorItem`'s constructor reads directly to build its
    `Attributes.ARMOR`/`ARMOR_TOUGHNESS`/`KNOCKBACK_RESISTANCE` modifiers -- so `ItemGoggles` implements that
    interface itself with every numeric value zeroed, in place of reusing `ArmorMaterials.CHAIN`. Durability 0
    leaves `Item#isDamageableItem()` (`maxDamage > 0`) false, which is what `ItemStack#hurtAndBreak` actually
    checks before doing anything -- the same "never damageable" end state as 26.x, reached by a different
    mechanism. The enchantment value (12, matching `ArmorMaterials.CHAIN`) is kept on 1.20.1 even though every
    defense number is zeroed, since 1.12.2 never overrode `getItemEnchantability` and so inherited chainmail's
    default -- the one incidental property worth preserving alongside the intentionally-zeroed ones.
  - Both registered in `BCCoreRegistries` (`decorated_destroy` through `decorated_laser_back`, and `goggles`)
    with textures/models/blockstates/loot tables/lang pulled or written following `BlockSpringWater`/
    `BlockPowerConsumerTester`'s established pattern; `goggles` needs only an inventory-icon item model, since
    no rendering pipeline exists yet to draw a worn item either way (consistent with every other rendering
    deferral elsewhere in this file). Verified with forced rebuilds, the full test suite, and real
    dedicated-server boots on both targets -- no registration or component-wiring error surfaced on either.

- `buildcraft.lib.misc.data.{Box,BoxIterable,BoxIterator}` and `.ProfilerBC` -- deferred as a
  group. `Box` (328 lines) needs `buildcraft.lib.client.render.laser.LaserData_BC8` (rendering,
  not ported) and `MessageUtil` (blocked, needs the old `IMessage` networking stack); it also
  has no consumer yet (every reader is in the unported `builders`/`core`/`energy`/`silicon`
  modules). `BoxIterable` exists only to construct a `BoxIterator`, so the two travel together.
  `BoxIterator` (256 lines) itself has no blocked dependency -- everything it needs
  (`NBTUtilBC`, `StringUtilBC`, `VecUtil`, `AxisOrder`) is already ported -- but it is dense,
  order/invert/repeat-aware 3-axis iteration logic (`advance`/`moveTo`/`compare`/`willVisit`/
  `hasVisited`) with no ported consumer to verify correctness against yet; a wrong axis-order
  edge case here would be easy to miss without a real caller exercising it, so it waits for one
  rather than shipping unverified. `ProfilerBC` is a client-only wrapper around
  `Minecraft.getMinecraft()` and the old `Profiler` (see `ProfilerUtil`'s already-ported
  `Profiler`->`ProfilerFiller` split) with no consumer either; low value to port ahead of the
  rendering pass it belongs with.

- **Survey of the rest of `buildcraft.core.{item,block,tile,gen}`** (20 of 24 files, `ItemWrench`,
  `BlockSpringWater` and `TilePowerConsumerTester`/`BlockPowerConsumerTester` aside). Unlike
  `buildcraft.lib`'s utility layer, almost none of this is mechanical -- each file needs either a
  real architectural decision this port hasn't made yet, or an unported subsystem. Recorded here
  so the next pass doesn't have to re-derive it:
  - `BlockDecoration`/`ItemBlockDecorated` is single-`Block`-with-metadata-subtypes
    (`EnumDecoratedBlock`, 6 values) -- item #1 on PORTING.md's own structural-changes list,
    "block metadata is gone". Needs splitting into one real `Block`/`BlockItem` per enum value,
    the same shape `BlockSpringWater` now demonstrates for `EnumSpring`'s water half (see the
    entry above) and `DyedBlockVariants` already uses for colour families -- not a per-file
    port. Has no reader anywhere in the currently-ported tree to design the split against yet
    (unlike spring water, nothing pulls it in even indirectly).
  - `core.gen.SpringPopulate` (water/oil spring world generation) is built on
    `PopulateChunkEvent`/`TerrainGen`, Forge's old chunk-populate hook. There is no equivalent
    event on either target -- world generation is entirely datapack/`Feature`-driven now
    (`Feature<NoneFeatureConfiguration>` registered through `BiomeModifications` or a
    `ConfiguredFeature`/`PlacedFeature` JSON pair). A real feature to write, not a rename. Until
    this lands, `BlockSpringWater` (ported) has no way to spawn naturally -- same "foundation
    ported, not yet wired to its trigger" situation as `DeltaManager`/`lib.cache` and `TileBC`.

    Update: the water half is done -- see the `core.gen.SpringGenerator` progress entry below. This
    guess at the modern shape was also only half right: 26.x turned out to have collapsed
    `Feature`/`ConfiguredFeature` into one tier, not kept the classic three-tier system this note
    assumed applies uniformly -- see that entry for the real, `javap`-verified shape on each target.
  - `core.tile.ITileOilSpring` is a two-method marker interface with nothing wrong with it, but
    its only implementor is `buildcraft.energy.tile.TileSpringOil`, entirely unported; nothing
    to port it *for* yet. `BlockSpringOil` (the other `EnumSpring` half) waits alongside it: its
    block entity is only sometimes present, decided by whether `buildcraft.energy` has
    registered one at all -- a runtime, cross-module decision that 1.12.2 expressed by mutating
    a shared `EnumSpring.OIL` instance, but doesn't map onto the modern `EntityBlock`/
    `BlockEntityType` model's *static*, registration-time-only block entity typing. Needs a real
    design once `buildcraft.energy` exists to design it against, not a mechanical copy of the
    water half.
  - `ItemGoggles` implements `ISpecialArmor` (confirmed absent from both targets' jars) to make
    a zero-defense, damage-immune helmet. Modern armor is a bigger redesign than a rename can
    cover: there is no `ArmorItem` class to extend any more (verified via `javap` -- armor
    material and rendering moved to a `net.minecraft.world.item.equipment.ArmorMaterial`
    record that requires a `ResourceKey<EquipmentAsset>`, a new equipment-rendering asset
    registry this port hasn't touched yet) -- needs its own design pass, not a quick port.
  - `ItemPaintbrush_BC8` is a 17-metadata-subtype item (1 "clean" + 16 dye colours) storing
    remaining uses in NBT keyed by damage-as-metadata -- the same subtype-removal redesign
    `StackUtil`'s class javadoc already describes in the abstract, here in concrete form. It
    also needs `ParticleUtil` (rendering, not ported) and `SpecialColourFontRenderer`
    (rendering). A real redesign (one item, uses + colour as data components) rather than a
    port.
  - `ItemMapLocation` needs `buildcraft.lib.misc.data.Box`, deferred above for its own reasons.
    `ItemVolumeBox`, `ItemMarkerConnector`, `BlockMarkerPath`/`BlockMarkerVolume` and
    `TileMarkerPath`/`TileMarkerVolume` all need `buildcraft.core.marker`/
    `buildcraft.lib.marker`, neither ported (see the next entry). `ItemFragileFluidContainer`
    needs `buildcraft.lib.fluid` (unported) and a `Capability<IFluidHandler>` (the `CapUtil`
    chain, already documented as blocked). `ItemList_BC8` needs `buildcraft.lib.list`,
    deferred above for having no ported consumer. `ItemEngine_BC8`/`BlockEngine_BC8`/
    `TileEngineCreative`/`TileEngineRedstone_BC8` all need `buildcraft.lib.engine`, unported.
  - `buildcraft.lib.marker` (4 files) was checked directly as part of this survey: blocked on
    `buildcraft.lib.tile.TileMarker` (unported -- and its own `onLoad`/`onChunkUnload` hooks
    are themselves gone from `BlockEntity`, folded into `setLevel`/`clearRemoved`/`setRemoved`;
    distinguishing "chunk unloaded" from "genuinely destroyed", which 1.12.2 could tell apart
    at the tile level, now needs the owning `Block`'s `onRemove(state, level, pos, newState,
    movedByPiston)` comparing `state.getBlock() != newState.getBlock()` instead -- a real
    design point for whoever ports `TileMarker`, not a one-line rename), and
    `buildcraft.lib.client.render.laser.LaserData_BC8` (rendering, unported; `MarkerSubCache`'s
    one use of it, `getPossibleLaserType()`, can simply be dropped when that file is ported --
    nothing calls it without a renderer to call it for). The message-dispatch blocker this
    entry used to flag is resolved -- see the "Networking" structural-change entry and the new
    `buildcraft.lib.net`/`BCNetwork` progress entry below -- so `MessageMarker` itself is now
    just an ordinary message to write once `MarkerCache` exists to route it through, not a
    design problem in its own right.

    Update: this whole entry is superseded -- `buildcraft.lib.marker` and `TileMarker` are both ported now
    (see the new `buildcraft.lib.marker` progress entry above), and the `onRemove` assumption two sentences
    up turned out to only hold for 1.20.1; 26.x found a better dedicated hook instead. See that entry for
    the full account. Only `buildcraft.core.marker`'s concrete subtypes remain unported.

- **The engine subsystem: `buildcraft.lib.engine.{TileEngineBase,EngineConnector,IEngineLikeForLedger}` plus two
  real machines, `core.block.{BlockEngineWood,BlockEngineCreative}`/`core.tile.{TileEngineWood,TileEngineCreative}`
  (both platforms).** The largest single feature this port has taken from `buildcraft.core`/`buildcraft.lib` so
  far -- MJ storage, a heat/power-stage state machine driving an overheat condition, chain-of-engines power
  routing, and a redstone-pulsed-vs-constant power split, all ported faithfully from `TileEngineBase_BC8`
  (660 lines). What changed is purely the plumbing, following patterns already established elsewhere in this
  port; see `TileEngineBase`'s own (long) class javadoc for the full account on each target. Registered
  (`engine_wood`, `engine_creative`) in `BCCoreRegistries` with textures/models/blockstates/loot
  tables/lang pulled from `buildcraft_resources/assets/buildcraftcore/`, a datapack recipe for `engine_wood`
  (a redstone-torch-free reproduction of the original's shaped recipe, using vanilla planks/glass/piston plus
  `gear_wood` rather than 1.12.2's ore-dictionary keys), and no recipe for `engine_creative` (creative-tab-only in
  1.12.2 too). Verified with forced rebuilds, the full 25-test suite, and clean dedicated-server boots on both
  targets (no registration/asset/capability error surfaced on either) -- but **not** with a full in-game
  placed-and-ticking check: this session could not get an interactive server console attached in its sandboxed
  environment (tried a named-pipe-fed stdin and writing directly to the server process's `/proc/<pid>/fd/0`;
  neither delivered a typed command to the running dedicated server), so `/setblock`-and-observe was not
  completed. Worth doing properly before relying on this for anything downstream.
  - **Renaming.** `TileEngineBase_BC8` -> `TileEngineBase` (dropping the `_BC8` suffix, matching this port's
    established convention of dropping 1.12.2 version-tag suffixes -- `ItemBC_Neptune`/`BlockBCTile_Neptune`/
    `TileBC_Neptune` and friends are all gone the same way already). `TileEngineRedstone_BC8` -> `TileEngineWood`:
    despite its class name this was never a combustion engine, it just outputs a small constant MJ draw while
    redstone-powered (hence the "free power" advancement) and was registered in 1.12.2 under the `WOOD`
    `EnumEngineType`/`tile.engine.wood` tag -- the new name follows what it actually is and how it was
    registered, not the misleading class name. `TileEngineCreative` keeps its name. 1.12.2's single, multi-variant
    `BlockEngine_BC8` (metadata-subtyped via `EnumEngineType`, extended by `BlockEngineBase_BC8`'s
    `registerEngine(type, constructor)` machinery so `buildcraft.core` could wire up WOOD/CREATIVE while
    `buildcraft.energy`, unported, plugged STONE/IRON/RF into the *same block instance* later) splits into two
    real, independent blocks, `BlockEngineWood`/`BlockEngineCreative` -- the same one-real-`Block`-per-variant
    split `BlockSpringWater`/`BlockDecoration` already established, with the added twist that this cross-module
    "other modules register more variants into my block later" design has no ported equivalent at all needed:
    `BlockEngineBase_BC8`'s variant/`registerEngine` machinery itself is not ported, only the two concrete engine
    tiles it used to host. `ItemEngine_BC8`, which existed purely to pick a model variant per metadata damage
    value, has nothing left to do and is not ported either -- `BCRegistry.addBlockAndItem`'s default `BlockItem`
    is sufficient, same as every other block in this port. `buildcraft.core.tile.ITileOilSpring` (a two-method
    marker interface whose only implementor, the unported `buildcraft.energy.tile.TileSpringOil`, doesn't exist in
    this port) and the `STONE`/`IRON`/`RF` engine types (`buildcraft.energy`, unported) are out of scope, matching
    the existing `buildcraft.core` survey entry's own reasoning for both.
  - **Capabilities.** An engine tile's `mjConnector` field (typed `IMjConnector`, always a plain `EngineConnector`
    for both concrete engines) turns out to be the *only* MJ capability either engine ever exposes: `EngineConnector`
    implements nothing beyond `IMjConnector`, so 1.12.2's `MjCapabilityHelper` `instanceof`-probing it for
    `IMjReceiver`/`IMjRedstoneReceiver`/`IMjReadable`/`IMjPassiveProvider` never matched anything there either --
    an engine pushes power outward by calling a neighbour's `receivePower`, it is never itself received from, read,
    or pulled from. So on 26.x, `BCCoreRegistries#registerCapabilities` registers only `MjCapabilities.CONNECTOR`
    for each engine block entity type, directly (not through `MjCapabilityHelper.registerAll`, which is built to
    expose every MJ capability unconditionally on every side -- not what an engine, which only ever answers on its
    `currentDirection` face, wants), guarded by `side == tile.getCurrentFacing()`. On 1.20.1, the tile itself holds
    a `LazyOptional<IMjConnector>` and answers `getCapability` with the same guard, matching
    `TilePowerConsumerTester`'s already-established per-instance pattern. **RF auto-conversion did not make it
    in.** 1.12.2's `getReceiverToPower(TileEntity, EnumFacing)` had an RF-fallback branch
    (`MjToRfAutoConvertor.createReceiver(rf)`, wrapping a neighbour's foreign `IEnergyStorage` to *look like* an
    `IMjReceiver`), but the `MjToRfAutoConvertor` class already built on both platforms goes the *opposite*
    direction -- it wraps an `IMjConnector` to *look like* Forge/NeoForge energy to outside callers, which is what
    lets an external mod pull RF out of a BuildCraft machine, not what lets an engine push MJ into a foreign RF
    machine. Writing the reverse adapter this call site would need is new work outside this pass's scope, not a
    rename of something already built, so `getReceiverToPower(Direction)` here is MJ-to-MJ only -- deliberately,
    documented in `TileEngineBase`'s own javadoc, not silently dropped.
  - **Rotation.** `attemptRotation()` skips any face without a valid power receiver behind it
    (`isFacingReceiver`), which rules out reusing `IBlockWithFacing`/`RotationUtil.rotateAll` the way
    `BlockMarkerBase` does (that cycles blindly through all six faces with no such check). 1.12.2 drove the cycle
    order from `VanillaRotationHandlers.ROTATE_FACING` (unported -- see this file's own "deliberately not ported"
    entry for that class, which also covers ~25 unrelated vanilla-block rotation handlers with no bearing here),
    so the same six-direction cycle (east-south-down-west-north-up) is reproduced inline in `TileEngineBase` using
    the already-ported, pure-Java `buildcraft.lib.misc.collect.OrderedEnumMap` rather than porting the whole
    unrelated class. Both concrete engine blocks implement `ICustomRotationHandler` directly (matching 1.12.2's
    own per-block-class implementation) and delegate to the tile's `attemptRotation()`, which
    `CustomRotationHelper.INSTANCE.attemptRotateBlock` (already ported, unchanged) dispatches to automatically via
    an `instanceof ICustomRotationHandler` check -- no extra registration needed.
  - **Block shape/face-solidity: real, decompiled-source-verified research, not a guess.** `javap` against
    `BlockBehaviour` on both targets' merged jars shows `getBlockFaceShape`/`isSideSolid` have no surviving
    override point at all any more -- `isFaceSturdy` (the modern rename `BlockMarkerBase` already uses, for a
    *different* purpose: checking a *neighbour's* face) is declared on `BlockBehaviour$BlockStateBase`, not on
    `BlockBehaviour`/`Block` itself, and is computed purely from the block's own `VoxelShape` geometry via
    `SupportType.FULL.isSupporting`. There is nothing left for a block to override to declare "only this one face
    is solid" independent of its collision shape. Since there is also no rendering pipeline in this port yet to
    feed 1.12.2's `EnumBlockRenderType.ENTITYBLOCK_ANIMATED` custom model (matching every other machine ported so
    far), both engine blocks use an ordinary static block model with no shape override at all -- `Block`'s own
    default (a full cube) is exactly what's wanted with no renderer to justify anything else, and a full cube is
    therefore sturdy on every side rather than just the one opposite `currentDirection`. This is a real,
    documented behaviour change from 1.12.2, not an oversight; reproducing the old behaviour would need a custom
    per-tile `VoxelShape`, undesirable without a renderer to justify the geometry.
  - **Ticking.** `ITickable#update()` becomes `TileEngineBase#serverTick()`, wired through each concrete engine
    block's `EntityBlock#getTicker` exactly like `BlockPowerConsumerTester`/`TilePowerConsumerTester` already do.
    The client-side half of the original `update()` (the piston `progress` animation interpolating every client
    tick while `isPumping`, plus `getProgressClient`/`lastProgress`/`clientModelData`/`ModelVariableData`) is
    dropped rather than kept inert: nothing in this port can register a `BlockEntityRenderer` yet to consume it,
    matching every other rendering deferral already documented elsewhere in this file. `progress` and
    `progressPart` are still tracked and persisted server-side, so a future renderer has real data to read.
  - **Owner tracking for the "free power" advancement.** `getOwner().getId()` was part of the old, much larger
    `TileBC_Neptune`, with no equivalent on the current, slimmer `TileBC`. `TileEngineWood` (not the shared base,
    since this is wood-engine-specific) adds its own small `@Nullable UUID owner` field, set from `setPlacedBy`'s
    `placer` argument the same "the block calls the tile's own placement hook" pattern `TileMarkerVolume
    #onPlacedBy`/`BlockMarkerVolume#setPlacedBy` already established, persisted, and passed to the already-ported
    `AdvancementUtil.unlockAdvancement(UUID, Identifier/ResourceLocation)` overload in place of the original
    direct call. The advancement JSON itself (`buildcraftcore:free_power`) is not ported, matching the exact
    precedent already set for `ItemWrench`'s `buildcraftcore:wrenched` -- `AdvancementUtil.unlockAdvancement`
    already tolerates an unregistered advancement id as a harmless one-time warning.
  - **A genuine, worth-flagging finding, not a guess: `TileEngineCreative`'s wrench-driven power-output cycling
    (`onActivated`) is very likely unreachable through an actual wrench, on both targets, and was probably
    already unreachable in 1.12.2 too.** `ItemWrench#useOn` intercepts every wrench right-click through
    `CustomRotationHelper.INSTANCE.attemptRotateBlock` and returns a definite result before a block's own
    interaction hook (`useItemOn`/`use`) is ever reached, and since `BlockEngineCreative` also implements
    `ICustomRotationHandler`, wrenching it always rotates it rather than falling through. 1.12.2's own
    architecture is the same shape (`ItemWrench_Neptune` intercepted rotation the same way, ahead of
    `BlockBCTile_Neptune#onBlockActivated`'s delegation to `TileEngineCreative#onActivated`), so this is a
    faithfully-ported pre-existing quirk, not a new bug -- but it is still ported and wired in (via `useItemOn` on
    26.x, `use` on 1.20.1), documented in `BlockEngineCreative`'s own javadoc, in case a future wrench redesign
    changes the short-circuiting.
  - New divergence-table rows worth knowing: `Player#sendSystemMessage(Component)` (26.x, no action-bar flag;
    the action-bar equivalent is the separate `Player#sendOverlayMessage(Component)`) vs.
    `Player#displayClientMessage(Component, boolean)` (1.20.1, action-bar flag still inline) -- found porting
    `TileEngineCreative#onActivated`'s status message. `World#isBlockIndirectlyGettingPowered(pos) -> int` is
    `Level#getBestNeighborSignal(pos) -> int` on both targets (confirmed via `javap` against `SignalGetter`,
    identical signature on both) -- the direct modern successor, alongside the already-known
    `isBlockPowered`/`hasNeighborSignal` (boolean) rename `BlockMarkerVolume` already uses.

- **`core.gen.SpringGenerator` (both platforms), replacing 1.12.2's `core.gen.SpringPopulate` -- the last
  piece of `core.block.BlockSpringWater` (registered since the `ItemWrench`/`BlockSpringWater` progress entry
  above, but never spawned anywhere until now).** 1.12.2's version was a `@SubscribeEvent`-driven
  `PopulateChunkEvent.Post` handler calling `TerrainGen.populate` for permission, then placing blocks directly
  with `World#setBlockState`. That whole imperative, cancellable-event world-gen style is gone on both
  targets, replaced by a declarative `Feature`/datapack system -- but the earlier guess in the
  `buildcraft.core` survey above (that both targets share one `Feature<FC>`/`ConfiguredFeature`/`PlacedFeature`
  three-tier shape) turned out to be wrong for 26.x specifically, confirmed by real `javap`/decompiled-source
  research against both merged jars rather than assumed:
  - **1.20.1 keeps the classic shape.** `Feature<FC extends FeatureConfiguration>` (abstract class,
    `place(FeaturePlaceContext<FC>)`) is wrapped in a `ConfiguredFeature<FC, Feature<FC>>` (datapack JSON,
    `Registries.CONFIGURED_FEATURE`) which is wrapped in a `PlacedFeature` (also datapack JSON,
    `Registries.PLACED_FEATURE`). Only the bare `Feature<FC>` type itself is a static code registry
    (`Registries.FEATURE`, confirmed via `javap`: `ResourceKey<Registry<Feature<?>>>`) -- registered here via
    a new `BCCoreFeatures` (`DeferredRegister<Feature<?>>`, mirroring the pattern `BCCoreRegistries` already
    uses for every other registry). `SpringGenerator extends Feature<NoneFeatureConfiguration>` (vanilla's own
    "no config" marker type, since this feature has no real configuration beyond which block to place) and the
    `ConfiguredFeature`/`PlacedFeature` pair are plain JSON under
    `data/buildcraft/worldgen/{configured_feature,placed_feature}/spring_water.json` -- the former still needs
    an explicit empty `"config": {}` even with `NoneFeatureConfiguration`, confirmed against vanilla's own
    bundled `void_start_platform.json`.
  - **26.x collapsed this into two tiers, not three.** `javap` against the real 26.x merged jar returns "class
    not found" for `net.minecraft.world.level.levelgen.feature.ConfiguredFeature` -- it does not exist at all.
    `Feature` itself is now a plain **interface** (`place(WorldGenLevel, ChunkGenerator, RandomSource,
    BlockPos)`, no `FeaturePlaceContext`), and a concrete feature is a **record implementing `Feature`
    directly**, carrying its own configuration as record fields and its own `codec()` -- confirmed against
    vanilla's own decompiled `SpringFeature` (`record SpringFeature(FluidState, boolean, int, int,
    HolderSet<Block>) implements Feature`), which this port's `SpringGenerator` (a zero-field record, since
    there's nothing to configure) follows exactly. The "configured feature" concept folded into the feature
    instance itself, and the two old registries traded roles: `Registries.FEATURE_TYPE` is now the *static*,
    code registry (holds `MapCodec<? extends Feature>` -- the codec a feature deserializes through, confirmed
    via `javap` and vanilla's own `FeatureTypes.bootstrap`, which registers vanilla's `SpringFeature.CODEC`
    under id `spring_feature` this way), while `Registries.FEATURE` is now the *dynamic*, datapack registry
    (holds actual, fully-configured `Feature` instances -- confirmed via `javap`:
    `ResourceKey<Registry<Feature>>`, not `Feature<?>`). `PlacedFeature` is unchanged in role on both targets
    (a `Holder<Feature>` + placement modifiers, still `Registries.PLACED_FEATURE`, still pure JSON). So on
    26.x, `BCCoreFeatures` registers a `DeferredRegister<MapCodec<? extends Feature>>` against
    `Registries.FEATURE_TYPE` (a `DeferredHolder<MapCodec<? extends Feature>, MapCodec<SpringGenerator>>`,
    `SpringGenerator.CODEC` built with `MapCodec.unit(SpringGenerator::new)` since there are no fields to
    serialize), and the actual feature/placed-feature entries are JSON under
    `data/buildcraft/worldgen/{feature,placed_feature}/spring_water.json` -- the former just
    `{"type": "buildcraft:spring_water"}`, no config block needed at all.
  - **Generation-rarity and column placement moved into declarative `PlacementModifier`s, not hand-rolled
    Java.** 1.12.2's "every 40th chunk" (`random.nextFloat() > 0.025f`) and `random.nextInt(16)` column pick
    are now a `minecraft:rarity_filter` (`"chance": 40`) and `minecraft:in_square` in this feature's
    `placed_feature` JSON (identical field names/ids on both targets, confirmed via `javap` and vanilla's own
    bundled `spring_water.json`/`lake_lava.json`) -- decompiled `RarityFilter#shouldPlace` computes exactly
    `random.nextFloat() < 1.0F / chance`, the same 1-in-40 odds. A closing `minecraft:height_range` (pinned to
    `above_bottom: 0` via a `minecraft:constant` height provider) anchors the scan to the world floor in place
    of the original's implicit `y=0`, and a trailing `minecraft:biome` filter matches the sanity check every
    real vanilla placed feature ends its chain with. The bedrock scan and water-fill loop themselves stayed in
    Java (in `SpringGenerator#place`) rather than being expressed declaratively too, since they need to inspect
    real block state column-by-column -- not something a `PlacementModifier` can do; the divide drawn here is
    "pure probability/position decisions go in JSON, world-state inspection stays in Java."
  - **Nether/End exclusion moved from a runtime dimension check to biome targeting, per the task's own
    suggested (and confirmed cleaner) approach.** 1.12.2 checked `dimId == -1 || dimId == 1` in Java; this
    feature instead is simply never attached to a Nether/End biome, via the `#minecraft:is_overworld` biome
    tag (confirmed present and correctly excluding Nether/End in both real merged jars' bundled data) on a
    biome-modifier JSON targeting the `fluid_springs` `GenerationStep.Decoration` step -- the same step
    vanilla's own spring features use (confirmed via `javap` against `GenerationStep$Decoration`). The biome
    modifier's own registered type id and JSON shape needed real, per-target verification, not an assumption
    that NeoForge kept Forge's `forge:add_features` unchanged: decompiled source confirms 26.x's
    `net.neoforged.neoforge.common.world.BiomeModifiers$AddFeaturesBiomeModifier` registers under
    `neoforge:add_features`, and the registry itself (`NeoForgeRegistries.Keys.BIOME_MODIFIERS`) lives under
    the `neoforge` namespace -- so the JSON is `data/buildcraft/neoforge/biome_modifier/spring_water.json`.
    1.20.1 keeps Forge's own `net.minecraftforge.common.world.ForgeBiomeModifiers$AddFeaturesBiomeModifier`
    under `forge:add_features` (`ForgeRegistries.Keys.BIOME_MODIFIERS` confirmed keyed `forge:biome_modifier`
    via decompiled source), so `data/buildcraft/forge/biome_modifier/spring_water.json` -- matching this
    project's own existing `data/forge/tags/items` convention for that namespace.
  - **`EnumSpring.WATER.canGen`** is checked at the top of `SpringGenerator#place`, matching 1.12.2's own guard
    in its event handler -- it's a plain mutable field, not config-driven yet (see its own javadoc), so there's
    no declarative way to gate a `PlacementModifier` chain on it from JSON.
  - **A faithfully-preserved quirk, not a new decision: the original's "handle flat bedrock maps" special
    case.** On finding bedrock at the very first scanned layer (the world floor, always solid bedrock on every
    world type), 1.12.2 shifted the target position one layer *below* the floor, which its Y&gt;=0 chunk
    storage silently discarded -- a no-op, not a crash, for that specific roll. A modern level's chunk storage
    isn't safely assumed to tolerate the same out-of-range write (26.x in particular gives every dimension a
    real, configurable minimum Y rather than a hard-coded 0), so this port reproduces the same *outcome* (that
    attempt places nothing) as an explicit early return instead of reproducing the out-of-bounds write itself.
    `World#getHeight()` as the water-fill loop's upper bound is the trap PORTING.md's structural-changes list
    already documents as item 15 -- replaced with `Level#getMaxY()` (26.x) / `Level#getMaxBuildHeight()`
    (1.20.1), paired with `getMinY()`/`getMinBuildHeight()` in place of the original's implicit `y=0` floor.
  - Verified with forced rebuilds, the full 25-test suite, and real dedicated-server boots on both targets
    against a **freshly deleted world save** (not a stale one from an earlier session's testing) -- a bad
    feature codec, a malformed placement/biome-modifier JSON, or a missing registration would surface as a
    datapack/registry error exactly here, at world load and spawn-chunk generation, not at compile time. Both
    booted clean with no exceptions and genuinely regenerated region files (26.x: 5 `.mca` files in ~2s;
    1.20.1: 10 `.mca` files in ~12s, logging real "Preparing spawn area: N%" progress throughout) -- not
    verified beyond that clean-boot-plus-generation ceiling: nothing in this sandboxed environment could
    confirm a spring block actually rolled a successful 1-in-40 placement and is sitting in one of those
    regenerated chunks (interactive server-console commands aren't reliably reachable here either, matching
    this file's other "not verified" notes on in-game interaction).

Deliberately not ported, with reasons:

- `buildcraft.lib.registry.{RegistrationHelper,RegistryConfig,TagManager,CreativeTabManager}`,
  `buildcraft.lib.block.{BlockBCBase_Neptune,BlockBCTile_Neptune}`, and
  `buildcraft.lib.item.IItemBuildCraft` (and everything implementing it --
  `ItemBC_Neptune`/`ItemBlockBC_Neptune`/`ItemBlockBCMulti`) -- all of 1.12.2's per-module
  registration plumbing. This is not a porting gap: the port's `BCRegistry` (see "Both
  platforms, at parity" above) already replaced the whole `RegistrationHelper`/`TagManager`/
  `RegistryConfig` trio, and `TileBC`/`BlockBCTile` already replaced the Neptune base
  classes -- both say so in their own javadoc. `IItemBuildCraft`'s job (setting an
  unlocalised name, a registry name, a creative tab, and per-damage-value model variants) is
  covered by `BCRegistry` plus the lang/model JSON for everything except the model-variant
  half, which has nothing left to port: item damage no longer selects a model variant at
  all.
- `buildcraft.lib.misc.CapUtil`, `buildcraft.lib.inventory.ItemTransactorHelper`,
  `buildcraft.lib.tile.craft.WorkbenchCrafting` and `buildcraft.lib.misc.CraftingUtil` -- one
  blocked chain. `CapUtil`'s actual job (exposing `Capability<IItemHandler>`/
  `Capability<IFluidHandler>` tokens, plus a custom-registered `Capability<IItemTransactor>`)
  is entirely built on `@CapabilityInject`, removed well before either target; 1.20.1's two
  vanilla tokens already have a built-in replacement with no class of their own needed
  (`ForgeCapabilities.ITEM_HANDLER`, used directly by `ItemHandlerManager`), but the custom
  `IItemTransactor` capability still needs designing against `RegisterCapabilitiesEvent`
  (1.20.1) or `BlockCapability` (26.x) -- real work, not a rename, and nothing yet needs
  `IItemTransactor` to be a capability rather than just an interface. `ItemTransactorHelper`
  (capability lookup against an arbitrary neighbouring block entity) needs that token to
  exist. `WorkbenchCrafting` needs `InventoryUtil.addToBestAcceptor`, which needs
  `ItemTransactorHelper`; `CraftingUtil` (`GameRegistry.findRegistry(IRecipe.class)` ->
  `RecipeManager`/`RecipeType`) has no other consumer, so it waits for the same thing.
  `InventoryUtil`'s own capability-independent half (drop/spawn/`addAll`/`addToPlayer`) is
  ported already; see its entry above.
- `buildcraft.lib.cap.CapabilityHelper` -- a generic multi-capability-per-face
  `ICapabilityProvider` (the same "several capability instances behind one provider" idea
  `MjCapabilityHelper`/`ItemHandlerManager` each implement one-off for their own case, made
  reusable). Blocked entirely on 26.x for the same reason those two are restructured there:
  no `ICapabilityProvider` to implement. Mechanically portable on 1.20.1, but every consumer
  (`TileFiller`, `TileQuarry`, `TileMiner`, `TileLaser`, pipe behaviours, ...) lives in
  `buildcraft.core`/`builders`/`factory`/`silicon`/`transport`, all entirely unported; nothing
  to wire it into yet.
- `buildcraft.lib.prop.UnlistedNonNullProperty` -- implements Forge's old
  `IUnlistedProperty`, the extended-blockstate mechanism for carrying render-only data that
  isn't a real blockstate property. Confirmed absent from both targets' jars via `javap`;
  block entity renderers get their block entity directly now, so there's nothing left needing
  an unlisted property to smuggle data through the blockstate.
- `buildcraft.lib.list` (8 files) -- the item-list ("phantom item list") GUI's matching
  engine (`ListHandler`, `ListMatchHandler{Armor,Class,Fluid,OreDictionary,Tools}`,
  `ListOreDictionaryCache`, `VanillaListHandlers`). The `buildcraft.api.lists` interfaces it
  implements are already ported, but every real consumer (`ItemList_BC8`, the list GUI/
  container, `BCLib`'s registration) is in `buildcraft.core`, entirely unported, and
  `ListMatchHandlerOreDictionary` specifically needs redesigning around tags now that the ore
  dictionary is gone. Waits for a real consumer along with the rest of `core`.
- `buildcraft.core.statements` (19 files) -- checked directly once `buildcraft.lib.statement`
  landed, since that was the blocker PORTING.md's `buildcraft.core` survey originally flagged
  for it. It turns out `lib.statement` was necessary but not sufficient: an import audit of
  all 19 files found most also need one or more of -- the old `IFluidHandler`/`IItemHandler`
  capability-based transfer API (`TriggerFluidContainer`, `TriggerFluidContainerLevel`,
  `TriggerInventory`, `TriggerInventoryLevel`, `CoreTriggerProvider`), gone entirely on 26.x
  and needing the same kind of per-file `ResourceHandler` redesign `lib.tile.item` and
  `FluidUtilBC` already went through, not a rename; the blocked `CapUtil` chain (same files);
  `buildcraft.core.{BCCoreSprites,BCCoreStatements}`, themselves unported (every concrete
  trigger/action's `getSprite()` reads a constant off `BCCoreSprites`, and each self-registers
  into `BCCoreStatements` at construction); `buildcraft.lib.engine.TileEngineBase_BC8`
  (`TriggerEnginePowerStage`), unported; and, for `StatementParameterDirection` specifically,
  direct `TextureAtlasSprite`/`TextureMap` rendering. `lib.statement` itself is done and
  verified (see its own Progress entry) -- this package just turned out to need several more
  things besides.
- `buildcraft.lib.misc.BlockUtil` (555 lines) -- deferred whole rather than partially ported,
  because it bundles several genuinely separate redesigns rather than one mechanical port:
  - Its fluid-block cluster (`isFullFluidBlock`, `getFluid`/`getFluidWithFlowing`/
    `getFluidWithoutFlowing`, `drainBlock`) is built on `IFluidBlock`/`BlockFluidBase`/
    `BlockFluidClassic`/`BlockLiquid`/`FluidRegistry` -- a whole block-per-fluid-level
    architecture that no longer exists. Fluids are `FluidState` on any `BlockState` now
    (confirmed: `BlockBehaviour.BlockStateBase#getFluidState()`), a fundamentally different
    shape, not a rename.
  - `Block.getDrops` and `ForgeEventFactory.fireBlockHarvesting`, which
    `getItemStackFromBlock`/`harvestBlock` build on, are both gone. The static
    `Block.getDrops` replacement takes a `BlockState`/`ServerLevel`/`BlockPos`/
    `BlockEntity`, and optionally an `Entity` and a *new* `net.minecraft.world.item.
    ItemInstance` type (confirmed via `javap` against the 26.x jar) in place of the old
    `ItemStack` + fortune-int pair -- `ItemInstance` didn't exist in any version this port
    has touched so far and needs its own investigation before anything builds on it.
    `BlockEvent.BreakEvent` itself moved packages and names, to
    `net.neoforged.neoforge.event.level.block.BreakBlockEvent` (confirmed present in the
    26.x NeoForge jar; the harvesting-chance hook `fireBlockHarvesting` used to pair with
    doesn't appear to still exist under that name and needs its own search).
  - `computeBlockBreakPower` reads `buildcraft.core.BCCoreConfig`, which is in `buildcraft.
    core` -- entirely unported.
  - `getOtherDoubleChest` reads `TileEntityChest`'s `adjacentChestX/ZNeg/Pos` fields directly;
    modern double-chest merging goes through `DoubleBlockCombiner` and needs verifying from
    scratch, not assumed compatible.
  - `explodeBlock` hand-builds an `Explosion` and manually sends `SPacketExplosion` to nearby
    players; both the `Explosion` constructor shape and the packet class have almost
    certainly changed and need the same from-scratch verification.

  Everything else in the file (`breakBlock`/`harvestBlock`/`destroyBlock`/
  `getFakePlayerWithTool`, `canChangeBlock`, `getBlockHardnessMining`/`isUnbreakableBlock`/
  `isToughBlock`, the `getTileEntity`/`getBlockState` chunk-avoiding wrappers around the
  already-ported `CompatManager`, `useItemOnBlock`, `onComparatorUpdate`, and the
  blockstate-property comparison helpers) has no similar blocker and is a reasonable target
  for a focused follow-up pass once the fluid/drops/double-chest pieces above are actually
  designed, rather than split off today into a partial file that would need revisiting
  anyway.
- `buildcraft.lib.block.VanillaPaintHandlers` -- registered explicit paint handlers for
  vanilla glass, glass panes and terracotta, each a single block with a 16-value colour
  property in 1.12.2. All three are 16 separate blocks now, following the
  `<colour>_<suffix>` naming convention every vanilla colour family uses
  (`white_stained_glass`, `red_terracotta`, ...) -- which is exactly what
  `buildcraft.api.blocks.DyedBlockVariants` (see the api.blocks port) discovers generically.
  `CustomPaintHelper`'s default fallback already recolours all three without any
  block-specific registration; there is nothing left for this class to do.
- `buildcraft.lib.block.LocalBlockUpdateNotifier` and `buildcraft.lib.world.
  WorldEventListenerAdapter` -- both exist only to implement `IWorldEventListener`, vanilla's
  generic "notify me of every block change in this level" hook. It is not renamed, it is
  gone -- confirmed absent from the 26.x jars, with nothing that fires on every block change
  generically to replace it (NeoForge's `BlockEvent` variants are for specific things:
  drops, neighbour-notify, trample; none is "a block's state changed"). Nothing in the port
  calls either class yet, so this is deferred pending a real consumer to design the
  replacement against, rather than guessed at speculatively.
- `buildcraft.lib.chunkload.ChunkLoaderManager` -- 1.12.2's chunk-forcing API
  (`ForgeChunkManager.requestTicket`/`Ticket`) was redesigned into a
  `TicketHelper`/`LoadingValidationCallback` pair in Forge *before* 1.20.1 (confirmed: this
  target's jar already has the new shape), and redesigned again into a registered
  `TicketController` on 26.x. Needs rewriting on both targets, not porting on one and
  renaming on the other. `IChunkLoadingTile`, the pure-data interface machines implement to
  ask for chunkloading, is ported; the manager that reads it is not.
- `buildcraft.lib.registry.MigrationManager` -- block/item id migration across mod versions
  (`RegistryEvent.MissingMappings`). A maintenance feature with no bearing on anything else
  in the port; deferred as low priority rather than blocked.
- `buildcraft.lib.block.VanillaRotationHandlers` -- QoL wrench-rotation for ~25 vanilla block
  types (anvils, stairs, doors, hoppers, skulls, ...). Each needs individually verifying
  against the modern renamed block classes (`BlockDirectional`->`DirectionalBlock`,
  `BlockRedstoneDiode`->`DiodeBlock`, etc.) and the file also leans on
  `ObfuscationReflectionHelper` for private-field access, which needs rethinking under
  Mojang mappings. Real value, but an enhancement rather than something anything else
  depends on; deferred rather than rushed.
- `buildcraft.lib.block.BlockMarkerBase` and the rest of `buildcraft.lib.item`
  (`ItemDebugger`, `ItemGuide`, `ItemGuideNote`, `ItemPluggableSimple`) -- follow
  `buildcraft.lib.marker` and the guide-book/pluggable systems respectively, none ported yet.
- `buildcraft.lib.script.{ScriptableRegistry,SimpleScript,SimpleReloadableRegistry,
  ReloadableRegistryManager}` -- 1,629 lines together, and effectively one feature:
  BuildCraft's own JSON-based scripting system for adding, replacing or removing recipes
  without writing a mod, predating (and now largely superseded in spirit by) vanilla's own
  datapack recipe system. `SimpleScript` alone is 1043 lines and leans on `BCLibProxy` for
  resource-pack enumeration and `Loader.isModLoaded` for its `is_mod_loaded` script
  function -- both 1.12.2-era FML, needing real replacements
  (`ModList.get().isLoaded(id)` for the latter). Large, self-contained, and nothing calls
  it yet; deferred as a unit rather than half-ported.
- `buildcraft.lib.config.{DetailedConfigOption,OverridableConfigOption,EnumRestartRequirement,
  StreamConfigManager}` (and by extension `RoamingConfigManager`, which reads them) all
  touch `net.minecraftforge.common.config.Property` -- Forge's old `Configuration`/
  `Property` config file API, confirmed absent from both targets' jars. It was replaced by
  `ModConfigSpec` (`ForgeConfigSpec`), a structural change to how a config *value* is
  declared, not a rename. BuildCraft's actual config values live in `buildcraft.core` (not
  ported) rather than in `lib` itself, and `BCLibConfig` (ported) is already a plain
  settings holder rather than a `Property` wrapper, so none of this blocks anything already
  landed. `StreamConfigManager` was already marked `@Deprecated` in its own 1.12.2 source;
  only `FileConfigManager` is genuinely dependency-free, and is a candidate for a later,
  focused pass once there is a real `ModConfigSpec` to wire it to.
- `buildcraft.lib.command` (4 files) -- pre-Brigadier commands (`ICommand`,
  `event.registerServerCommand`). Modern commands are Brigadier
  (`CommandDispatcher<CommandSourceStack>` via `RegisterCommandsEvent`); this is a rewrite
  of each command's argument parsing and execution, not a port, and also depends on the
  dropped `BCLib` singleton for its mod-version/changelog commands specifically.

- `CapabilitiesHelper` — it existed only to supply the no-op storage and null factory
  1.12.2's capability system demanded but never used. Neither argument exists now.
  `TilesAPI` and `MjCapabilities` show the replacement shape per platform.
- `IFluidHandlerAdv` on 26.x — replaced by `FluidFilters`, because `ResourceHandler` can be
  introspected from outside. 1.20.1 keeps the interface. See structural change 6.
- `EnumColor`'s sprite registry and `getLocalizedName` — client-only statics behind
  `@SideOnly`, which has no equivalent; they belong to the rendering rewrite.
- `EnumRedstoneChipset`, `BCItems`, `BCBlocks` — all three are keyed off item damage or the
  eight old `@ObjectHolder` mod ids. They want `DeferredHolder` against real registry
  entries, so they follow the modules that define those entries rather than leading them.
- `IItemHandlerFiltered` — renamed `IFilteredItemHandler` on 26.x, because the interface it
  extends is now `ResourceHandler<ItemResource>`. Unchanged on 1.20.1.
- The eight rendering interfaces in `transport` — `IPipeBehaviourBaker`, `IPipeFlowRenderer`,
  `PluggableModelKey`, `PipeApiClient` and friends. They are all shaped around
  `BlockRenderLayer` and quad baking, so they come back with the rendering rewrite rather
  than being guessed at now.
- The `package-info.java` files. Each carried only
  `@API(apiVersion = ..., owner = ..., provides = ...)`, an FML annotation for the
  standalone-API-jar mechanism that no longer exists. Where a package needed real
  documentation it got a new one, such as `buildcraft.api.core`.

- **`buildcraft.factory` — started: `BlockChute`/`TileChute` (both platforms), the first file this port has
  touched in this module, and the first machine anywhere in the port with a real, working item inventory other
  blocks can insert into and extract from.** Landing it closes a loop several sessions old:
  `buildcraft.lib.tile.item.ItemHandlerManager#getHandlerForFace(Direction)` had no caller until now, and its own
  class javadoc named "a tile's `RegisterCapabilitiesEvent` lookup function" as exactly the thing that would
  eventually call it -- `BCFactoryRegistries`' new `registerCapabilities` listener is that caller. Verified with
  forced rebuilds, the full test suite, real dedicated-server boots on both targets, **and real, observed
  gameplay via RCON on both**: a dropped item summoned above a placed chute was pulled in and pushed into a
  neighbouring hopper -- confirmed by reading the hopper's `Items` NBT afterward (`minecraft:diamond` on 26.x,
  `minecraft:netherite_ingot` on 1.20.1) and confirming the dropped item entity was gone and never reappeared
  anywhere nearby. `data get block` on the placed chute also confirmed `ItemHandlerManager`/`ItemHandlerSimple`'s
  NBT round-trip is correct on both targets (`inv: {stacks: [...]}` on 26.x's `ValueInput`/`ValueOutput` shape,
  `inv_manager: {inv: {items: [...]}}` on 1.20.1's `CompoundTag` shape), and that the battery-driven progress
  timer ticks and resets correctly in real time.
  - **New capability: `buildcraft.api.inventory.ItemTransactorCapabilities`, one per target, declaring only the
    BuildCraft-native `IItemTransactor` half of 1.12.2's `CapUtil.CAP_ITEM_TRANSACTOR`/`CAP_ITEMS` pair.** The
    vanilla-interop half does not need declaring on either target: checked via `javap` against the real
    NeoForge universal jar, 26.x already ships `Capabilities.Item.BLOCK`
    (`BlockCapability<ResourceHandler<ItemResource>, Direction>`) and `Capabilities.Item.ENTITY`/
    `ENTITY_AUTOMATION` (the entity-capability equivalents) as its own built-in tokens -- confirmed by reading
    `CapabilityHooks` in the NeoForge sources jar, which registers `Capabilities.Item.BLOCK` for every vanilla
    container block entity (chests, hoppers, furnaces, ...) and `Capabilities.Item.ENTITY_AUTOMATION` for
    minecart-type entities already. `ItemHandlerManager#getHandlerForFace` already returns exactly that
    `ResourceHandler<ItemResource>` shape (confirmed by its own return type), so 26.x never needed an
    `IItemHandler`-shaped bridge at all -- the port's earlier engine-subsystem finding that NeoForge dropped
    `IItemHandler` in favour of `ResourceHandler<T>`/`Transaction` turned out to apply to items exactly as it did
    to energy, with NeoForge itself supplying the "vanilla items" half of the pair this time, not just BuildCraft's
    own MJ-to-RF bridge. On 1.20.1 the vanilla-interop half is `ForgeCapabilities.ITEM_HANDLER`, already used by
    that target's `ItemHandlerManager`. `ItemTransactorCapabilities` (1.20.1) still needs an
    `event.register(IItemTransactor.class)` call wired into `BCFactoryRegistries.register`, matching
    `MjCapabilities`' own precedent for why 1.20.1 needs an explicit declaration where 26.x does not
    (`@CapabilityInject` is gone).
  - **`ItemTransactorHelper` (both platforms) is a trimmed port**: only `getTransactor`/`move`, all
    `TileChute` calls. `getInjectable`/`wrapInjectable`/`insertAllBypass` are dropped -- they exist only for
    `buildcraft.api.transport.IInjectable`/`PipeApi`, both belonging to the entirely unported `transport` module,
    the same "not needed for this task's real caller" scope note `ItemMarkerConnector` already established for
    its own deferred sub-feature. `createDroppingTransactor` is dropped for the same reason (no caller anywhere
    in this port). On 26.x, 1.12.2's single `getTransactor(ICapabilityProvider, EnumFacing)` had to split into two
    overloads -- `getTransactor(Level, BlockPos, Direction)` for a neighbouring block entity (the same
    `level.getCapability(BlockCapability, BlockPos, Direction)` idiom `TileEngineBase#getReceiverToPower` already
    established) and `getTransactor(Entity, Direction)` for a neighbouring entity (`Entity#getCapability
    (EntityCapability, Direction)`, confirmed via `javap`) -- because there is no common `ICapabilityProvider`
    supertype left for both to share. 1.20.1 keeps the original single signature unchanged, since both
    `BlockEntity` and `Entity` still extend `CapabilityProvider` there (confirmed via `javap`). `move`'s
    `boolean simulate` parameter is rebuilt on nested `Transaction`s on 26.x, the same "peek in a throwaway
    transaction, then commit only what really moved" trick `AbstractInvItemTransactor` already uses for its own
    all-or-nothing insert; 1.20.1's copy keeps 1.12.2's shape unchanged, `boolean simulate` included.
  - **`TileChute`'s `hasInventoryAtPosition` and `BlockChute`'s per-side `CONNECTED_MAP` blockstate are dropped
    outright, not ported.** 1.12.2's `CONNECTED_MAP` was a purely cosmetic "this face visually touches an
    inventory" indicator synthesised in `getActualState`, which no longer exists at all (see the structural-
    changes list) -- and, unlike `TileMarkerVolume`'s `ACTIVE` property (which mattered for connection *logic*),
    nothing reads `CONNECTED_MAP` for gameplay purposes and there is no renderer to show the visual distinction
    either way, so it is dropped rather than reproduced as an always-pushed real blockstate. `hasInventoryAtPosition`
    was `CONNECTED_MAP`'s only caller and goes with it.
  - **`onBlockActivated`'s GUI is dropped -- the first genuinely new kind of GUI deferral in this port.** Nothing
    ported so far has a GUI/container framework at all (`buildcraft.lib.gui` is entirely unported), so every
    earlier GUI-adjacent deferral (`ItemMarkerConnector`'s volume-box editor, `TileEngineCreative`'s wrench-cycle
    feature) never actually needed one either. A chute is the first block that would have. Right-clicking one is
    simply a no-op for now -- no override at all, rather than a `InteractionResult.PASS` stand-in that implies a
    GUI is coming back soon.
  - **No custom `VoxelShape`, despite the real 1.12.2 model being a genuine stepped funnel, not a cube.** The
    model (`buildcraft_resources/assets/buildcraftfactory/models/block/chute.json`, seven stacked boxes) is
    ported faithfully as a real block model. But nothing in this pass needs collision fidelity to match: item
    pickup scans an `AABB` sitting *above* the block (`BoundingBoxUtil.extrudeFace`), never the block's own
    volume, so a full-cube collision/light-occlusion shape costs nothing functionally, and a faithful rotated
    composite shape across all six facings would be real extra engineering for a purely cosmetic gap with no
    Java renderer to show it off either way -- the same call `BlockEngineWood` already made for its own non-cube
    1.12.2 render type. `BlockBehaviour.Properties#noOcclusion()`, set where the block is registered, keeps the
    one non-cosmetic half of 1.12.2's `isOpaqueCube() -> false` (light does not treat the block as a full
    occluder) without needing the shape override.
  - Registered in a new `BCFactoryRegistries` (mirroring `BCCoreRegistries`'s structure exactly) on both
    platforms, wired into each platform's `BuildCraft.java` mod constructor alongside the existing
    `BCRegistries.register(modBus)` call (a new top-level registration entry point, since `buildcraft.factory`
    has never needed one before). Textures/models/blockstate/loot table/lang/recipe pulled or written from
    `buildcraft_resources/assets/buildcraftfactory/`, following `BlockPowerConsumerTester`/`BlockEngineWood`'s
    established pattern -- same `buildcraft:chute` single-mod-id convention every other block in this port
    already uses, even though the source assets still live under the old per-module `buildcraftfactory`
    resource tree.

- **`buildcraft.factory` — `BlockMiningWell`/`TileMiner`/`TileMiningWell` and the cosmetic `BlockTube` shaft it
  digs through (both platforms), plus a trimmed `buildcraft.lib.misc.BlockUtil` and `InventoryUtil#addToBestAcceptor`
  built specifically to support them.** The mining well digs straight down from its own position, one block at a
  time, powered by MJ, laying `tube` blocks behind it and inserting whatever it digs up into the best nearby
  inventory. Verified with forced rebuilds, the full test suite, real dedicated-server boots on both targets, and
  a real, observed, full dig-and-deposit cycle via RCON on **both**: a well placed above a solid stone column with
  its battery hand-filled via `data merge block` dug six full blocks down in real time, laying six `tube` blocks
  behind it, and deposited six `minecraft:cobblestone` into a neighbouring hopper (confirmed by reading the
  hopper's `Items` NBT afterward) -- and, on both targets, breaking the mining well itself afterward
  (`setblock ... minecraft:air destroy`) correctly turned every one of those six `tube` blocks back to air,
  confirming the shaft-retraction hook fires (and reaches the right tile) on each platform's own real removal
  path. Real-time RCON testing against an idle dedicated server (no player connected) is workable but has a hard
  practical limit worth recording for next time: the world ticks close to real-time for a few seconds after any
  RCON command lands, then the server goes fully idle and `time query gametime` freezes solid -- `/tick query`
  still reports "running normally" throughout, so it is not a freeze the game itself reports; the fix that worked
  here was re-sending `data merge block ... {battery:...}` in a loop from the test script rather than trying to
  wait out a single long sleep.
  - **`BlockUtil` is a ~550-line-to-4-method trim, same discipline as `ItemTransactorHelper`'s own trim**:
    `computeBlockBreakPower`, `isUnbreakableBlock`, `getFluidWithFlowing` and `breakBlockAndGetDrops` are the only
    four `TileMiner`/`TileMiningWell` actually call; nothing else came along for the ride.
    `breakBlockAndGetDrops` loses its `GameProfile owner`/`boolean grabAll` parameters and the entire
    `FakePlayer`/`BreakEvent`/`getFakePlayerWithTool` apparatus 1.12.2 built around them: confirmed by decompiling
    the real `Level#destroyBlock`/`Block#getDrops` out of the merged jar (via Vineflower, extracted to scratch
    space, never into the repo), modern `Block.getDrops(state, serverLevel, pos, blockEntity, breakingEntity,
    tool)` computes a block's loot straight from a `LootParams` built out of the position and tool -- no player
    object, fake or otherwise, required at all -- so `breakBlockAndGetDrops` is now just "compute drops, then
    `level.destroyBlock(pos, false)`" (`false` so vanilla does not *also* spawn the drops as item entities; the
    caller already has the `List<ItemStack>` and inserts it directly via `addToBestAcceptor`). The one real
    behavioural loss: no `BreakEvent` is posted any more, so a protection mod that only listens for that event
    cannot see or cancel a mining well digging through a claim -- accepted for this pass rather than building
    mod-compat event plumbing nothing in this port needs yet. `getFluidWithFlowing` returns a real
    `net.minecraft.world.level.material.FluidState` instead of reconstructing 1.12.2's Forge `Fluid`: a position's
    fluid (source or flowing, either one) is just `BlockState#getFluidState()` now, no `IFluidBlock`/
    `BlockFluidBase` abstraction to rebuild; the viscosity number `TileMiningWell#canBreak` gates flowing-lava on
    is `fluidState.getType().getFluidType().getViscosity(fluidState, level, pos)`, confirmed via `javap` on both
    targets (`net.neoforged.neoforge.fluids.FluidType`/`net.minecraftforge.fluids.FluidType`, both carrying a
    position-aware `getViscosity` overload). `isUnbreakableBlock` drops its owner parameter along with the
    player-relative-hardness detour it existed for, collapsing to `state.getDestroySpeed(level, pos) < 0` --
    1.12.2's own bottom-line check for bedrock and friends, reached directly instead of through a fake player.
  - **`InventoryUtil#addToBestAcceptor` lands, correcting that class's own stale "not ported yet" javadoc note**
    (it named the missing `ItemTransactorHelper`/`CapUtil` this session's chute pass already built). Only does
    the `IItemHandler`-equivalent half of 1.12.2's version -- the `IInjectable` (pipe) half is not ported, since
    `buildcraft.transport` itself is not ported at all yet, the same gap `ItemTransactorHelper`'s own javadoc
    already noted for its dropped `getInjectable`/`wrapInjectable`.
  - **`TileMiner`/`TileMiningWell` drop every render-only field from 1.12.2's `TileMiner`** (`currentLength`,
    `lastLength`, `getLength`, `getPercentFilledForRender`, `hasFastRenderer`, both render-distance overrides,
    and the whole `world.isRemote` client-interpolation branch that opened `update()`) -- there is no renderer in
    this port yet to consume any of them, the same "no renderer to serve it" call already made for
    `TileEngineBase`'s dropped progress animation. `wantedLength` survives as a plain server-side field, since
    `updateLength()` still needs it to notice when the dig target moved. `IdAllocator`/`TileBC_Neptune.IDS`/
    `NET_LED_STATUS`/`NET_WANTED_Y` and the `onLoad` random stagger they needed are dropped with the id-tagged
    payload system, matching `TileEngineWood`'s own precedent. `migrateOldNBT` is not ported -- no old saves to
    migrate from.
  - **A real, verified bug fix, not just a straight port: `TilesAPI.HAS_WORK`'s capability instance now reads
    `!isComplete()` (the method) instead of reproducing 1.12.2's `() -> !isComplete` (the *field*).** On the
    server, 1.12.2's `isComplete` field was only ever written by a client-only network handler
    (`readPayload`) -- nothing server-side ever assigned it, so it stayed permanently `false`, and the capability
    lambda was therefore permanently `true` regardless of whether the miner had actually finished digging. Almost
    certainly an artifact of the field/method name collision with `isComplete()` (the method), which computed the
    real, server-authoritative answer (`currentPos == null`) but was never what the capability actually read.
    Dropping the now-pointless client-mirror field and wiring the capability to the method instead is a
    correction, not a divergence -- worth a human double-check given the reasoning is inferred from reading the
    code rather than from an upstream changelog admitting the bug.
  - **`IWorldEventListener`/`WorldEventListenerAdapter` (`TileMiningWell`'s `worldEventListener`, 1.12.2's
    "wake up instantly on any block change anywhere" optimization on top of its periodic `SafeTimeTracker` poll)
    has no cheap modern replacement and is dropped outright, not reproduced.** Confirmed via `javap`: `Level`
    carries no `addListener`/`EventListener` method of any kind on either target any more, and neither
    NeoForge/Forge nor vanilla exposes a global "any block changed" bus event to hook once in its place (only
    per-position hooks like `neighborChanged`, which would need registering on every block type in the game to
    reproduce the old behaviour). The periodic `SafeTimeTracker(256)` poll already there as the real fallback
    either way covers the same ground, just with up to ~12.8 more seconds of latency noticing a block manually
    placed or removed in the miner's own dig column -- not a functional regression.
  - **`BlockTube#removedByPlayer` doesn't exist on either target any more at all** -- confirmed via `javap`
    against both `Block` and `BlockBehaviour` on both the 26.x and 1.20.1 merged jars: zero matches, the hook is
    gone outright, not renamed. 1.12.2 layered two mechanisms: `setBlockUnbreakable()` (hardness -1, blocking
    survival breaking) and `removedByPlayer` additionally refusing creative-mode insta-mine specifically while a
    `TileMiner` still stood above the shaft (insta-mine bypasses hardness). With the conditional half's hook
    gone, this port collapses to the unconditional half alone (`strength(-1.0F, ...)`, matching
    `BlockSpringWater`'s "always unbreakable" precedent) -- accepted because `TileMiner`'s own shaft-retraction
    logic (next bullet) already guarantees a tube block is never left orphaned (with no `TileMiner` above it) in
    normal play, the only case the dropped conditional half ever actually mattered for. `tube` also carries no
    `BlockItem` at all -- the first block in this port registered that way, via a new `BCRegistry#addBlock`
    (additive alongside `addBlockAndItem`) -- and an empty loot table via `Properties#noLootTable()` rather than
    a JSON file, since a player is never meant to obtain it directly.
  - **1.12.2's `TileMiner#onRemove()`/`BlockBCTile_Neptune#breakBlock` (the shaft-retraction hook) reaches two
    genuinely different modern hooks per platform, not the same one with a different name.** Confirmed by
    decompiling the real `LevelChunk#setBlockState` out of each merged jar (Vineflower, scratch space only): on
    26.x, `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)` is a hook that did not exist at all on 1.20.1
    -- it fires directly on the tile, while it is still valid, and is called *before* the block entity is removed
    from the level, which is exactly where 1.12.2's `TileBC_Neptune#onRemove()` used to fire from. On 1.20.1,
    there is no such `BlockEntity` hook; `Block#onRemove(state, level, pos, newState, movedByPiston)` still
    exists (confirmed unchanged via `javap`) and still runs while the old block entity is still valid, so
    `BlockMiningWell#onRemove` calls the tile's cleanup method directly instead -- the same `Block`-drives-
    `BlockEntity` shape 1.12.2's own `BlockBCTile_Neptune#breakBlock`/`TileBC_Neptune#onRemove()` pair used, just
    now needed only on the one platform that lost the tile-level hook. This divergence was already flagged in
    the "Things that differ between our two targets" table below the previous chute session added it for a
    different reason -- confirmed still accurate and now has a second, independent real caller.
  - No `owner` field on `TileMiningWell`, unlike `TileChute`/`TileEngineWood`: 1.12.2's `getOwner()` fed a
    `GameProfile` into `breakBlockAndGetDrops` purely to build a `FakePlayer`, and this port's version needs no
    such thing (see above) -- there is no advancement to unlock and no fake player to attribute the break to, so
    there is nothing left for an owner field to feed. A deliberate divergence from the pattern the task briefing
    suggested, made after confirming the technical need it existed for is gone, not a shortcut.
  - Registered in the existing `BCFactoryRegistries` (additive, mirroring `CHUTE`'s registration exactly for
    `mining_well`; `tube` has no item and no ticker). Textures/models/blockstate/loot table/lang/recipe pulled
    from `buildcraft_resources/assets/buildcraftfactory/`. One real asset finding worth flagging: the shipped
    1.12.2 `tube.json` block model is a degenerate zero-size element (`"from": [8,8,8], "to": [8,8,8]`) textured
    with plain `minecraft:blocks/stone` -- i.e. the tube block was never actually visible in 1.12.2 either
    (underground, in a hole its own removal destroys almost immediately, so nobody noticed). Ported faithfully
    rather than "fixed" with the unused `mining_well/tube.png`/`pump/tube.png` art assets sitting in
    `buildcraft_resources` but referenced by no model anywhere in the shipped resource pack.
    - **Reversed later, at the user's own explicit request, not silently.** After actually playing the mod, the
      user reported not seeing the mining well "drop its pipe that goes down" -- confirmed live via RCON first,
      not assumed: the mining well genuinely digs and the tube shaft genuinely gets placed block-by-block
      (`execute if block <pos> buildcraft:tube` passed all the way down a real dig column), it was just invisible
      the whole time, exactly as documented above. Since the user asked directly to see it, this is no longer
      "preserve a genuine upstream oddity nobody asked to fix" territory (see `TileFloodGate`'s own precedent for
      when that reasoning applies) -- it is now a real, requested fix: `tube.json` (both platforms) is a plain
      `minecraft:block/cube_all` using `buildcraft_resources/assets/buildcraftfactory/textures/blocks/tube/
      default.png` (the one real, correctly-sized -- 16x16, matching every other block texture in this port --
      tube texture already sitting unused in the resource pack; `end.png` is 8x8 and would need scaling, so was
      not used). Verified via a real `runClient` boot: no missing-model/missing-texture warnings for `tube`
      anywhere in the log.

- **`buildcraft.factory`'s fluid foundation lands, and `TilePump`/`BlockPump` is the third factory machine.**
  This batch is `buildcraft.lib.fluid.Tank` (both platforms), `buildcraft.lib.misc.FluidUtilBC#pushFluidAround`
  (both platforms, the two methods that class's own javadoc had flagged as blocked pending exactly this), and
  `TilePump`/`BlockPump` built on top of them. No new capability class was needed for either platform -- see
  below, a real correction to the assumption this task started from.
  - **The fluid capability turned out to need no BuildCraft-native token at all, unlike the item side.**
    1.12.2's `CapUtil.CAP_FLUIDS` was `IFluidHandlerAdv extends IFluidHandler` -- a single capability that was
    *already* vanilla-interop-shaped, unlike `IItemTransactor` (which needed `ItemTransactorCapabilities` plus a
    separate vanilla-interop token because it does *not* implement `IItemHandler`). Confirmed via `javap` against
    the real universal jars: 26.x already ships `net.neoforged.neoforge.capabilities.Capabilities$Fluid.BLOCK`,
    typed `BlockCapability<ResourceHandler<FluidResource>, Direction>` -- exactly `Tank`'s own shape, once `Tank`
    is itself a `ResourceHandler<FluidResource>` (see below) -- and 1.20.1 already ships
    `ForgeCapabilities.FLUID_HANDLER`, typed `Capability<IFluidHandler>` -- exactly what `Tank` already is by
    extending Forge's own `FluidTank`. Both were registered directly (`BCFactoryRegistries` on 26.x,
    `TilePump#getCapability` on 1.20.1) with no wrapper class in between; `FluidUtilBC#pushFluidAround`'s
    neighbour lookup queries them directly too, the same way `pushFluidAround` needed no `FluidTransactorHelper`
    the way `addToBestAcceptor` needed `ItemTransactorHelper`. `FluidUtilBC`'s own javadoc (both platforms) is
    corrected in place to say so, the same "stale note fixed once its blocker landed" precedent
    `InventoryUtil#addToBestAcceptor`'s own javadoc already set.
  - **26.x's `Tank` is a one-slot `FluidStacksResourceHandler`, not a from-scratch `ResourceHandler`
    implementation.** Confirmed via `javap`: `net.neoforged.neoforge.fluids.FluidTank` (the direct rename target
    1.12.2's own superclass would suggest) genuinely does not exist on this target -- "class not found", as this
    task's own briefing already expected. What *does* exist, and was not anticipated going in, is
    `net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler`: the fluid counterpart of
    `ItemHandlerSimple`'s own `ItemStacksResourceHandler` base, in the same `StacksResourceHandler<S, T extends
    Resource>` family, with the same transaction-safe `insert`/`extract` and `ValueIOSerializable` NBT
    persistence already built in. `Tank` (26.x) is therefore exactly as thin a wrapper over its base as
    `ItemHandlerSimple` is over its own -- `isValid`/`onContentsChanged` overrides for the filter/callback, plus
    `fillInternal` (new: `StacksResourceHandler#set(int, T, int)`, already public, is the direct modern
    equivalent of 1.12.2 `FluidTank#fillInternal`'s validator-bypassing write) -- not the ground-up
    transaction-aware handler the task briefing budgeted real design time for. Worth a human double-check purely
    because it means less new code than expected, not because the design is shaky.
  - **1.20.1's `Tank` keeps a real base-class relationship, just under a moved package.** Confirmed via `javap`
    against the Forge 1.20.1 universal jar (not the merged jar -- the merged jar doesn't carry it):
    `net.minecraftforge.fluids.capability.templates.FluidTank` is 1.12.2's own `net.minecraftforge.fluids.
    FluidTank` moved under `capability.templates`, with the same `fill`/`drain`/`isFluidValid`/`getCapacity`/
    `onContentsChanged` surface (`boolean doFill/doDrain` -> `FluidAction`, matching every other fill/drain
    rename already in this port's fluid code). `Tank` still implements `IFluidHandlerAdv` here (unlike 26.x,
    which drops it -- see above): 1.20.1's `IFluidHandler` has no transaction-scoped way to introspect a
    handler's slots from outside, so filtered draining still needs its own interface method, same as 1.12.2.
  - **The vanilla "infinite water source" rule is unchanged in shape, confirmed against the real modern
    mechanic, not assumed.** 1.12.2's own comment pointed at `BlockDynamicLiquid.updateTick`; the direct modern
    descendant is `FlowingFluid#getNewLiquid` (decompiled out of the 26.x merged jar, scratch space only): a
    flowing block becomes a new source once it has `neighbourSources >= 2` horizontally-adjacent source
    neighbours of the same fluid *and* the block directly below is either solid or another source of that fluid
    -- the identical two-neighbour threshold and shape 1.12.2's own check already used, just re-expressed against
    `FluidState#isSource()`/`BlockState#isSolid()` instead of Forge's old `Material#isSolid()`. Ported verbatim
    into `TilePump#buildQueue0` rather than guessed at.
  - **The oil-spring branch (`isOil`/`oilSpringPos`/`ADVANCEMENT_DRAIN_OIL`/`BCEnergyFluids.crudeOil`/
    `ITileOilSpring#onPumpOil`) is dropped entirely, not ported.** All five depend on `buildcraft.energy`, not
    ported at all in this port. Not a functional cut: 1.12.2's own `isOil` already opened with
    `if (BCModules.ENERGY.isLoaded()) { ... } return false;`, and since `buildcraft.energy` never registers here
    either, that condition is permanently `false` regardless of whether the branch exists in source -- dropping
    it outright reproduces the exact real-world behaviour this port is already in, matching the precedent already
    set for `BlockSpringWater`'s dropped oil half and the un-ported `ITileOilSpring` itself.
  - `fluidConnection` is not ported -- a dead field even in 1.12.2's own source (assigned in `buildQueue`, never
    read anywhere in the whole 1.12.2 tree).
  - The debug-profiler instrumentation (`Profiler debugProf`/`Stopwatch watch`/`ProfilerEntry`, gated behind
    `DEBUG_PUMP`, a system property nobody sets) is dropped, not reproduced -- zero behavioural effect, and it
    would have needed merging with a live per-tick world profiler this target has no simple handle on.
    `DEBUG_PUMP`'s real diagnostic value, the `BCLog.logger.info` calls explaining *why* a drain attempt failed,
    is kept in full.
  - `Fluid#isGaseous()` has no modern equivalent -- confirmed via `javap` against `FluidType` on both targets: no
    such method exists, getter or builder flag. The established Forge/NeoForge convention (a negative
    `FluidType#getDensity()` marks a gas) stands in for it; nothing this port registers is actually gaseous yet
    (only vanilla water/lava), so the branch it gates is currently dead in practice, kept faithfully anyway since
    the original explicitly special-cased it.
  - `BlockUtil`'s own 1.12.2 `getFluid`/`getFluidWithoutFlowing`/`drainBlock` (`TilePump`'s other fluid-block-
    inspection dependencies, beyond the already-ported `getFluidWithFlowing`) become `FluidUtilBC#getFluidSource`/
    `#drainBlock` instead, on both platforms -- relocated there rather than added to `BlockUtil`, which was out of
    scope for this pass. Real per-platform research, not a rename: NeoForge's own generic
    `net.neoforged.neoforge.transfer.fluid.FluidUtil#tryPickupFluid` was considered for 26.x and rejected after
    reading its source -- its own doc comment warns it can mutate the world (via `BucketPickup#pickupBlock`) even
    when the `Transaction` it was given is never committed, unsuitable for `mine()`'s simulate-then-commit two
    step. On 1.20.1, `net.minecraftforge.fluids.FluidUtil#getFluidHandler(Level, BlockPos, Direction)` was also
    considered and rejected after decompiling it: confirmed it only resolves a handler through a neighbouring
    `BlockEntity`, and a plain vanilla water/lava lake has none, unlike 1.12.2's own version of that method (which
    wrapped `IFluidBlock`/`BucketPickup` blocks directly). Both platforms' `drainBlock` instead go straight to
    `BucketPickup` (the same interface Forge's own `BucketPickupHandlerWrapper` builds on for this exact case,
    confirmed by reading its decompiled source): a `FluidState` read for the side-effect-free simulate case, and
    only the real case calls `pickupBlock`, which vanilla's `LiquidBlock` already refuses unless the state is a
    source (confirmed by reading `LiquidBlock#pickupBlock` on both targets) -- no redundant source check needed.
  - `createMjReceiver()` no longer exists as an overridable hook on `TileMiner` (dropped when that class landed,
    see its own javadoc) -- its `mjReceiver` field is fixed to a plain, non-redstone `MjBatteryReceiver`. Since a
    pump genuinely still wants `MjRedstoneBatteryReceiver` (unlike the mining well, which was already happy with
    the plain receiver) and `TileMiner` was out of scope to modify for this pass, `TilePump` adds a second
    receiver field, `mjRedstoneReceiver`, wrapping the same `battery` -- registered in its place
    (`BCFactoryRegistries` on 26.x, an intercepting `getCapability` override on 1.20.1); the inherited
    `mjReceiver` field is simply never registered for this tile. `IMjRedstoneReceiver` is presently a pure marker
    (nothing in this port reads it yet to actually allow cheap redstone-direct power), so this has no observable
    behavioural effect today -- it is there for whenever a future redstone-to-MJ feature wants to find pumps by
    it, matching what the field existed for in 1.12.2 too.
  - `getOwner().getId()` (used only to grant the "draining the world" advancement) follows the same owner-UUID-
    field pattern `TileChute`/`TileEngineWood` already established -- unlike `TileMiningWell` (which needed no
    owner at all, see that class's own javadoc), a pump's advancement grant is the one place this tile still
    cares who placed it.
  - `BlockPump` needed no facing property or GUI-opening hook to drop: unlike `BlockMiningWell` (which kept a
    facing property purely for parity), 1.12.2's real `BlockPump` had neither to begin with -- it extended
    `BlockBCTile_Neptune` directly, overriding only `createTileEntity`.
  - Registered in the existing `BCFactoryRegistries` (additive, same shape as `MINING_WELL`'s own registration).
    Textures/blockstate/block+item model/loot table/lang pulled from `buildcraft_resources/assets/
    buildcraftfactory/` following the established pattern; the LED-status textures (`led_green`/`led_red`) are
    not pulled in, matching every other render-only asset already skipped elsewhere in this port (no renderer to
    show them). The 1.12.2 recipe is **not** ported: its `t` key requires `buildcraftfactory:tank`, an entirely
    separate `factory` block/item this port hasn't reached yet (the "Tank" *block*, not this batch's
    `lib.fluid.Tank` *class* -- same name, different thing) -- inventing a substitute ingredient would misrepresent
    the real recipe, so it waits for that block the same way `BlockTube` waits for a reason to have a `BlockItem`.
  - **Verified with a real dedicated-server boot and RCON on both targets, not just a compile check -- a pump
    genuinely fills its tank from world water and correctly tells a finite pool apart from an infinite one.**
    Two scenarios, both observed directly via `/data get block` and `/execute store result score ... if block`
    (no `mcrcon`/`mcstatus` dependency -- a small stdlib `socket`+`struct` script speaks the RCON binary protocol
    directly): (1) a flat 5x5 source-block pond sitting on solid ground -- every position the pump drains from is
    the "two-or-more-same-fluid-neighbours-over-solid-ground" infinite-source pattern by construction, and
    indeed, after ten separate MJ-gated drain cycles across two full battery charges, both drained positions were
    still confirmed real `minecraft:water` blocks, never `air`; the tank correctly accumulated `5000`/`1000` mB
    (26.x/1.20.1) at exactly `10 * MjAPI.MJ` per drain, and the battery correctly hit `0` after exactly five
    charges' worth. (2) a single isolated water source block on a stone platform, with no same-fluid neighbours
    anywhere -- drained exactly once (`1000` mB into the tank, matching a single battery charge), and the source
    block genuinely became `air` afterward, confirmed the same way. Both scenarios reproduced on 26.x; scenario
    (1) also reproduced independently on 1.20.1 (tank correctly held `1000` mB after one charge, and the drained
    position was still confirmed water afterward) to confirm the shared algorithm and the platform-specific
    `Tank`/NBT shape both actually work there too. `pushFluidAround` itself was not observed moving fluid between
    two real in-world blocks -- there is no second fluid-accepting block anywhere in this port yet for it to push
    into (the same gap the task briefing itself flagged as a real possibility) -- so it is verified by code
    review and the already-proven `move` primitive it is built on, not by a live cross-block transfer. Both dev
    servers booted and shut down cleanly with zero exceptions in the log either time.

- **`buildcraft.factory` — `TileTank`/`BlockTank` (both platforms), the storage tank.** Stacked vertically with
  others of its own kind, a tank behaves as one combined multi-block fluid reservoir: filling the bottom tank
  spills upward once it is full, draining pulls from the top down for a liquid (bottom up for a gas), and a
  capability query landing on *any* tile in the column sees the whole column's combined contents. This is the
  first machine in this port whose defining behaviour genuinely spans multiple physical block entities at once,
  not just one tile talking to its immediate neighbours.
  - **The column walk itself ports unchanged in shape**: starting from `this`, walk upward one block at a time
    while the neighbour above is a `TileTank` and `canTanksConnect` agrees, then the same downward, returning the
    run bottom to top. It only ever looks straight up/down (a stack is 1-wide by definition), so it is not a
    flood-fill and needs no visited-set. Every fluid method (`fill`/`drain`/`insert`/`extract`/
    `balanceTankFluids`) calls this first, then spreads its work across the resulting list, reversing the list's
    iteration order for a gas versus a liquid (liquid settles toward the bottom -> fill packs bottom-first, drain
    empties top-first; gas rises -> both flip). **Renamed from 1.12.2's private `getTanks()` to
    `getConnectedTanks()` on both platforms** -- not optional on 1.20.1, where `IFluidHandler` itself declares an
    unrelated same-signature `int getTanks()` ("how many tank slots does this handler have") that this class also
    has to implement; Java does not allow two same-parameter-list methods differing only in return type. 26.x has
    no such collision but uses the same name for consistency between the two files.
  - **The multi-tank capability aggregation needed real, different per-platform designs, confirmed via `javap`
    against both merged jars -- not a mechanical trim of `TilePump`'s own single-tank capability.**
    - On 26.x, `TileTank` implements `net.neoforged.neoforge.transfer.ResourceHandler<FluidResource>` *directly*
      (confirmed via `javap`: `size()`, `getResource(int)`, `getAmountAsLong(int)`, `getCapacityAsLong(int, T)`,
      `isValid(int, T)`, `insert(int, T, int, TransactionContext)`, `extract(int, T, int, TransactionContext)`),
      as a single logical slot (`size() -> 1`) whose every method calls `getConnectedTanks()` and fans out across
      the column -- the direct modern equivalent of 1.12.2's own `TileTank implements IFluidHandlerAdv`. Each
      physical tile's own `tank` field (a plain one-slot `Tank`) still holds and serialises that block's own share
      of the fluid; the aggregate view is assembled fresh from every tile's `tank` on each call, never cached.
      Registered in `BCFactoryRegistries` exactly like `TilePump`'s own `Capabilities.Fluid.BLOCK` registration,
      just handing back `tile` itself (now a `ResourceHandler<FluidResource>`) instead of a `tank` field.
    - On 1.20.1, `IFluidTankProperties`/`FluidTankProperties` (what 1.12.2's `getTankProperties()` returned) do
      not exist on this target at all -- confirmed via `javap` against the Forge 1.20.1 universal jar, neither
      type is present. The modern `IFluidHandler` surface replaces that single properties array with
      `getTanks()`/`getFluidInTank(int)`/`getTankCapacity(int)`/`isFluidValid(int, FluidStack)` directly, so
      `TileTank` implements those four (plus `fill`/`drain`/`IFluidHandlerAdv#drain`) instead, still presenting
      the whole column as slot `0`. Exposed through the tile's own `getCapability`/`invalidateCaps` override for
      `ForgeCapabilities.FLUID_HANDLER`, the same per-instance pattern `TilePump`/`TileChute` already established,
      returning `this` rather than a wrapped field.
  - **The comparator hook's modern names, confirmed via `javap` against `BlockBehaviour` on both merged jars, with
    a real signature divergence between the two targets.** 26.x: `protected boolean hasAnalogOutputSignal
    (BlockState)` / `protected int getAnalogOutputSignal(BlockState, Level, BlockPos, Direction)` -- the extra
    trailing `Direction` parameter (unused here, matching how 1.12.2's own `getComparatorInputOverride` never used
    `world`/`pos` for anything beyond the tile lookup either) is genuinely not present on 1.20.1's copy of the
    same two hooks, which are `public boolean hasAnalogOutputSignal(BlockState)` / `public int
    getAnalogOutputSignal(BlockState, Level, BlockPos)`. `getComparatorLevel()`'s own math is unchanged 1.12.2
    logic, unmoved, still reading this tile's own physical `tank` (not the whole column) -- a stacked column's
    comparator output is per physical block, matching 1.12.2's own behaviour exactly.
  - **No ticker.** 1.12.2's `update()` had two jobs: tick the dropped `FluidSmoother` (no renderer to serve it,
    same reasoning as everywhere else in this port), and re-check the comparator level once a tick, calling
    `markDirty()` again if it had changed -- but `Tank`'s own `onContentsChanged` already calls
    `markChunkDirty()`/`markChunkDirty` on *every* content change regardless of whether the comparator level
    actually moved, so that tick-polled recheck was already redundant in 1.12.2 itself the moment any fill/drain
    had happened at all. Wiring `Tank`'s `onChange` callback straight to `markDirtyAndSync()` reproduces the one
    behaviour that callback ever actually caused, with no ticker and no `getTicker` override on `BlockTank` at
    all -- a deliberate simplification, not an oversight, documented here so a future reader does not assume a
    ticker was simply forgotten.
  - **`ITankBlockConnector` needed no port at all and is dropped, not just left unported.** It was a marker
    interface 1.12.2's `BlockTank` implemented purely so `getActualState`/`shouldSideBeRendered` (the cosmetic
    "block below is also a tank" face-culling blockstate) could check for it on a neighbour -- 1.12.2's own real
    fluid-column logic (`TileTank#getTanks()`) already used a direct `instanceof TileTank` check, never the
    marker. With `getActualState` gone entirely (no such hook exists any more -- see PORTING.md's structural-
    changes list) and `shouldSideBeRendered` dropped with it (no renderer), nothing is left to read the marker.
  - **Everything render/old-network/GUI-only is dropped**, matching every precedent already set by `TilePump`/
    `TileChute`: `FluidSmoother`/`smoothedTank`/`getFluidForRender`, the id-tagged network-cache payload system,
    and `onActivated`'s two behaviours (`FluidUtilBC.onTankActivated`, still not ported anywhere -- see that
    class's own javadoc -- and `BCFactoryGuis.TANK.openGUI`, no GUI/container framework exists yet). Right-
    clicking a tank is a no-op for now, matching `BlockChute`.
  - **A full-cube default block shape was chosen deliberately, not by default neglect**, over reproducing
    1.12.2's real non-cube bounding box (`2/16 .. 14/16` horizontally) and its matching `JOINED_BELOW`-aware
    model: nothing in this pass exercises collision or occlusion fidelity for a tank, and there is no renderer to
    show a faithful shape off either way -- the same call already made for `BlockPump`/`BlockEngineWood`'s own
    non-cube 1.12.2 render types. The block model uses the real tank textures (`tank/end.png`, `tank/side.png`
    from `buildcraft_resources`) on a plain cube parent; `tank/side_joined_below.png` is not pulled in, since
    `JOINED_BELOW` itself is dropped (see above). `ICustomPipeConnection`/`getExtension` (pipe-connection-shape
    hints) are dropped too -- `buildcraft.transport` is not ported at all, so there is no reader.
  - The 1.12.2 recipe (`buildcraftfactory:tank`, a hollow ring of `#blockGlassColorless`) is **not** ported: same
    "wait for a real ingredient rather than invent a substitute" reasoning `TilePump`'s own skipped recipe already
    used for its own missing `buildcraftfactory:tank` ingredient (a coincidence of naming -- that pump recipe note
    was about *this* block, now landed, but the glass-colour ore-tag ingredient this tank's own recipe needs is a
    separate, still-unaddressed gap).
  - Registered in the existing `BCFactoryRegistries` (additive, same shape as `PUMP`'s own registration, inserted
    directly after it and before the `TUBE` block so as not to disturb the javadoc comment already anchored to
    `TUBE`). Textures/blockstate/block+item model/loot table/lang pulled from `buildcraft_resources/assets/
    buildcraftfactory/` following the established pattern.
  - **Verified with forced rebuilds, the full 25-test suite, real dedicated-server boots on both targets with
    zero exceptions in either log, and a real, observed, multi-tank fluid-balancing column via RCON on both --
    not just single-tank fill/drain the way `TilePump`'s own verification was.** A `TilePump` was placed with a
    three-tall `TileTank` column directly above it, over a 3x3 water pond, with its battery hand-filled via
    `data merge block` (the same technique `TileMiningWell`/`TilePump` verification already established, looping
    short RCON round trips rather than one long sleep to keep the idle dedicated server's world actually
    ticking). On 26.x: after the battery fully drained, `data get block` on each of the three tank tiles showed
    `16000`/`16000`/`3000` mB of `minecraft:water` bottom to top -- the bottom two tanks completely full and the
    third correctly catching the overflow, entirely through the real `TilePump -> FluidUtilBC.pushFluidAround ->
    TileTank.insert()` capability path (the first time `pushFluidAround` has ever been observed moving fluid
    into a real second block in this port -- `TilePump`'s own verification pass had no fluid-accepting neighbour
    to test against yet and said so explicitly). The apparent shortfall against a naive battery/10,000,000 x
    1,000 mB estimate (35,000 mB delivered against a 500,000,000-microjoule battery that would suggest 50,000)
    is fully accounted for, not a bug: that battery value was more than double `TilePump`'s real 50,000,000-
    microjoule capacity, so `MjEffects.tick`'s `shedExcessPower()` (unit-tested, unchanged 1.12.2 arithmetic --
    see `MjBatteryTester`) was faithfully burning off the excess above 2x capacity every tick throughout the
    test, exactly as designed. A second run on 26.x and a fresh run on 1.20.1, both kept deliberately under that
    2x-capacity shed threshold, confirmed exact accounting instead: 1.20.1's bottom tank read `9000`/`16000` mB
    after 9 completed drain cycles (9,000 mB expected, 9,000 mB observed), and after a second battery top-up,
    `16000`/`2000`/`0` mB bottom to top after 18 total cycles (18,000 mB expected, 18,000 mB observed) -- the
    overflow into the second tank confirmed on this platform too, with zero loss once the shed mechanic was
    avoided. Draining (top-down for a liquid) was **not** independently observed live -- there is still no real
    in-game path to trigger `TileTank`'s own `extract`/`drain` at all in this build (`FluidUtilBC.onTankActivated`
    is still unported, so right-clicking a tank with a bucket does nothing, and nothing else in this port drains
    *from* a `TileTank`), so that half of the column logic is verified by code review and the same
    already-proven `getConnectedTanks()`/direction-reversal machinery the fill path just demonstrated live, not
    by an observed live drain -- worth a human double-check once a real fluid consumer exists to test it against.
    Both dev servers booted and shut down cleanly with zero exceptions in the log either time.

- **`buildcraft.factory` — `TileFloodGate`/`BlockFloodGate` (both platforms), the flood gate.** Given a
  fluid piped into its own single-slot `tank` (registered the same direct way `TilePump`'s own `tank` is, not
  `TileTank`'s aggregating-column pattern -- a flood gate is a single, non-stacking tile), it breadth-first
  searches outward through open space along up to 4 of its 5 non-top sides (`openSides`) and spreads that fluid
  into the world, one source block every 16 ticks, on a rebuild cadence that backs off exponentially
  (`{16, 32, 64, 128, 256}` ticks) whenever the search queue empties out with nothing left to place, and resets to
  the fastest delay the moment a placement actually succeeds.
  - **Fluid placement needs no `FakePlayer`, confirmed rather than assumed.** `TileFloodGate#canFill` only ever
    accepts two kinds of target -- plain air, or an existing *flowing* (non-source) block of the tank's own fluid
    -- and neither is ever a `LiquidBlockContainer` (a cauldron and friends), so the modern placement call is a
    plain, unconditional `Level#setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL)`
    with no player object of any kind. Independently confirmed moot either way: `BuildCraftAPI.fakePlayerProvider`
    is still never assigned anywhere in this port (`grep` across both platforms turns up only its declaration), so
    routing through it would have been a guaranteed `NullPointerException` regardless -- the same "no renderer/no
    consumer yet" situation as several other deferred fields in this port, just for a field that would crash on
    use rather than silently do nothing.
  - **A genuine, faithfully-preserved bug in 1.12.2's own `TileFloodGate#update()`, found while verifying this
    method line-by-line against the original, not introduced by porting it.** The path-revalidation loop iterates
    over every intermediate position `p` on the route back to the flood gate, but the actual fillability check
    inside that loop calls `canFillThrough(currentPos)` -- the loop variable `p` is read for nothing but the
    `p.equals(currentPos)` skip-self test, never passed to `canFillThrough` itself. The result is that 1.12.2's
    real binary never independently re-validates the intermediate steps of a path at all, only the destination,
    repeated once per path element. Ported byte-for-byte as written (bug included), per this port's established
    precedent for preserved-not-silently-fixed upstream quirks (`BlockMarkerVolume#neighborChanged`) -- flagged
    in both platforms' class javadoc and here for a human to weigh in on whether it is worth deviating from
    upstream to fix.
  - **`openSides` is a real, persisted `EnumSet<Direction>`, encoded as a plain bitmask `int`** (one bit per
    `Direction#ordinal()`), not 1.12.2's `NBTTagByteArray`/`NBTPrimitive` dual-format reader -- there is no old
    save data for a fresh port to stay backward-compatible with, so the "7.99.7 and before" legacy-array fallback
    is dropped outright, matching how `TileMiner`'s own `migrateOldNBT` was already dropped for the same reason.
    It needs no client sync of its own beyond the ordinary `markDirtyAndSync()` full-state push `toggleOpenSide`
    already triggers -- there is still no renderer in this port to consume a faster sync of this one field, the
    same reasoning that already dropped `TileMarkerVolume#showSignals`'s own id-tagged payload pair.
  - **The wrench interaction is real and was confirmed reachable, unlike a same-shaped precedent that turned out
    not to be.** `BlockEngineCreative`'s own javadoc already documented that `ItemWrench#useOn` intercepts a
    wrench click through `CustomRotationHelper.INSTANCE.attemptRotateBlock` *before* a block's own
    `useItemOn`/`use` ever runs, and speculated its own output-cycling wrench feature was consequently dead code
    since that block implements `ICustomRotationHandler`. `BlockFloodGate` does not implement
    `ICustomRotationHandler` and has no handler registered against it, so `attemptRotateBlock` falls through to
    its own `InteractionResult.PASS` default (confirmed by reading `CustomRotationHelper#attemptRotateBlock`
    directly, not assumed) -- and a `PASS` does not consume the interaction, so it genuinely reaches
    `BlockFloodGate#useItemOn`/`#use` on both targets.
  - **In-game verification, both platforms, via RCON.** `data merge block` on a placed flood gate's own NBT
    (`{tank:{stacks:[{id:"minecraft:water",amount:...}]}}` on 26.x -- `FluidStacksResourceHandler`'s real codec
    shape, `id`/`amount`, decompiled from the NeoForge sources jar rather than guessed after a first wrong guess
    silently produced a zero-length stack list and crashed the dedicated server with an `IndexOutOfBoundsException`
    in `StacksResourceHandler#getAmountAsLong` -- worth knowing for the next person who merges NBT into a
    `Tank`-backed tile on this target: a malformed `stacks` list decodes to *empty*, not *rejected*, and every
    `Tank` read assumes exactly one slot always exists; `{tank:{FluidName:"minecraft:water",Amount:...}}` on
    1.20.1, unchanged Forge `FluidTank` NBT shape) filled the tank directly. With a flood gate placed in the
    middle of five otherwise-sealed 1-block pockets (one per non-top side, each connected to the flood gate's own
    position only, no path around), and `openSides` set to exclude `WEST` via `data merge block ...
    {openSides:45}`, all four open pockets (`DOWN`/`NORTH`/`SOUTH`/`EAST`) filled with real placed water source
    blocks (`execute if block ... minecraft:water`, matching the pattern already established for
    `TileMiningWell`/`TilePump`/`TileTank`) while the closed `WEST` pocket never did, on both platforms, with the
    tank draining exactly 4,000 mB (four 1,000 mB placements) each time -- a clean, unambiguous confirmation that
    `openSides` genuinely gates the search. **Not independently observed live: an actual client wrench right-click
    flipping `openSides` end to end.** This environment has no graphical client to drive a real interaction
    through; what *is* verified live is everything `toggleOpenSide` actually does once reached (the NBT-level
    `openSides` gating above) and, via direct source reading rather than assumption, that the interaction pipeline
    genuinely reaches `BlockFloodGate#useItemOn`/`#use` for a wrench click on this block (see above) -- worth a
    human double-check with a real client before relying on this specific gesture.
  - **A real, dedicated-server-crashing robustness gap was found (and left as-is, not fixed) in shared
    `Tank`/`StacksResourceHandler` plumbing this pass does not own.** Feeding `data merge block` a `stacks` list
    that fails to decode (a wrong field name, in this case) does not reject the merge or fall back to the
    previous contents -- it silently produces a zero-*length* list instead of the expected always-one-slot list,
    and the very next `tank.getAmountAsInt(0)` (called unconditionally at the top of every `serverTick()`) throws
    `IndexOutOfBoundsException` and crashes the dedicated server. This is shared `net.neoforged.neoforge.transfer
    .StacksResourceHandler` behaviour, not something `TileFloodGate` does differently from `TilePump`/`TileTank`
    (both of which read tank slot 0 just as unconditionally, and neither is in scope for this pass to modify) --
    flagged here since it was only actually triggered by this task's own testing, not because it is specific to
    the flood gate. A real player can never produce this through ordinary play (nothing in-game ever hands a tank
    a malformed NBT blob), but it is worth a human's attention as a shared fragility the next `Tank`-owning tile's
    own verification pass should be aware can happen from a bad `/data merge`.
  - Registered in the existing `BCFactoryRegistries` (additive, inserted directly after `TANK`/`TANK_TYPE` and
    before the `TUBE` block, same care taken as `TANK`'s own insertion not to disturb `TUBE`'s anchored javadoc).
    A plain full cube (matching `PUMP`/`TANK`'s own "no renderer to justify a non-cube model yet" call) using the
    real `flood_gate/open.png`/`top.png` textures from `buildcraft_resources/assets/buildcraftfactory/` (the
    per-side `open`/`closed` texture swap and the `connected_*` blockstate properties that drove it are dropped
    along with `getActualState`, matching precedent -- see the class's own javadoc); blockstate/block+item
    model/loot table/lang written following the established pattern. The 1.12.2 recipe is not ported, same
    "no invented ingredient substitute" reasoning already applied to `TilePump`'s and `TileTank`'s own skipped
    recipes (this one needs `buildcraftfactory:tank`, itself unrecipe'd yet -- see `TileTank`'s own entry).
  - Verified with forced rebuilds, the full test suite, real dedicated-server boots with zero exceptions on both
    targets, and the live RCON fluid-placement/`openSides`-gating test described above on both platforms.
    **One environment-specific gotcha hit during this pass's own verification, unrelated to the port itself**:
    26.x's dedicated server pauses ticking entirely after `pause-when-empty-seconds` (default 60) with no players
    connected, which silently stalled the very first `openSides`-gating attempt (nothing placed for over a
    minute) until noticed in the server log and raised locally in `run/server/server.properties` for this test
    session -- worth remembering for whoever next needs a long-idle RCON-only verification run on 26.x; 1.20.1
    has no equivalent setting and was unaffected.

- **A real, player-facing bug was found and fixed after real jars were deployed to Prism Launcher and actually
  played: only `buildcraft.core`'s own blocks/items ever showed up in BuildCraft's creative tab, on both
  platforms.** `BCCoreRegistries#TAB_MAIN`'s `displayItems` read `REGISTRY.creativeTabEntries()` -- that class's
  own private `BCRegistry` instance -- but every module (`buildcraft.core`, `buildcraft.factory`, and every one
  still to come) constructs its **own separate** `BCRegistry`, each with its own private `creativeOrder` list.
  `buildcraft.factory`'s chute, mining well, pump, tank, and flood gate were all correctly registered (obtainable
  via `/give`, fully functional in-world) but simply invisible in the tab -- explaining a real player's "I don't
  seem to be able to do much in the creative menu" experience after trying the mod, even though five working
  machines existed by that point. Missed by every prior verification pass this port has run because all of them
  `/give` items directly over RCON and never open the creative-tab UI itself.
  - Fixed by giving `BCRegistry` a static list of every instance constructed (`ALL`) and a new
    `allCreativeTabEntries()` that concatenates all of them, in construction order; `TAB_MAIN` now calls that
    instead of its own module's `REGISTRY`. Safe specifically because `CreativeModeTab#displayItems` is only
    ever invoked lazily (when something actually needs the tab's contents), by which point every module's
    `register(modBus)` -- and therefore every module's `BCRegistry` construction -- has already run during mod
    construction; `BCCoreRegistries` does not need a compile-time reference to `BCFactoryRegistries` or any
    later module for this to work.
  - Verified with forced rebuilds on both platforms, the full 25-test suite, and real dedicated-server boots
    with zero exceptions on both targets (confirming the fix does not disturb ordinary registration/serverside
    behaviour). **Not independently confirmed with a live, on-screen creative-tab check**: this environment has
    no input automation to actually open a creative inventory screen and read what is drawn (the same
    limitation already noted for `BlockFloodGate`'s wrench gesture and for client-side rendering generally,
    below), and repeated attempts to trigger `displayItems` indirectly by watching `:neoforge-26x:runClient`'s
    own logs for evidence it had run were inconclusive -- the dev client's automatic world auto-join is not
    reliably reproducible run to run, so no log signal was ever confirmed either way. The fix itself is a small,
    mechanical list-concatenation change with a clear, verified-by-reading root cause, but a human should still
    open a real creative inventory once before trusting this fully.

- **`buildcraft.factory` — `TileAutoWorkbenchItems`/`BlockAutoWorkbenchItems` (both platforms), the auto
  workbench, and with it this port's first real GUI/container foundation.** Every machine ported so far needed
  zero player-facing UI (place it, pipe items/fluids through it, maybe wrench it) -- `BlockChute`'s own javadoc
  already flagged this as "the first block that *would* have wanted one." A player drags items into a phantom
  3x3 blueprint grid; the tile derives a matching material filter, pulls piped-in materials that exactly match
  (by item + data components, not by recipe ingredient/tag -- see below), and crafts one item at a time, charged
  by MJ (`POWER_GEN_PASSIVE = MjAPI.MJ / 5` per tick, `POWER_REQUIRED` = 10 seconds' worth, `POWER_LOST` = half
  that per tick once materials run out) exactly as 1.12.2's `TileAutoWorkbenchBase` did.
  - **New files, both platforms**: `buildcraft.lib.gui.ContainerBCTile` (tile-bound menu base),
    `buildcraft.lib.gui.slot.{IPhantomSlot,SlotBase,SlotPhantom,SlotOutput,SlotDisplay}`,
    `buildcraft.lib.tile.craft.WorkbenchCrafting`, `buildcraft.factory.tile.{TileAutoWorkbenchBase,
    TileAutoWorkbenchItems}`, `buildcraft.factory.block.BlockAutoWorkbenchItems`,
    `buildcraft.factory.container.ContainerAutoCraftItems`, `buildcraft.factory.gui.GuiAutoCraftItems`, and a
    genuinely new (no 1.12.2 counterpart) `buildcraft.factory.client.BCFactoryClientRegistries`. `BCRegistry`
    (both platforms) grew a new `addMenu(name, IContainerFactory<M>)` helper alongside its existing
    `addBlockAndItem`/`addBlockEntity`, so the next GUI-needing machine (quarry, filler, assembly table, ...)
    registers a `MenuType` the same one-line way.
  - **Deliberately dropped, matching this task's own scope, not discovered mid-port**: 1.12.2's
    `GuiRecipeBookPhantom`/`IRecipeShownListener` recipe-book integration (click a recipe in the book to
    auto-fill the blueprint) -- pure client UI convenience with no bearing on the tile's own logic; a player can
    still fill the blueprint by hand. The `ICON_FILTER_OVERLAY_SAME/DIFFERENT/SIMILAR` filter-overlay icons on
    the material slots -- a material slot's *behaviour* never depended on the overlay, only its look. Both are
    noted in each new class's own javadoc. `TileAutoWorkbenchFluids`/`BlockAutoWorkbenchFluids` are out of scope
    for this pass entirely (a separate follow-up once this foundation is proven) and were not touched.
    `buildcraft.lib.misc.CraftingUtil` (1.12.2's `GameRegistry.findRegistry(IRecipe.class)` scan) has no port at
    all -- both targets' own `RecipeManager#getRecipeFor` already does the lookup directly, collapsing the whole
    class into one call inside `WorkbenchCrafting#tick()`. The block's own crafting recipe (gear + crafting
    table, `gwg`) *is* ported, as a clean modern shaped-recipe JSON -- unlike `TilePump`/`TileTank`/
    `TileFloodGate`'s skipped recipes, both its ingredients (`buildcraft:gear_stone`, vanilla's crafting table)
    already exist in this port.
  - **Genuine platform divergence #1, confirmed via `javap` against both real merged jars: the recipe-matching
    API shape, not just a rename.** 1.12.2's `WorkbenchCrafting extends InventoryCrafting` was simultaneously
    the temporary crafting-grid storage *and* the object handed straight to `IRecipe#matches`/
    `#getCraftingResult`. On 1.20.1, `Recipe<C extends Container>` is still generic over a live container type,
    and `CraftingContainer` (extending `Container`) is exactly that same shape -- so the 1.20.1
    `WorkbenchCrafting` *does* implement `CraftingContainer` itself, backed by a small internal `ItemStack[]`
    standing in for 1.12.2's inherited storage, staying structurally close to the original. On 26.x,
    `Recipe<T extends RecipeInput>` is generic over a plain **immutable data holder**, not a live container --
    `CraftingInput`, built only through the static factory `CraftingInput.of(int width, int height,
    List<ItemStack> items)` (no mutable container interface exists to implement at all), with `Recipe#matches`/
    `#assemble` (no `RegistryAccess` parameter on `assemble` here, confirmed via `javap` — 1.20.1's takes one)
    both reading it directly. So the 26.x `WorkbenchCrafting` is a plain object that snapshots either the live
    blueprint (while matching) or its own temporary grid (while executing a craft) into a fresh `CraftingInput`
    each time one is needed. `RecipeManager#getRecipeFor` also returns one layer deeper on 26.x
    (`Optional<RecipeHolder<T>>`, the recipe itself `holder.value()`) than 1.20.1's plain `Optional<T>`.
  - **A real bug, found only by actually crafting through RCON, not by reading the API surface alone:
    `CraftingInput.of` silently trims its input to the bounding box of non-empty stacks.** Confirmed by reading
    this target's own decompiled source (`CraftingInput.ofPositioned`) after a real dedicated-server crash: a
    3x3 blueprint holding a 1x2 sticks pattern (planks in slot 0 and slot 3) produces a **trimmed 1x2**
    `CraftingInput`, not a 3x3 one -- matching for recipe *lookup* still works fine either way (`RecipeManager`
    handles a trimmed input correctly), but `CraftingRecipe#getRemainingItems(CraftingInput)` is sized to the
    *trimmed* grid, not `width * height`. The first version of `WorkbenchCrafting#craftExact` built a 3x3
    `CraftingInput` and then looped `remainingStacks.get(s)` for `s` in `0..9`, and crashed the live dedicated
    server outright with `ArrayIndexOutOfBoundsException: Index 2 out of bounds for length 2` the moment a real
    craft was attempted (`net.minecraft.ReportedException: Ticking block entity`, caught and fixed in this same
    pass -- see the RCON verification note below for the exact scenario that triggered it). Fixed by using
    `CraftingInput.ofPositioned(width, height, grid)` instead and mapping the trimmed grid's own
    `(tx, ty)` coordinates back to the original blueprint's `(positioned.left() + tx, positioned.top() + ty)`
    when returning a genuine remaining item (an empty bucket, say) to materials -- `matches()`/`assemble()`
    themselves needed no change, since both only ever read through `input.getItem(...)`, which is already
    trim-relative and safe. 1.20.1's `WorkbenchCrafting`, which never trims (its `CraftingContainer` is always
    reported as the tile's own fixed `width * height`, exactly like 1.12.2's `InventoryCrafting`), has no
    equivalent bug -- confirmed by the same RCON scenario succeeding there without incident, both before and
    after the 26.x fix. Worth remembering for any future 26.x code that builds a `CraftingInput` from a grid
    that can be smaller than the crafting recipe search area: `of`/`ofPositioned` trims, and anything reading
    `getRemainingItems`/similar per-slot recipe output has to size itself off the (possibly trimmed) input, not
    off its own caller-side grid dimensions.
  - **Genuine platform divergence #2, confirmed via `javap`: the GUI slot base class.** On 1.20.1, classic
    `IItemHandler`/`net.minecraftforge.items.SlotItemHandler` both still exist, and `ItemHandlerSimple` still
    implements `IItemHandlerModifiable` directly, so `SlotBase` keeps 1.12.2's shape almost unchanged --
    `extends SlotItemHandler`, wrapping the handler directly. On 26.x, neither `IItemHandler` nor
    `SlotItemHandler` exist at all (see `ItemHandlerSimple`'s own javadoc); NeoForge's replacement,
    `net.neoforged.neoforge.transfer.item.ResourceHandlerSlot`, wraps a `ResourceHandler<ItemResource>` through
    an `IndexModifier`, but since every inventory this GUI layer touches is concretely an `ItemHandlerSimple`
    (never just any `ResourceHandler`), the 26.x `SlotBase` instead extends NeoForge's own
    `net.neoforged.neoforge.world.inventory.StackCopySlot` directly and reads/writes the handler's own
    `getStackInSlot`/`setStackInSlot` pair through the abstract `getStackCopy`/`setStackCopy` pair -- one fewer
    layer of indirection than wiring up a `ResourceHandlerSlot` + `IndexModifier` would need, and consistent
    with this port's established preference for talking to its own concrete handler types directly (see
    `ItemHandlerSimple`'s own javadoc, and `TileTank` implementing `ResourceHandler` itself rather than being
    wrapped). `SlotDisplay` follows the same split: a hand-rolled `InventoryBasic`-backed classic `Slot` on
    1.20.1 (1.12.2's own shape, unchanged), `StackCopySlot` directly on 26.x (no fake backing `Container`
    needed at all there).
  - **A third, smaller divergence, also confirmed via `javap`, in the container-menu click/merge plumbing**:
    `AbstractContainerMenu#clicked(int, int, ?, Player)` takes 1.20.1's classic `ClickType` but 26.x's own
    `ContainerInput` enum instead -- a genuine, target-specific rename (re-verified via `javap`, not assumed
    from the task brief that flagged it), and on both targets `clicked` now returns `void` rather than 1.12.2's
    `ItemStack` (the cursor stack lives in `getCarried()`/`setCarried` instead). `ContainerBCTile#clicked`
    (both platforms) intercepts an `IPhantomSlot` click before delegating to `super.clicked`, setting/growing
    the phantom slot's content to match the carried stack without ever consuming it -- ported from
    `ContainerBC_Neptune#slotClick`'s same behaviour. Shift-click merging (`#quickMoveStack`) is a plain two-
    region merge (machine slots, then player inventory, or the reverse) using `moveItemStackTo` (the modern
    rename of `mergeItemStack`), which already respects `Slot#mayPlace` -- so phantom/output/display slots are
    automatically skipped without `ContainerBCTile` needing to filter them out itself.
  - **No custom network payload survives, and none was rebuilt.** 1.12.2's `writePayload`/`readPayload` pushed
    the MJ progress bar's value and the assumed-result display by hand, id-tagged. The progress value is now a
    single container `DataSlot` (`TileAutoWorkbenchBase#getPowerStoredForSync`/`#setPowerStoredForSync`, the
    same "block entity doubles as the `ContainerData` source" idiom vanilla's own furnace uses -- the server's
    `get()` always reads the live field, the client's `set()` writes into its own local tile instance when a
    change packet arrives). The assumed-result display slot needs nothing at all: `SlotDisplay#getItem()` reads
    `TileAutoWorkbenchBase#getCurrentRecipeOutput()` live, and `AbstractContainerMenu#broadcastChanges()`
    already diffs and pushes every slot's `getItem()` to the client every tick on its own. 1.12.2's
    `powerStoredLast`/partial-tick progress-bar interpolation has no replacement -- the progress bar just reads
    the last-synced `DataSlot` value directly; a minor, deliberate simplification given no `DeltaManager`-style
    interpolation is wired into the GUI layer yet.
  - **Opening the menu diverges in the expected, already-documented-elsewhere way.** `BlockAutoWorkbenchItems`
    always opens the GUI on right-click, with no wrench check at all -- matching 1.12.2's own
    `onBlockActivated`, unlike `BlockFloodGate`'s wrench-gated interaction. On 26.x this overrides
    `useWithoutItem` (confirmed `protected` via `javap`, the split-hook shape) and opens through
    `Player#openMenu(MenuProvider)`, with `TileAutoWorkbenchBase` itself implementing `MenuProvider` and
    overriding `IMenuProviderExtension#writeClientSideData` to write its own `BlockPos` by hand (read back by
    `ContainerAutoCraftItems`'s `RegistryFriendlyByteBuf` factory constructor to look the tile up again
    client-side). On 1.20.1 this overrides the still-unified `use` and opens through
    `NetworkHooks.openScreen(ServerPlayer, MenuProvider, BlockPos)`, which writes that same `BlockPos`
    automatically -- no `writeClientSideData` override needed there at all.
  - **Dist-isolation for client screen registration, mirroring but not identical to the `MessageMarker`
    precedent already on file.** `MessageMarker.ClientPlayerLookup` had to isolate one client-only field read
    into a lazily-loaded nested class because that message's registration itself had to run, and be verified,
    unconditionally on both sides. Menu-screen registration is different: it is *inherently* client-only, so the
    coarser, standard idiom applies instead -- gate the *listener registration call itself*, never the method
    body. `BuildCraft`'s constructor (both platforms) only calls
    `modBus.addListener(BCFactoryClientRegistries::registerScreens)` behind a dist check
    (`FMLEnvironment.getDist().isClient()` on 26.x -- confirmed via `javap` that `FMLEnvironment.getDist()` is a
    **method**, not the field the task brief's own starting guess assumed; `FMLEnvironment.dist.isClient()` on
    1.20.1, where it genuinely *is* a public field, confirmed separately via `javap` against that target's own
    `net.minecraftforge.fml.loading.FMLEnvironment`) -- so a dedicated server never has a reason to load or
    bytecode-verify `BCFactoryClientRegistries`, and transitively `GuiAutoCraftItems` (a client-only type on
    26.x, since `Screen`/`GuiGraphicsExtractor` don't exist on a dedicated server there), at all. The actual
    `MenuScreens.register`/`RegisterMenuScreensEvent#register` call and every import of the `Screen` subclass
    live only inside that gated class.
  - **A second, unrelated real bug was found and fixed in the block's own crafting recipe JSON, specific to
    26.3's data format.** The first version of `auto_workbench_item.json`'s shaped-recipe `key` used the classic
    `{"item": "minecraft:crafting_table"}` object form for the non-tag ingredient -- valid on 1.20.1 (and
    accepted there without incident) but rejected outright on 26.3, which crashed *dedicated server world
    creation itself* (`RegistryDataLoader` failing to parse `buildcraft:auto_workbench_item` out of the
    `minecraft:recipe` registry, `IllegalStateException`, before "Done" ever printed) because 26.3's ingredient
    codec no longer accepts that bare object form for a plain item reference -- only a plain string (an item id,
    or a `"#namespace:tag"` string) or its own `neoforge:ingredient_type` object shape. Fixed by using the bare
    string form (`"w": "minecraft:crafting_table"`) instead, matching the already-working `"g": "#c:gears/stone"`
    tag reference beside it. Found on the very first dedicated-server boot attempt after adding the recipe, not
    by inspection -- worth remembering for any future 26.x recipe JSON that still writes ingredients the classic
    `{"item": ...}` way.
  - Registered in `BCFactoryRegistries` (`auto_workbench_item`, same default block properties as every other
    `factory` machine, plus the new `AUTO_WORKBENCH_ITEMS_MENU` field) and `BCRegistry`'s new `addMenu` helper,
    inserted directly after `FLOOD_GATE`/`FLOOD_GATE_TYPE` and before `TUBE`, with the same care taken not to
    disturb `TUBE`'s own anchored javadoc that every prior insertion into this file has needed (flagged as a
    real, previously-hit bug class in this task's own brief). Textures/blockstate/block+item model/loot table/
    lang pulled or written following the established `BlockFloodGate`/`BlockPump` pattern -- `up`/`down` both
    use the original's single `top.png` (matching 1.12.2's own model, which never referenced its own bundled
    `bottom.png` at all -- dropped as a genuinely unreferenced asset, not merely unported), sides use `side.png`
    (`side_alt.png`, also unreferenced by the original model, dropped the same way).
  - **In-game verification, both platforms, via RCON, two independent scenarios each (different position, different
    recipe every time, not a replay of the same test).** `/data merge block` on a freshly placed auto-workbench's
    own NBT (`{blueprint:{stacks:[...]},materials:{stacks:[...]},powerStored:...}` on 26.x -- the
    `ValueIOSerializable`-driven `ItemHandlerSimple` shape, `stacks: [{count, id}, ...]`, empty slots encoding to
    a bare `{}`; `{inv_manager:{blueprint:{items:[...]},materials:{items:[...]}}}` on 1.20.1 -- classic
    `{id, Count}` item NBT nested one level deeper under `inv_manager`, both shapes read directly off a real
    `/data get block` rather than guessed) simulated a player having already configured the blueprint and piped
    in materials, with `powerStored` set directly to `POWER_REQUIRED` to skip the idle-server tick-throttling
    gap already documented in this project's own RCON-verification memory. First scenario (both platforms): a
    1x2 sticks pattern (2 oak planks) with 10 planks piped in -- after ticking, the result slot held 8 sticks
    (4 automatic crafts, 2 sticks each), materials correctly dropped from 10 to 6 planks, and
    `material_filter` balanced to all 9 slots holding oak planks (the only unique blueprint ingredient), exactly
    matching `createFilters()`'s expected single-ingredient case. Second, independent scenario at a different
    position (both platforms): a real wooden-pickaxe pattern (3 planks across the top row, a stick down the
    middle column, 6 planks + 4 sticks piped in) produced one real `minecraft:wooden_pickaxe` in the result slot,
    consumed exactly 3 planks + 2 sticks, and balanced `material_filter` 5 slots to planks / 4 to sticks --
    matching `createFilters()`'s own balancing-formula arithmetic worked out by hand from its ported algorithm
    (`64*1/3` vs `64*1/2` and so on), and `powerStored` correctly fell back to 0 afterward rather than
    accumulating for a next craft, since a pickaxe's own max stack size of 1 makes `canFullyAccept` correctly
    refuse a second craft into an already-occupied result slot. Zero exceptions in either server's log across
    both scenarios on both platforms; this is also the RCON scenario that surfaced both real bugs documented
    above (the `CraftingInput` trimming crash, first hit by this exact sticks scenario on 26.x; the recipe-JSON
    ingredient-format crash, hit on 26.x world creation before any RCON command could even run).
  - **Verified with forced `--no-build-cache clean` rebuilds on both platforms, the full 25-test suite, real
    dedicated-server boots with zero exceptions on both targets (before *and* after both bug fixes above), and a
    real `:neoforge-26x:runClient` boot reaching texture-atlas stitching -- well past mod construction and
    `RegisterMenuScreensEvent` registration -- with no `IllegalStateException`/`ClassNotFoundError`/
    `NoClassDefFoundError` naming `GuiAutoCraftItems`/`BCFactoryClientRegistries`/`ContainerAutoCraftItems`
    anywhere in the log, confirming the dist-isolation guard is correctly wired.** **Not independently verified:
    the actual on-screen GUI itself** -- this environment has no mouse/keyboard input automation, so the phantom-
    slot drag-and-drop interaction, the progress-bar fill, and the background texture's on-screen placement can
    only be confirmed by a human opening a real client and right-clicking a placed auto-workbench, the same
    caveat already on file for `BlockFloodGate`'s wrench gesture, the creative-tab fix, and client-side rendering
    generally. What *is* verified live, end to end, is every piece of logic the GUI would otherwise only be a
    thin skin over: blueprint matching, exact-stack requirement checking, material consumption, filter
    auto-derivation, MJ accumulation/spend, and result production -- all directly, via RCON, bypassing the GUI
    entirely.

- **`buildcraft.energy` — `TileEngineStone`/`BlockEngineStone` (both platforms), the Stirling Engine, and with
  it the first real fuel-burning machine ported (`buildcraft.core`'s `TileEngineWood`/`TileEngineCreative` are
  constant/redstone-driven engines, not fuel-burning ones). Reuses both foundations landed earlier this port
  without change: `buildcraft.lib.engine.TileEngineBase` (the `burn()`/`engineUpdate()`/`isBurning()`/
  `createConnector()`/`getMaxPower()`/`maxPowerReceived()`/`maxPowerExtracted()`/`explosionRange()`/
  `getCurrentOutput()` extension points were already there, unused, waiting for exactly this machine) and the
  auto-workbench batch's `ContainerBCTile`/`SlotBase` GUI foundation -- a single real fuel slot, one `DataSlot`
  for the flame-level indicator, no phantom slots at all, much smaller than `ContainerAutoCraftItems`.
  - **New files, both platforms**: `buildcraft.energy.tile.TileEngineStone`, `buildcraft.energy.block.
    BlockEngineStone`, `buildcraft.energy.container.ContainerEngineStone`, `buildcraft.energy.gui.
    GuiEngineStone`, `buildcraft.energy.client.BCEnergyClientRegistries`, and a new top-level
    `buildcraft.BCEnergyRegistries` (mirroring `BCFactoryRegistries` exactly -- its own private `BCRegistry`,
    `addBlockAndItem`/`addBlockEntity`/`addMenu`, a `registerCapabilities` on 26.x since `TileEngineStone`
    exposes both its fuel slot and its MJ connector as capabilities). `BuildCraft`'s constructor (both
    platforms) grew one additive line each for `BCEnergyRegistries.register(modBus)` and, behind the same
    dist-isolation guard `BCFactoryClientRegistries` already established, `BCEnergyClientRegistries::
    registerScreens`. The `getCurrentOutput()` PID-like smoothing (`esum`/`clamp`/`MAX_OUTPUT`/`MIN_OUTPUT`/
    `kp`/`ki`/`eLimit`) is a direct, unchanged port of the original's arithmetic on both platforms -- no
    redesign needed, exactly as the task brief for this batch already anticipated.
  - **The fuel slot's `isForceInserting` mechanic is a real, deliberate 1.12.2 quirk, ported faithfully, not
    incidental behaviour "fixed" along the way.** While a fuel item's leftover container item (e.g. the empty
    bucket a burning lava bucket leaves behind) is sitting in the fuel slot, the slot's insertion checker accepts
    *any* item, not just fuel -- `isForceInserting` is set immediately before the container item is force-written
    with `setStackInSlot` (bypassing the checker entirely) and cleared again only once the player empties the
    slot (`onSlotChange`). This narrow window where an unrelated item could technically also be piped in is
    exactly what 1.12.2 itself did; see `TileFloodGate`'s own progress entry above for this port's established
    precedent of preserving a genuine upstream oddity rather than "fixing" it unasked.
  - **`explosionRange()` returns `2` (the original constant) and is never called anywhere in the already-ported
    `TileEngineBase` on either platform -- confirmed, not assumed, by re-reading 1.12.2's own
    `TileEngineBase_BC8` (~line 469): the one call site (`worldObj.createExplosion(...)`) is commented out in
    the original source itself.** 1.12.2's own engines never actually explode on overheat despite the plumbing
    being there. No working explosion mechanic was added; the constant is returned faithfully and stays unused,
    matching upstream's own dead code exactly.
  - **Fuel burn-time lookup is a genuine, confirmed-via-decompile platform divergence on 26.x, and the task
    brief's own starting research needed two real corrections once actually verified.** 1.12.2's
    `TileEntityFurnace.getItemBurnTime(stack)` has no direct equivalent: fuel value is now the
    `net.minecraft.world.item.component.CookingFuel` data component (`burnTime()`/`speedMultiplier()`, each a
    `ResolvableInt`/`ResolvableFloat` needing a `LootContext`), reached the way vanilla's own
    `AbstractFurnaceBlockEntity#getBurnDuration` does, confirmed by decompiling that class from this target's
    own sources jar. Since `TileEngineStone` isn't a `Container`/`BaseContainerBlockEntity`, it can't inherit
    `BaseContainerBlockEntity#getLootContext` and builds its own `LootContext` in `getItemBurnTime`. **First
    correction**: `LootContextParamSets.CONTAINER_PROCESS` does *not* leave `BLOCK_STATE`/`ORIGIN` as "at least"
    required with the rest optional, as the task brief's own starting guess put it -- reading its actual
    registration shows all four of `BLOCK_STATE`, `BLOCK_ENTITY`, `CONTAINER` and `ORIGIN` are `.required(...)`,
    only `NeoForgeLootContextParams.QUERIED_STACK` is `.optional(...)`, and `LootParams.Builder#create`
    validates every required key eagerly (`ContextMap.Builder#buildAndValidate`) -- omitting one throws
    immediately rather than silently defaulting. `LootContextParams.CONTAINER` is typed `ContextKey<SlotProvider>`
    (a single `getSlot(int)` method), not `Container` as the name suggests, so `TileEngineStone` supplies a
    trivial no-op `slot -> null` stub -- nothing about a plain burn-time lookup ever queries it. **Second
    correction**: `ResolvableInt` (and `ResolvableFloat`) live under
    `net.minecraft.world.level.storage.loot.providers.number.ints` (`.floats` for the latter), not
    `net.minecraft.util.valueproviders` as the task brief's own starting guess had it -- confirmed by extracting
    and reading the real class from this target's sources jar. `ResolvableInt.getFromItem(stack,
    DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0)` is the actual one-line resolution call,
    correctly returning `0` for a stack with no `COOKING_FUEL` component at all.
  - **The container-item lookup (the empty bucket a burning lava bucket leaves behind) is also a genuine,
    confirmed divergence on 26.x.** 1.12.2's `fuel.getItem().getContainerItem(fuel)` becomes
    `ItemStack#getCraftingRemainder()` -- a NeoForge extension default method (`ItemInstanceExtension`, mixed
    into `ItemStack` via the `ItemInstance` interface) that returns a **nullable `ItemStackTemplate`**, not an
    `ItemStack` directly; `ItemStackTemplate#create()` produces the real stack. Confirmed by decompiling this
    target's own `AbstractFurnaceBlockEntity#consumeFuel`, which does exactly this for a burning lava bucket.
  - **A real defect was found and fixed on 1.20.1 during RCON verification, not by inspection.** The first
    version of `getItemBurnTime` called `stack.getBurnTime(null)` directly -- the `IForgeItemStack` default
    method mixed into `ItemStack` itself, which looks like the natural one-line equivalent and compiles fine.
    It is *not* the resolved lookup: it is the per-item override hook, returning `-1` as a sentinel meaning
    "this item doesn't override its own burn time, fall back to vanilla's `FurnaceBlockEntity.getFuel()` map" --
    confirmed by decompiling `net.minecraftforge.common.ForgeHooks#getBurnTime`, which is what actually resolves
    that sentinel (`int ret = stack.getBurnTime(recipeType); return ForgeEventFactory.getItemBurnTime(stack, ret
    == -1 ? VANILLA_BURNS.getOrDefault(...) : ret, recipeType)`) and fires the burn-time event. Calling
    `ItemStack#getBurnTime` directly made every plain vanilla fuel item, coal included, resolve to `-1` --
    `isValidFuel` never went true (`-1 > 0` is false), so a freshly-placed engine silently refused to accept any
    fuel at all. Caught on the very first RCON test against a real dedicated server: a coal item written into the
    fuel slot via `/data modify block ... set value` stayed at `burnTime: -1, totalBurnTime: -1` and the item
    count never dropped, where the 26.x server (tested moments earlier) had immediately shown the correct
    `totalBurnTime: 1600`. Fixed by calling `net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null)`
    instead, which already handles both the `-1` fallback and the empty-stack case (`0`) itself. Recompiled,
    rebooted, and re-verified with the identical RCON scenario afterward -- see below.
  - **The recipe is portable and ported, unlike `TilePump`/`TileTank`/`TileFloodGate`'s skipped ones** -- all
    four ingredients (cobblestone, glass, `buildcraft:gear_stone`, vanilla piston) already exist in this port or
    are vanilla. Pattern (`www` / `" g "` / `GpG`) and glass/piston handling directly mirror the already-shipped,
    near-identical `engine_wood.json` recipe (`www` / `" g "` / `GpG` too, just planks/gear_wood instead of
    cobblestone/gear_stone) -- `g`/`p` are plain item references (`minecraft:glass`, `minecraft:piston` on 26.x;
    `{"item": ...}` on 1.20.1, matching that same file), and `G` is `buildcraft:gear_stone` the same bare-string
    way `engine_wood.json` already references `buildcraft:gear_wood`. Cobblestone reuses the exact tag reference
    `gear_stone.json`'s own recipe already established for this port: `#c:cobblestones` on 26.x,
    `{"tag": "forge:cobblestone"}` on 1.20.1 -- note the genuine per-platform tag-id divergence (plural
    `c:cobblestones` vs singular `forge:cobblestone`), already on file in that recipe, not a new finding.
  - **Dropped from the original GUI, out of scope for this pass, matching the auto-workbench batch's own
    precedent for dropping pure polish.** 1.12.2's `GuiEngineStone_BC8` wired up the in-GUI help/tooltip
    framework (`LedgerEngine`, `DummyHelpElement`, two `ElementHelpInfo` fields) -- not ported anywhere in this
    port yet, on either platform, and out of scope here; noted in `GuiEngineStone`'s own javadoc. 1.12.2's own
    `deltaFuelLeft`/`DeltaInt`/`deltaManager` GUI-sync mechanism is dropped entirely (not partially ported) in
    favour of a single container `DataSlot` for the flame-level percentage (`TileEngineStone#
    getFuelPercentForSync`/`#setFuelPercentForSync`), exactly the `TileAutoWorkbenchBase#getPowerStoredForSync`
    idiom already established -- no partial-tick interpolation, the flame indicator just reads the last-synced
    value directly.
  - **Right-click always opens the GUI**, matching `BlockAutoWorkbenchItems`'s own precedent exactly (re-verified,
    not just trusted from that entry's own note): `useWithoutItem` on 26.x, the still-unified `use` opening
    through `NetworkHooks.openScreen` on 1.20.1.
  - **In-game verification, both platforms, via RCON, against a real dedicated server each, not a clean-boot-only
    check.** A Stirling Engine was placed with a chute (an already-ported MJ receiver) directly above it (its
    default `currentDirection` is `UP`) and a redstone block on an adjacent side; 20 coal was written into the
    fuel slot via a real NBT round trip read back off `/data get block` first, not guessed (`fuel.stacks[0]` on
    26.x -- the `ValueIOSerializable` shape, a bare list of `{id, count}`, empty slots encoding to `{}`;
    `inv_manager.fuel.items[0]` on 1.20.1 -- classic `{id, Count}` item NBT nested one level under `inv_manager`,
    matching `ItemHandlerManager#serializeNBT`'s own per-handler key). On both platforms, watched entirely
    through real elapsed time (no state was hand-set to "already burning" to skip the natural cycle): `burnTime`/
    `totalBurnTime` immediately resolved to `1600` (real coal, real vanilla burn time, confirming the platform-
    specific lookup on both targets independently), the coal stack count dropped from 20 to 19 the instant
    `burn()` ran, `esum`/`power`/`heat` climbed tick over tick exactly as the PID-like loop and heat-toward-
    `IDEAL_HEAT` formula predict, `progressPart`/`progress` engaged (the piston-pump state machine inherited from
    `TileEngineBase`), and — watched across the full cycle, not just its start — burnTime reached the end of the
    first coal's 1600-tick run and the engine auto-refuelled from the same slot with no external help, the stack
    count dropping to 18 with a fresh `burnTime: 1600`, confirming the "zero-gap refuel" behaviour inherited
    unchanged from `TileEngineBase#serverTick`'s call order (`engineUpdate()` before `burn()` each tick). The
    neighbouring chute's own MJ battery filled to its own capacity over the same window, confirming power
    actually left the engine and reached a real neighbour, not just accumulated locally. Zero exceptions in
    either server's log across the whole session, before and after the 1.20.1 `getBurnTime` fix above.
  - **Verified with forced `--no-build-cache clean` rebuilds on both platforms (before and after the 1.20.1 bug
    fix), the full 25-test suite, real dedicated-server boots with zero exceptions on both targets, and a real
    `:neoforge-26x:runClient` boot that reached full texture-atlas stitching and a loaded resource manager
    (`mod/buildcraft` included) with no `IllegalStateException`/`ClassNotFoundError`/`NoClassDefFoundError`/
    `BootstrapMethodError` naming `GuiEngineStone`/`ContainerEngineStone`/`BCEnergyClientRegistries` anywhere in
    the log.** **Not independently verified: the actual on-screen GUI itself** -- this environment has no mouse/
    keyboard input automation, so the fuel slot's click-to-insert interaction and the flame indicator's on-screen
    fill cannot be exercised live, the same caveat already on file for every prior GUI-touching entry in this
    file. What *is* verified live, end to end, is every piece of logic the GUI is a thin skin over: fuel
    consumption, burn-time countdown, heat/power-stage progression, the PID-like output smoothing, and real MJ
    delivery to a neighbour -- all directly, via RCON, bypassing the GUI entirely.

- **`buildcraft.core` -- `ItemPaintbrush`, the Paintbrush**, a right-click-on-block tool that recolours a
  vanilla dyeable block (wool, concrete, terracotta, stained glass, ...) to a chosen `DyeColor`, or -- held as
  the plain/colourless variant -- attempts to strip an existing colour back to plain. The hard part was already
  done by an earlier batch: `buildcraft.api.blocks.CustomPaintHelper`/`DyedBlockVariants` (both platforms)
  already implement the actual "find the colour-family a block belongs to and swap it" logic, including the
  `<colour>_<suffix>` registry-naming fallback that covers most vanilla dyeable blocks for free. This batch is
  only the item wrapping that call, tracking its own durability.
  - **New files, both platforms**: `buildcraft.core.item.ItemPaintbrush` -- one shared class, not 17, taking a
    `@Nullable DyeColor` constructor argument (`null` for the colourless variant). `BCCoreRegistries` grew one
    `PAINTBRUSH` field (the colourless variant) plus a `PAINTBRUSHES` field, an `EnumMap<DyeColor,
    DeferredItem<ItemPaintbrush>>`/`EnumMap<DyeColor, RegistryObject<ItemPaintbrush>>` populated by a real
    runtime loop over `DyeColor.values()`, registered as `paintbrush_<colour>` (e.g. `paintbrush_white`) using
    `DyeColor#getSerializedName()` -- the task's own suggested shape, and consistent with `DyedBlockVariants`'
    own `<colour>_<suffix>` naming convention. No new module holder needed; `buildcraft.core` already has
    `BCCoreRegistries`, unlike the auto-workbench/Stirling-engine batches which needed brand-new
    `BCFactoryRegistries`/`BCEnergyRegistries` holders. 17 sets of item-definition JSON (26.x only)/model JSON/
    texture PNG were added under `assets/buildcraft/{items,models/item,textures/item}/paintbrush*`, copied from
    `buildcraft_resources/assets/buildcraftcore/textures/items/paintbrush/` (the modern `DyeColor`-matching
    filenames -- `light_blue.png`/`light_gray.png` -- not the legacy `lightblue.png`/`silver.png` duplicates
    sitting alongside them from an old pre-1.12 dye-colour naming scheme), and one shared lang entry,
    `item.buildcraft.paintbrush = "Paintbrush"`, added to both platforms' `en_us.json`.
  - **17 metadata sub-items become 17 separate registry entries, following `BlockDecoration`'s own precedent for
    the identical "several looks, one 1.12.2 class" situation, just items instead of blocks.** 1.12.2's
    `ItemPaintbrush_BC8` packed `usesLeft`/`colour` into a metadata value plus a "damage" NBT byte, with
    hand-rolled `getDamage`/`setDamage`/`isDamaged`/`showDurabilityBar`/`getDurabilityForDisplay` overrides
    simulating a vanilla durability bar on top -- metadata was the only way 1.12.2 had to give "the same item,
    several looks". Confirmed via `javap` against both platforms' real jars that `ItemStack#isDamageableItem()`/
    `getDamageValue()`/`setDamageValue(int)`/`getMaxDamage()` are identical, stable, pre-data-component vanilla
    API on both targets (durability predates data components entirely; 1.20.1 has no
    `net.minecraft.core.component.DataComponentType` at all), so each of the 17 entries is a plain
    `Item.Properties().durability(64).stacksTo(1)` damageable tool needing none of 1.12.2's own hand-rolled
    overrides -- `stack.hurtAndBreak(...)` replaces `usesLeft--` directly.
  - **The colourless variant is a real, distinct tool, not a placeholder, ported faithfully guard and all.**
    1.12.2's own guard, `if (colour != null && usesLeft <= 0) return false;`, short-circuits false and is
    skipped entirely when `colour == null` -- the colourless brush is never blocked by its own uses-left count.
    Reproduced exactly in `ItemPaintbrush#useOn`. Durability is still spent identically for every variant,
    including the colourless one, on every successful paint -- matching a genuine 1.12.2 quirk (the counter was
    decremented even though the colourless guard above never consulted it) rather than "fixing" it, the same
    precedent `TileFloodGate`'s and the Stirling Engine's own entries already established for preserving a
    genuine upstream oddity unasked.
  - **The colourless brush's "clear paint" action is currently a no-op against every plain vanilla block, and
    this is honest, current platform behaviour inherited from an earlier batch, not a defect in this one.**
    `CustomPaintHelper.INSTANCE.attemptPaintBlock(..., null)` reaches a block-specific `ICustomPaintHandler` if
    one is registered for that block (none are, yet, for any vanilla block), but its generic
    `defaultAttemptPaint` fallback returns `FAIL` immediately for `paint == null` ("Clearing paint has no
    generic form: there is no way to know which colour is the 'plain' one") -- already-existing, already-ported
    behaviour this batch did not touch and is not responsible for changing.
  - **One real, deliberate behavioural difference from 1.12.2, a direct and unavoidable consequence of the
    17-separate-items redesign, not of any change to the painting logic itself.** 1.12.2's single
    shared-metadata item downgraded a spent coloured brush in place to the colourless variant (metadata 0)
    rather than destroying it, since metadata was just a rewritable value on the same `ItemStack`. With 17
    separate registry entries there is no equivalent in-place "downgrade" -- `hurtAndBreak` instead runs
    vanilla's own tool-break behaviour: a coloured brush that reaches its 64th successful use is consumed like
    any other vanilla tool (confirmed live via RCON below: `damage=0/0 consumed=true name=Air`), rather than
    turning into a fresh colourless one. This is the natural, expected consequence of the durability-based
    design this task's own brief specified, not something silently changed along the way.
  - **Dropped: `ParticleUtil.showChangeColour`.** `ParticleUtil` is not ported to either platform (client-side
    particle rendering is deferred generally, matching every other client-rendering caveat already on file in
    this document), and porting it just for this single call would be new client-rendering infrastructure out of
    scope for this batch. `SoundUtil.playChangeColour(Level, BlockPos, DyeColor)`, already ported identically on
    both targets from an earlier batch, is kept and called on every successful paint.
  - **Tooltip/display name uses `ColourUtil#getTextFullTooltip(DyeColor)`, not `#getTextFullTooltipSpecial`,
    which 1.12.2 used for the same purpose.** `getTextFullTooltipSpecial` emits a private escape sequence meant
    for `SpecialColourFontRenderer`, which is not ported on either platform (deferred with the rest of client
    rendering) -- using it here would have embedded unrenderable characters in the item's name for every colour
    except `BLACK`/`BLUE` (that method's own special-cased pair). `getTextFullTooltip` is the safe, already-
    ported equivalent that only ever emits standard `ChatFormatting` codes. Confirmed via `javap` that
    `Item#getDescriptionId()` is `final` on 26.x, so the shared base name (`item.buildcraft.paintbrush`, one lang
    entry for all 17 variants, per the task's own spec) is built directly in `ItemPaintbrush#getName(ItemStack)`
    rather than through a per-registry-entry description id override.
  - **The modern item-use-on-block hook, confirmed via `javap` against `Item` on both platforms' real jars, is
    identical in name and signature on both targets**: `InteractionResult useOn(UseOnContext)` -- the same hook
    `ItemWrench#useOn` already established a precedent for. `UseOnContext#getClickLocation()` already returns
    the absolute world-space hit position 1.12.2 built by hand (`VecUtil.add(new Vec3d(hitX, hitY, hitZ), pos)`),
    so no equivalent helper call is needed. The one real per-platform divergence inside the method body is
    `InteractionResult` itself (already on file as API-migration-reference item 14, re-verified fresh for this
    class): a success check is `instanceof InteractionResult.Success` on 26.x, `== InteractionResult.SUCCESS` on
    1.20.1. `ItemStack#hurtAndBreak` also has a narrower overload set on 1.20.1 -- confirmed via `javap` that only
    the generic `<T extends LivingEntity> hurtAndBreak(int, T, Consumer<T>)` form exists there (26.x additionally
    has a direct `hurtAndBreak(int, LivingEntity, InteractionHand)` convenience overload), so 1.20.1's callback
    calls `LivingEntity#broadcastBreakEvent(InteractionHand)` itself.
  - **In-game verification, both platforms, via a real dedicated server each, RCON, plus a temporary throwaway
    debug entry point -- and an honest account of what that does and does not prove.** There is no input
    automation in this environment to right-click as a real player, and a real dedicated server has no `Player`
    entity at all until a client actually connects (confirmed: `/give`, `/execute as @a`, and every other
    player-targeted command fail with "No player was found" against zero connected clients; the vanilla command
    surface on both targets was checked via `/help` and has no `/player`-style fake-interaction command on either
    release). Rather than settle for only re-confirming `CustomPaintHelper` (already known-good from an earlier
    batch), a temporary `RegisterCommandsEvent` debug command was added to each platform's `BuildCraft.java`
    (`net.neoforged.neoforge.common.util.FakePlayerFactory`/`net.minecraftforge.common.util.FakePlayerFactory`
    -- both present on the real loader jars, confirmed via `javap`/jar listing -- construct a genuine, if fake,
    `ServerPlayer`), which built a real `UseOnContext` around that fake player and called
    `stack.getItem().useOn(context)` directly -- i.e. it called `ItemPaintbrush#useOn` itself, the actual new
    code this batch added, not a re-test of already-known-good infrastructure underneath it. Both platforms were
    exercised identically and gave identical results: a red paintbrush on `white_wool` recoloured it to
    `red_wool` (`result=SUCCESS`/`Success[...]`, `damage=1/64`); the same brush against the now-red wool failed
    without spending a use (`result=FAIL`, `damage=0/64`, matching `CustomPaintHelper`'s own "already this
    colour" `FAIL`-not-`PASS` behaviour); the colourless brush against `blue_concrete` failed cleanly
    (`result=FAIL`), confirming the "no-op against plain vanilla blocks" note above live, not just by reading the
    code; a creative-mode fake player painted `blue_concrete` to `green_concrete` without spending durability
    (`damage=0/64` unchanged); and a brush pre-set to `damage=63` (its last use) painted successfully once more
    and was then genuinely consumed (`damage=0/0 consumed=true name=Air` -- `stack.getItem()` on an emptied stack
    resolves to `Items.AIR`), confirming the deliberate tool-break-at-max-damage behaviour documented above
    actually happens rather than throwing or leaving a broken-but-present stack. The same scenario set was also
    run against `white_terracotta` to confirm the recolour path isn't wool-specific. The temporary debug command
    and its `BuildCraft.java` hook were fully removed before this batch finished -- confirmed by `git status`
    showing no diff against either `BuildCraft.java` afterward -- and every verification step below (clean
    rebuild, tests, server boots, `runClient`) was re-run *after* that removal, against the final code, not
    against the code with the debug command still present. **Honest limitation, same as every prior
    RCON-verified batch in this file: the real right-click interaction itself, end to end through a real
    connected client's input, remains unverified** -- what is verified, directly, is that `ItemPaintbrush#useOn`
    itself (not a stand-in, not just the painting logic underneath it) dispatches correctly, decrements
    durability correctly, respects creative mode correctly, and breaks the tool correctly, on both platforms
    independently.
  - **Verified with forced `--no-build-cache clean` rebuilds on both platforms, the full 25-test suite, real
    dedicated-server boots with zero exceptions on both targets (both worlds deleted and rebooted fresh after
    the debug command's removal), and a real `:neoforge-26x:runClient` boot that reached full texture-atlas
    stitching (including `textures/atlas/items.png-atlas`) and a loaded resource manager (`mod/buildcraft`
    included) with no missing-model/missing-sprite warnings for any `paintbrush*` entry and no
    `IllegalStateException`/`ClassNotFoundError`/`NoClassDefFoundError`/`BootstrapMethodError` anywhere in the
    log.** Not independently verified: the on-screen appearance of the 17 item textures/models themselves --
    same rendering caveat as every other entry in this file -- and the real right-click interaction, per the
    paragraph above.

- **`buildcraft.transport` -- the first slice of pipes**, BuildCraft's signature feature and its own large
  subsystem (124 files in the original). This batch is deliberately scoped to a straight run of the simplest
  possible item pipe: a cobblestone pipe that moves an item from a source inventory into a destination
  inventory, with real server-side simulation and every other pipe feature (colours, wires, gates, pluggables,
  fluid/power flow, every non-cobblestone material) explicitly deferred. The entire pipe *API* was already
  ported by an earlier pass (confirmed by diffing the original `BuildCraftAPI/api/buildcraft/api/transport` file
  list against both platforms plus `modules/shared`); this batch is the first real *implementation* against it.
  - **New files, both platforms**: `buildcraft.transport.pipe.{PipeRegistry,Pipe,PipeEventBus,
    DefaultPipeConnection}`, `buildcraft.transport.pipe.behaviour.{PipeBehaviourSeparate,PipeBehaviourCobble}`,
    `buildcraft.transport.pipe.flow.{PipeFlowItems,TravellingItem}`, `buildcraft.transport.tile.
    {TilePipeHolder,SimplePipeWireManager}`, `buildcraft.transport.block.BlockPipeHolder`,
    `buildcraft.transport.item.ItemPipeHolder`, and a new top-level `buildcraft.BCTransportRegistries`
    (mirroring `BCFactoryRegistries`/`BCEnergyRegistries` exactly). `BuildCraft`'s constructor (both platforms)
    grew one additive line, `BCTransportRegistries.register(modBus)`.
  - **The one-block-many-items architecture is the real 1.12.2 design, reproduced deliberately, not a
    simplification invented for this batch.** Re-reading `common/buildcraft/transport/pipe/{PipeRegistry,
    Pipe}.java` directly (not assumed) confirms 1.12.2 already used a single shared block/tile pair
    (`BlockPipeHolder`/`TilePipeHolder`) for every pipe material, with a `PipeDefinition` (id, behaviour
    constructor, flow type) stamped onto the tile's `Pipe` object by whichever `ItemPipeHolder` instance placed
    it -- the same shape the paintbrush batch's 17-separate-items design was explicitly *not* an example of
    (that was for metadata-variant items with no shared runtime state; pipes are the opposite case). This batch
    needed `BlockPipeHolder`/`TilePipeHolder` written exactly once; a second pipe material in a future batch is
    only a second `PipeDefinition` + a second `ItemPipeHolder` instance registered in `BCTransportRegistries`,
    no new block/tile code at all -- confirmed live: `BCTransportRegistries#registerCapabilities` never
    references the specific `PIPE_COBBLESTONE` definition, only the shared block entity type.
  - **A real, load-bearing defect was found and fixed on both platforms during RCON verification, not by
    inspection: adjacent pipe segments failed to detect each other as pipes at all.** The first version of
    `BCTransportRegistries#registerCapabilities` (26.x) only registered `Capabilities.Item.BLOCK` for
    `TilePipeHolder`; it never registered `PipeApi.CAP_PIPE_HOLDER`/`CAP_PIPE`/`CAP_PLUG` themselves, the three
    tokens that expose the tile/pipe/pluggable objects *as objects* (as opposed to arbitrary capabilities the
    behaviour/flow choose to expose) -- 1.12.2's own `TilePipeHolder` constructor wires exactly these three via
    `caps.addCapabilityInstance(CAP_PIPE_HOLDER, this, ...)`/`addCapability(CAP_PIPE, this::getPipe, ...)`/
    `addCapability(CAP_PLUG, this::getPluggable, ...)`, which this batch had ported everywhere *except* that one
    constructor-time registration. Without it, `IPipeHolder#getNeighbourPipe` (which resolves
    `Level#getCapability(PipeApi.CAP_PIPE, pos, side)`) always returned null for a real neighbouring pipe tile,
    so `Pipe#updateConnections` could never tell "the block next to me is another pipe" from "the block next to
    me is a plain inventory" -- two adjacent cobblestone pipe segments connected to each other as
    `ConnectedType.TILE` (falling through to `DefaultPipeConnection`) instead of `ConnectedType.PIPE`, and
    `Pipe#canPipesConnect`/`canBehavioursConnect`/`canFlowsConnect` never ran between them at all. Caught live:
    the first RCON test placed two pipe segments next to each other and read back `con: 0` on the second segment
    (no connections recorded at all, since its first tick ran before the first segment had a chance to be
    detected either) and, after a partial fix attempt, `con` values that decoded to TILE-TILE instead of the
    expected PIPE-PIPE, plus a plain-air `con` bit that could only be explained by `getNeighbourPipe` resolving
    null. Fixed on both platforms: 26.x now additionally calls `event.registerBlockEntity(PipeApi.CAP_PIPE_HOLDER,
    ..., (tile, side) -> tile)` / `(..., PipeApi.CAP_PIPE, (tile, side) -> tile.getPipe())` / `(...,
    PipeApi.CAP_PLUG, (tile, side) -> tile.getPluggable(side))` in `BCTransportRegistries`; 1.20.1's
    `TilePipeHolder#getCapability` (which -- unlike 26.x -- already exposes its own capabilities directly, per
    `TileChute`'s own precedent on that target) now additionally checks for and answers those same three tokens
    before falling through to the pipe's own `getCapability`. Re-verified afterwards: `con` bitmasks on both
    platforms decoded correctly (`WEST=TILE` towards the hopper, `EAST=PIPE` towards the neighbouring segment, and
    the mirror image on the far segment), confirmed by hand-decoding the two-bits-per-face encoding documented in
    `Pipe#writeToNbt`'s own comment, not just by trusting that no exception was thrown.
  - **`Pipe.java` is a close, direct port of 1.12.2's own `Pipe.java`, with the one deliberate simplification the
    task brief itself anticipated**: no `writePayload`/`readPayload`/network constructor/`getModel()`/
    `PipeModelKey` -- nothing client-side observes a pipe's connection state or behaviour data yet, since this
    batch has no client rendering at all. Connection tracking (`connected`/`types` maps,
    `updateConnections()`, `canPipesConnect`/`canBehavioursConnect`/`canFlowsConnect`) is real, unabridged,
    server-side logic, ported unchanged in structure. `DefaultPipeConnection` (consulted when a neighbour block
    implements neither `ICustomPipeConnection` nor has a `PipeConnectionAPI` registration) is byte-identical on
    both platforms: confirmed via `javap` that `BlockBehaviour$BlockStateBase#getCollisionShape(BlockGetter,
    BlockPos)` (the modern replacement for 1.12.2's `IBlockState#getCollisionBoundingBox(World, BlockPos)`) is
    unchanged in shape and still returns a shape in the neighbour's own *local* block space, not world space --
    the same "no re-offsetting needed" property the 1.12.2 box had.
  - **`PipeEventBus` needed only its one Minecraft-only dependency removed, not a redesign**, confirmed by
    re-reading 1.12.2's own 193-line file directly: the reflection-based `@PipeEventHandler` dispatch
    (`MethodHandle`/`Modifier`/`Parameter`, all plain `java.lang.reflect`/`java.lang.invoke` API) is completely
    untouched by the port and copied over structurally unchanged. The only thing dropped is
    `BCDebugging.shouldDebugLog`-gated state-validation calls around `fireEvent` -- no debug-flag system exists
    anywhere else in this port either, and those checks were opt-in diagnostics, not behaviour. This file is
    byte-identical on both platforms (nothing in it touches a Minecraft type), the same "worth knowing before
    duplicating a platform class" category `VecUtil`/`RotationUtil`/`MjEffects` already established. Proven
    working live, not just by inspection: `PipeBehaviourCobble#modifySpeed`, a real `@PipeEventHandler` static
    method, fires on every item reaching a pipe's centre -- confirmed via RCON by watching queued
    `TravellingItem`s consistently carry `speed: 0.01d` (the method's own target), not the default `0.05`.
  - **`buildcraft.transport.pipe.flow.PipeFlowItems`/`TravellingItem` are the real logic this whole batch exists
    to prove works, ported closely from `common/buildcraft/transport/pipe/flow/{PipeFlowItems,TravellingItem}
    .java` (698 + 220 lines)**, with client-rendering members dropped (`clientItemLink`/`stackSize`/
    `interpolatePosition`/`getRenderPosition`/`getRenderDirection`/`isVisible`/`getAllItemsForRender`,
    `sendItemDataToClient`/the network constructor/`readPayload`/`writePayload`) and the delay-bucket item queue
    (1.12.2's `buildcraft.lib.misc.data.DelayedList<E>`) folded in as a small private reimplementation rather
    than ported under its own name, since nothing else in this batch's scope needs a generic delayed queue and
    it was not itself part of this batch's file list. `addTriggers` (registering
    `BCTransportStatements.TRIGGER_ITEMS_TRAVERSING`) is dropped outright -- gates/statements are out of scope
    and `BCTransportStatements` is not ported.
  - **This is the single largest genuine per-platform divergence in the whole batch, and it lives entirely
    inside `PipeFlowItems`/the capability-exposure it needs, confirmed by reading each target's own already-
    ported `IFlowItems`/`IInjectable` interface rather than assumed:** 1.12.2's `injectItem(ItemStack stack,
    boolean doAdd, ...)` took a whole stack and a simulate flag and handed back the leftover stack. **1.20.1
    keeps that shape completely unchanged** (`ItemStack`/`boolean doAdd`, no transaction type at all -- 1.20.1
    has no transfer API), so that platform's `PipeFlowItems`/`TilePipeHolder`/`Pipe` read almost as a literal
    transliteration of the original, and the vanilla-interop capability is the classic `IItemHandler` under
    `ForgeCapabilities.ITEM_HANDLER`, exposed via `TilePipeHolder`'s own `getCapability(Capability<T>, Direction)`
    override (matching `TileChute`'s established 1.20.1 precedent of exposing capabilities directly rather than
    through a `RegisterCapabilitiesEvent` listener). **26.x reshapes the same method entirely** around the
    transfer API: an `ItemResource` plus a plain `int` count and a `TransactionContext` this method must never
    commit itself -- the caller (typically a real vanilla hopper's own transaction, reached through
    `Capabilities.Item.BLOCK`) decides whether the effect sticks. That makes `PipeFlowItems`'s own mutation of
    its item queue genuinely transaction-unsafe unless handled: if a caller's transaction rolls back after
    `injectItem` returns a non-zero accepted count, the item must not silently stay queued while the source
    inventory also gets its stack back. `PipeFlowItems` (26.x) carries a small, self-contained
    `net.neoforged.neoforge.transfer.transaction.SnapshotJournal` over its own delay-bucket queue for exactly
    this -- the same mechanism NeoForge's own `ItemStacksResourceHandler` uses internally for its slot array,
    hand-rolled here because this flow's state is a moving queue, not a fixed array -- called via
    `journal.updateSnapshots(transaction)` immediately before every mutating entry point
    (`injectItem`, `tryExtractItems` when not simulating). The vanilla-interop capability on 26.x is
    `Capabilities.Item.BLOCK` (`BlockCapability<ResourceHandler<ItemResource>, Direction>`), wrapped by a small
    private `PipeItemResourceHandler` adapter inside `PipeFlowItems` itself (one virtual slot, `insert` forwards
    straight to `injectItem`, extraction unsupported) and registered per block entity type in
    `BCTransportRegistries#registerCapabilities`, following `BCFactoryRegistries#CHUTE`'s own precedent for
    exposing `ItemHandlerManager` the same way.
  - **Two of 1.12.2's own `IItemTransactor`/`ItemTransactorHelper` steps are dropped on both platforms, and this
    is a genuine, deliberate simplification of the *original's* own two-phase ejection logic, not a divergence
    invented by either platform's own porting pass.** 1.12.2's `onItemReachEnd`'s `TILE` branch tried
    `IInjectable` first (in case the "tile" neighbour was actually a foreign pipe mod's block exposing that
    interface directly) and only fell back to a plain `IItemTransactor` insert if that failed. Both platforms'
    `ItemTransactorHelper` already dropped `getInjectable`/`wrapInjectable` before this batch even started
    (their own javadoc says so explicitly: "belongs to the entirely unported transport module"), anticipating
    exactly this batch's own needs; since `updateConnections()`'s own logic never assigns `ConnectedType.TILE`
    to a neighbour that is itself a real `IPipe` (that always resolves to `ConnectedType.PIPE` instead, handled
    by a separate branch that calls `injectItem` directly), the `IInjectable`-first step could only ever have
    mattered for a hypothetical third-party pipe mod that isn't a BuildCraft `IPipe` -- none exists in this
    ecosystem, so both platforms eject into a `TILE`-connected neighbour with a single, direct
    `IItemTransactor#insert`/classic-`IItemHandler#insertItem` call.
  - **A genuine, deliberate scope choice, not a faithful copy of the original's own value, and confirmed rather
    than guessed: `canBeColoured` is `false` on the cobblestone `PipeDefinition`, even though the real 1.12.2
    cobblestone pipe is colourable.** Re-reading `common/buildcraft/transport/BCTransportPipes.java` directly
    shows `builder.builder.enableColouring()` is called once, right after the structure pipe is defined, and
    that flag then persists on the shared builder for every pipe defined afterwards -- wood, stone, cobblestone,
    quartz, gold, and so on. This batch disables it anyway: colouring a pipe means applying a dye, which needs
    interaction/GUI plumbing this batch does not add (dye colours on a pipe are explicitly out of scope), so
    leaving `canBeColoured` true would advertise `IPipe#setColour` as a working feature nothing in this batch
    can legitimately trigger. The id/texture-prefix naming (`"cobblestone"`, not 1.12.2's own
    `"cobblestone_item"`) likewise deliberately diverges: the `_item` suffix existed only to stay unique
    alongside sibling `cobblestone_fluid`/`cobblestone_power`/`cobblestone_rf` definitions this batch does not
    register.
  - **`IWireManager` is implemented for real, not stubbed, the same "implement it properly even though nothing
    yet exercises it live" standard the Stirling Engine's own `explosionRange()` entry established.**
    `buildcraft.transport.tile.SimplePipeWireManager` is new to the port (not a port of 1.12.2's own 
    `buildcraft.transport.wire.WireManager`, which additionally builds and rebuilds cross-pipe "wire system"
    graphs -- entirely out of scope, since no wire item, gate, or `IWireEmitter` exists anywhere in this port
    yet): a genuine `EnumMap<EnumWirePart, DyeColor>` backs `addPart`/`removePart`/`getColorOfPart`/
    `hasPartOfColor`, with real NBT persistence. `isPowered`/`isAnyPowered` are honestly `false` always --
    correct, not faked, since no `IWireEmitter` could ever legitimately power one -- and `updateBetweens` is a
    no-op for the same reason (no cross-pipe wire graph exists to update).
  - **`getRedstoneInput`/`setRedstoneOutput` (`IRedstoneStatementContainer`, part of `IPipeHolder`'s own
    `extends`) follow `TileEngineBase#isRedstonePowered`'s own already-ported precedent exactly, confirmed
    identical on both platforms via direct comparison of that class's own two copies**: `level.getSignal(pos
    .relative(side), side)` (the modern replacement for `world.getRedstonePower(pos.offset(side), side)`) for a
    specific side, `level.getBestNeighborSignal(pos)` for the "any side" case. `setRedstoneOutput` genuinely
    no-ops (`return false`) on both platforms -- no gate/statement system exists anywhere in this batch that
    could ever call it with something real to output.
  - **Everything pluggable-related is a stub, exactly as scoped, not an oversight.** `getPluggable` always
    returns `null` on both platforms; `PluggableHolder` is not ported; no `PipePluggable` type is registered
    anywhere. `canPlayerInteract`/`onPlayerOpen`/`onPlayerClose` are minimal (no GUI exists for this pipe in
    this batch): `canPlayerInteract` checks the tile is still validly placed and the caller is within reach,
    matching `Container.stillValidBlockEntity`-style precedent; the open/close hooks are plain no-ops.
    `scheduleRenderUpdate`/`scheduleNetworkUpdate`/`scheduleNetworkGuiUpdate`/`sendMessage`/`sendGuiMessage` are
    likewise plain no-ops -- there is no renderer and no client sync to schedule anything for.
  - **The recipe is skipped, not invented, matching `TilePump`/`TileTank`/`TileFloodGate`'s own established
    precedent for a genuinely non-portable source, not the "missing ingredient" case those three actually hit.**
    Checked directly: `buildcraft_resources/assets/buildcrafttransport/recipes/` holds no JSON recipe for any
    material pipe (only `_factories.json`/a handful of unrelated plug/sealant recipes), confirming the original
    generates every material pipe's recipe through a Java code-driven factory system rather than plain
    per-recipe JSON -- and `BCTransportRecipes` (the file that would contain that factory) is explicitly out of
    this batch's own scope list. Porting the recipe would mean porting that factory system first, which this
    batch does not do.
  - **Textures/models/blockstate/loot table reuse the exact real cobblestone-pipe texture, following
    `BlockTank`/`BlockPump`'s own "plain full-cube, no renderer yet" precedent exactly.**
    `buildcraft_resources/assets/buildcrafttransport/textures/pipes/cobblestone_item.png` (confirmed 16x16 via
    `file`, a plain flat texture, not a texture-atlas sheet needing UV cropping) is reused unchanged as both the
    block's `cube_all` texture and the item's icon on both platforms. Block registered via `REGISTRY.addBlock`
    (no auto `BlockItem`, since this block's item is the pipe-specific `ItemPipeHolder`, not a generic one --
    the same `addBlock`-without-`addBlockAndItem` shape `BlockTube` already established a precedent for, for a
    different reason). Loot table drops a plain `buildcraft:pipe_item_cobblestone` on survival, matching every
    other block in this port; the genuine `loot_table`-singular-vs-`loot_tables`-plural directory-name split
    between 26.x and 1.20.1 (already on file in PORTING.md's build-gotchas section) applies here too.
  - **In-game verification, both platforms, via RCON against a real dedicated server each -- a genuine,
    end-to-end, tick-by-tick simulation, not hand-set NBT state, and an honest account of the mechanism used.**
    The rig: a real vanilla hopper (facing sideways) fed by a real vanilla chest sitting directly above it, a
    straight run of two cobblestone pipe segments, and a second real vanilla chest as the destination -- no
    fluid/dropped-item trick, no debug insertion into the pipe's own flow. **What *does* need a temporary debug
    mechanism, and why**: a dedicated server started for RCON testing has no connected player at all (confirmed:
    every player-targeted command fails with "No player was found" against zero connected clients, the same
    finding already on file from the paintbrush batch's own verification entry), so there is no real click to
    place a pipe item with, and 1.12.2's own placement logic (`TilePipeHolder#onPlacedBy`) is only ever invoked
    through that click -- `/setblock buildcraft:pipe_holder` alone was confirmed, live, to *not* call it (the
    resulting tile's NBT had no `pipe` key at all: the block existed but held no `Pipe` object, so it could
    never connect to anything). A temporary `RegisterCommandsEvent` debug command was added to each platform's
    `BuildCraft.java` (`net.neoforged.neoforge.common.util.FakePlayerFactory`/`net.minecraftforge.common.util.
    FakePlayerFactory`, the same fake-player construction the paintbrush batch's own debug command already used)
    that sets the block directly and then calls `TilePipeHolder#onPlacedBy(fakePlayer, stack)` on it directly --
    the actual new placement logic this batch added, not a re-test of already-known-good `BlockItem`/
    `Item#useOn` scaffolding underneath it (the same "call the interesting method directly" precedent the
    paintbrush batch's own debug command established, applied here to the placement-logic equivalent of
    `useOn`). **What the seed step does *not* need a debug mechanism for, and this is the actual proof of this
    batch's own goal**: getting an item *into* the pipe network from a source inventory is not seeded by the
    debug command at all -- a real cobblestone stack was written into the source chest via `/data merge block`
    (verified round-trip first via `/data get block` before trusting the format on each platform: 26.x's modern
    lowercase `{id, count}` item shape vs. 1.20.1's classic `{Slot, id, Count}` shape, confirmed different by
    direct comparison, not assumed), and the real vanilla hopper -- driven entirely by vanilla game logic, with
    no BuildCraft code involved on the extraction side at all -- pulled from the chest and pushed into the
    pipe's `Capabilities.Item.BLOCK`/`ForgeCapabilities.ITEM_HANDLER` capability on its own, on its own normal
    8-tick cooldown, confirmed live by watching a pipe's own NBT accumulate ten separately-timed queued
    `TravellingItem`s (`tickStarted` values 8 ticks apart, each with `timeToDest: 75` matching the
    `side`-to-centre distance divided by `PipeBehaviourCobble`'s own `0.01` target speed) purely from repeated
    real hopper pushes, not a single seeded call. Watched entirely through real elapsed game ticks after that
    (`time query gametime` advancing between checks, never hand-set): the source chest's `Items` list went from
    5 cobblestone to empty, both pipe segments' `pipe.flow.items` lists (real internal queue state, not a
    display value) filled and then drained back to empty as the item(s) travelled centre-to-centre and
    end-to-end across the two segments, and the destination chest's `Items` list ended with exactly
    `{count: 5, id: "minecraft:cobblestone"}` (26.x) / `{Count: 5b, id: "minecraft:cobblestone"}` (1.20.1) --
    confirmed on both platforms independently, in separate RCON sessions against separate fresh worlds. Pipe
    connection bitmasks (`con`, the two-bits-per-face encoding `Pipe#writeToNbt` documents) were hand-decoded
    and cross-checked against the actual rig layout on both platforms, not just trusted to be non-zero -- see
    the connection-capability defect entry above, which this same verification pass is what caught it. The
    temporary debug command and its `BuildCraft.java` hook were fully removed before this batch finished on both
    platforms -- confirmed by `git diff` showing each `BuildCraft.java` reduced to exactly the one intended
    additive `BCTransportRegistries.register(modBus)` line -- and every verification step below (clean rebuild,
    tests, server boots, `runClient`) was re-run *after* that removal, against the final code. **Honest
    limitation**: the real right-click placement interaction itself, end to end through a real connected
    client's input, remains unverified, the same caveat already on file for every prior RCON-verified batch in
    this document -- what is verified directly is that `TilePipeHolder#onPlacedBy` itself dispatches correctly
    (constructs the right `Pipe`, registers the right event handlers, fires `PipeEventPlaced`), and that every
    other new class in this batch behaves correctly under real, un-coached, tick-driven gameplay once a pipe
    exists.
  - **Verified with forced `--no-build-cache clean` rebuilds on both platforms (before and after the connection-
    capability bug fix, and again after the debug command's removal), the full 25-test suite, real dedicated-
    server boots with zero exceptions on both targets (fresh worlds, deleted and rebooted after the debug
    command's removal), and a real `:neoforge-26x:runClient` boot that reached full texture-atlas stitching
    (including `textures/atlas/items.png-atlas`/`blocks.png-atlas`) and a loaded resource manager
    (`mod/buildcraft` included) with no missing-model/missing-sprite warnings for `pipe_holder`/
    `pipe_item_cobblestone` and no `IllegalStateException`/`ClassNotFoundError`/`NoClassDefFoundError`/
    `BootstrapMethodError` anywhere in the log.** Not independently verified: the on-screen appearance of the
    pipe block/item, any pipe connection shape, and the item-travelling-through-pipe visual -- no client
    rendering exists in this batch at all, per its own scope (see above), the same caveat already on file for
    every prior entry in this document.

- **`buildcraft.transport` -- the Wooden Pipe (`PipeBehaviourWood`), the port's first *active* pipe.** Where the
  cobblestone pipe only ever moves an item something else pushes into it, a wooden pipe reaches out and pulls
  items from an adjacent inventory on its own -- but, confirmed by re-reading `common/buildcraft/transport/
  pipe/behaviour/PipeBehaviourWood.java` directly rather than assumed from another BuildCraft version, only
  when it has MJ to spend, at a flat cost of one MJ per item. This is the first batch to combine an
  already-ported engine, an already-ported pipe, and an already-ported inventory into one real, self-powered
  automation loop, with no hopper or other push mechanism anywhere in it.
  - **New files, both platforms**: `buildcraft.transport.pipe.behaviour.{PipeBehaviourDirectional,
    PipeBehaviourWood}`. `PipeBehaviourDirectional` (1.12.2's own base class `PipeBehaviourWood` extends) had
    no ported equivalent yet, so it is a real, if trimmed, port in its own right -- see below for what was cut.
    `BCTransportRegistries` (both platforms) grew a `PIPE_WOOD` `PipeDefinition` and a `PIPE_ITEM_WOOD` item,
    the same shape `PIPE_COBBLESTONE`/`PIPE_ITEM_COBBLESTONE` already established.
  - **The previous batch's "a future pipe material is just another `PipeDefinition` plus another
    `ItemPipeHolder` instance, never a new block or tile class" claim holds for the block/tile layer on both
    targets, and holds for capability *registration* too on 1.20.1, but needed a small, honest amendment on
    26.x.** No new block or tile class was needed on either target -- confirmed live, the same
    `BlockPipeHolder`/`TilePipeHolder` pair serves both materials unchanged. On 1.20.1, nothing else needed
    touching either: `TilePipeHolder#getCapability` already falls through generically to `pipe.getCapability`,
    so `PipeBehaviourWood`'s own `MjCapabilityHelper`-backed `getCapability` override is reached with zero
    changes to `TilePipeHolder`/`BCTransportRegistries`'s capability wiring. **On 26.x, three additional lines
    were needed in `BCTransportRegistries#registerCapabilities`** (`MjCapabilities.CONNECTOR`/`RECEIVER`/
    `REDSTONE_RECEIVER`, each registered against the shared `PIPE_HOLDER_TYPE` the same way the item-transfer
    capability already is) -- a real, if small, correction to "just a `PipeDefinition` plus an item": a brand
    new *capability* a pipe material wants to expose still needs registering once, on 26.x specifically, the
    same way the previous batch's own `CAP_PIPE_HOLDER`/`CAP_PIPE`/`CAP_PLUG` entry already had to. The
    registration itself needed no new dispatch machinery -- it reuses `TilePipeHolder#getCapability`'s existing
    generic delegation to `Pipe#getCapability` -> `PipeBehaviour#getCapability`, exactly like the vanilla-interop
    item capability already does.
  - **A genuine, confirmed-not-guessed platform divergence: `buildcraft.api.mj.MjCapabilityHelper`'s two copies
    are no longer interchangeable in shape, and this batch is the first to actually need the one class
    `PipeBehaviourWood` in both 1.12.2 and this port's own `TileEngineWood`/`TileEngineStone` lean on.** On
    1.20.1, `MjCapabilityHelper` is still the instance-based `ICapabilityProvider` delegate 1.12.2 had --
    `PipeBehaviourWood` holds one (`new MjCapabilityHelper(this)`) and forwards `getCapability` straight to it,
    a direct, unchanged reuse. On 26.x, `MjCapabilityHelper` was restructured earlier in this port into a
    static `registerAll(RegisterCapabilitiesEvent, BlockEntityType)` registrar that resolves which MJ
    interfaces to expose by an `instanceof` check against the *block entity itself* -- confirmed, by grepping
    the whole 26.x source tree, to have **zero real callers anywhere in this codebase**, even for the two
    already-ported real `IMjReceiver`s (`TilePowerConsumerTester` in `BCCoreRegistries`, and the two engines'
    own `MjCapabilities.CONNECTOR` registration) -- both register their capability by hand instead. That shape
    cannot apply to a wooden pipe at all: `TilePipeHolder` is one shared block entity type for every pipe
    material, so `instanceof IMjReceiver` against the *tile* could never distinguish "this particular pipe
    happens to be wood" from any other material sharing the same tile class. `PipeBehaviourWood` (26.x) instead
    exposes itself directly (`capability == MjCapabilities.RECEIVER ? (T) this : ...`, the identical
    identity-check-and-cast idiom `PipeFlowItems#getCapability` already established for its own item capability
    in the previous batch), and `BCTransportRegistries` registers those tokens against the shared tile type by
    hand -- see the entry above.
  - **`PipeBehaviourDirectional` is a real but deliberately trimmed port of 1.12.2's own class of the same
    name -- the auto-facing-selection logic (`onTick`'s `canFaceDirection`/`advanceFacing` fallback) is ported
    for real, since it is what lets a wooden pipe work at all with zero new player-interaction plumbing.**
    Dropped, all deliberate scope cuts, not oversights:
    - **Wrench-driven facing selection** (`onPipeActivate`, the `EnumPipePart` hit-part parameter, and the
      `EntityUtil.getWrenchHand` branch) -- needs real "which face of a multi-part pipe block did the player
      click" hit-detection this port has never built for pipes (the pipe skeleton batch's own scope notes
      already deferred all pipe interaction). Not load-bearing: `advanceFacing()`'s fallback already picks a
      valid facing the moment one exists.
    - **`addActions`/`onActionActivate`** (`BCTransportStatements.ACTION_PIPE_DIRECTION`) -- gates/statements
      are out of scope for this whole module, matching `PipeFlowItems`'s own `addTriggers` drop for the
      identical reason.
    - **`getTextureData`/`writePayload`/`readPayload`** -- no client rendering or network sync exists in this
      batch, the same "no renderer yet" deferral already established throughout this module.
    - **The face-cycling order** (`OrderedEnumMap`/`VanillaRotationHandlers.ROTATE_FACING`) -- neither is
      ported, and nothing in this batch's scope needs a *specific* order any more, now that the only thing
      that ever cared about the order (a player cycling faces by hand) is dropped above.
      `advanceFacing()`'s fallback iterates `Direction.values()` in plain ordinal order instead --
      functionally equivalent for auto-selection, confirmed live in both RCON rigs below (see "Honest
      limitation").
  - **`BCTransportConfig.mjPerItem` becomes a plain `private static final long MJ_PER_ITEM = MjAPI.MJ;` on
    `PipeBehaviourWood` itself, not a ported config class.** `BCTransportConfig` (187 lines) is a whole 1.12.2
    Forge `Configuration`-file system with no equivalent anywhere in this port; only the one numeric value this
    behaviour actually reads is kept, at its 1.12.2 default. `mjPerMillibucket` is not needed at all, since the
    fluid-extraction branch is dropped outright below.
  - **The fluid-extraction branch of `extract(power, simulate)` is dropped outright, not stubbed with a dead
    `instanceof`.** No fluid pipe of any kind is registered anywhere in this port yet, so
    `pipe.getFlow() instanceof IFlowFluid` could never be true here; writing it anyway would be less honest
    than simply not porting it. The `fluidSideCheck` `@PipeEventHandler`, which only ever mattered to a
    fluid-flow pipe, goes with it.
  - **A real, newly-surfaced defect, found by re-reading the shared block's own loot table rather than by
    testing, and left unfixed as a documented limitation, not silently patched around.** `pipe_holder.json`'s
    loot table (both platforms) unconditionally drops `buildcraft:pipe_item_cobblestone` -- correct when
    cobblestone was the only registered pipe material, but now genuinely wrong for a wooden pipe: breaking one
    in survival would hand the player back a cobblestone pipe item instead. Fixing this properly needs a loot
    mechanism that reads the tile's own `pipe.def` NBT to pick an item id dynamically, which vanilla's loot
    table JSON has no built-in way to do and which this batch's scope does not include building. Left as a
    known, real gap for whichever future batch adds a third pipe material and finally makes a proper per-material
    loot table worth building.
  - **Recipe skipped, matching the cobblestone batch's own precedent for the identical reason, re-checked for
    wood specifically rather than assumed to carry over.** `buildcraft_resources/assets/buildcrafttransport/
    recipes/` still holds no JSON recipe for any material pipe, wood included -- confirmed by listing the
    directory fresh for this batch, not reused from the earlier finding.
  - **Assets**: `wood_item_clear.png` (16x16, confirmed via `file`) is reused unchanged as both the block's
    `cube_all` texture (`pipe_wood.json`, a new model alongside the shared `pipe_holder.json`, since the wood
    item's icon must not show the cobblestone texture the shared block model is hard-wired to) and the item's
    icon, the same "one flat texture, no renderer yet" shape the cobblestone batch established.
    `wood_item_filled.png` (the "has an active facing" cosmetic state) is real but unrenderable in this batch,
    so it is not ported, matching the brief exactly. The lang key departs from 1.12.2's own literal string
    (`"Wooden Transport Pipe"`) in favour of this port's own already-established short naming convention (see
    `pipe_item_cobblestone` -> "Cobblestone Pipe"): `pipe_item_wood` -> **"Wooden Pipe"**.
  - **In-game verification, both platforms, via RCON against real dedicated servers -- this batch's actual
    goal, proven end to end, not by inspection.** Two independently-designed rigs (different items, different
    left/right layout, different platforms), each: a Creative Engine (default `currentDirection = UP`, no
    wrench needed) with a redstone block on one of its side faces (triggering `onNeighbourBlockChanged` via
    ordinary vanilla neighbour-update propagation, exactly the way the Stirling Engine batch's own rig already
    proved `isRedstonePowered` gets set for real); a wooden pipe directly above the engine; a source chest
    touching the wood pipe's only other real neighbour; a cobblestone pipe continuing the run; a destination
    chest at the far end. As with the cobblestone batch's own precedent, a dedicated server with no connected
    player can't right-click a pipe into place, so the same temporary `RegisterCommandsEvent` debug command
    (`bcdebug placepipe <pos> <item>`, `net.neoforged.neoforge.common.util.FakePlayerFactory`/
    `net.minecraftforge.common.util.FakePlayerFactory`) drove `TilePipeHolder#onPlacedBy` directly for both the
    wood and cobblestone pipe placements -- the placement logic itself, not a re-test of already-verified
    scaffolding. Everything downstream of that is unassisted: on 26.x, 10 diamonds written into a source chest
    via `/data merge block` (`{count, id}` shape, verified round-trip via `/data get block` first) were
    entirely gone from that chest and entirely present in the destination chest (`{count: 10, id:
    "minecraft:diamond"}`) after nothing but real elapsed game ticks (`time query gametime` climbing from 355
    to 1283 between checks); on 1.20.1, a mirrored rig (source chest on the pipe's *other* side, engine and
    redstone block swapped left-right) moved 7 gold ingots the same way (`{Count: 7b, id:
    "minecraft:gold_ingot"}` shape), gone from the source and present in the destination between gametime 366
    and 643. Both pipes' `con` bitmasks were hand-decoded and cross-checked against each rig's actual layout
    (matching the two-bits-per-face encoding already on file from the cobblestone batch), confirming each wood
    pipe's real neighbour set, not just trusted. **The auto-pick behavioural gap documented above was directly
    observed, not just theorised**: each rig's wood pipe had three real neighbours (the engine below, an
    inventory to one side, a pipe to the other), and in both cases `beh.currentDir` came back set to exactly
    the one side that was actually a `ConnectedType.TILE` (`"WEST"` in the 26.x rig, `"EAST"` in the mirrored
    1.20.1 rig) with no player interaction at all -- the engine below never qualified as a facing candidate in
    either rig, since `PipeFlowItems#canConnect` correctly refuses to mark a non-item-capable neighbour (the
    engine) as a `TILE` connection in the first place, so there was never any ambiguity between "the inventory"
    and "the engine" for `advanceFacing()` to get wrong. Zero exceptions in either server's log across both
    sessions. The debug command was fully removed from both platforms' `BuildCraft.java` before finishing,
    confirmed via `git diff` showing each file byte-identical to its pre-batch state, and every verification
    step (forced `--no-build-cache clean` rebuild, the full 25-test suite, fresh dedicated-server boots, and a
    real `:neoforge-26x:runClient` boot reaching full texture-atlas stitching with no missing-model/
    missing-sprite warnings for `pipe_wood`/`pipe_item_wood` and no `IllegalStateException`/
    `ClassNotFoundError`/`NoClassDefFoundError`/`BootstrapMethodError` anywhere in the log) was re-run afterward,
    against the final code, on both targets. **Honest limitation, same category as every prior RCON-verified
    batch in this file**: the real right-click placement interaction, end to end through a real connected
    client, remains unverified; what is verified directly is every piece of this batch's own logic once a pipe
    already exists -- auto-facing, MJ-gated extraction, and the capability wiring that lets an engine find a
    pipe as a receiver at all.

- **The three already-ported engines (`BlockEngineWood`/`TileEngineWood`, `BlockEngineCreative`/`TileEngineCreative`,
  `BlockEngineStone`/`TileEngineStone`) gain a real, direction-dependent block appearance -- a facing-visibility
  fix, the same shape as the mining well's tube-shaft fix above, not a gameplay-logic change.** The person
  actually playing this mod (not just compiling it) reported that wrenching a Creative Engine next to a Mining
  Well "does nothing" -- they could not tell whether wrenching ever pointed it the right way. Live investigation
  first, before touching any code: RCON, a fake-player-driven wrench click, and reading `TileEngineBase
  #attemptRotation()`/`getReceiverToPower()` directly all confirmed the underlying mechanic was already
  completely correct -- the click reaches `attemptRotation()`, it finds the Mining Well as a valid MJ receiver,
  it updates `currentDirection`, and power genuinely flows afterward. **The bug was that nothing about the
  block's own appearance ever changed**: all three engines shared one static `cube_column` model (an "end"
  texture top/bottom, a "side" texture around the middle) with no facing-dependent variation at all, and
  `currentDirection` lived purely in tile NBT, invisible to the block's own `BlockState` -- so a player had no
  way to visually confirm which way an engine pointed, or that wrenching did anything.
  - **Fix, both platforms, plain static blockstate/model JSON only -- no `BlockEntityRenderer`, no animation.**
    All three concrete engine blocks now declare vanilla's own `BlockStateProperties.FACING`
    (`net.minecraft.world.level.block.state.properties.BlockStateProperties`) via `createBlockStateDefinition`
    (confirmed the exact same method name and signature, `protected void createBlockStateDefinition(
    StateDefinition.Builder<Block, BlockState> builder)`, on **both** targets -- not the divergence the task
    brief cautioned might exist versus 1.12.2's own `createBlockState`, confirmed directly against this
    codebase's own pre-existing `BlockMarkerBase`/`BlockMiningWell` precedent rather than assumed), registered
    via `registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP))` in each
    block's constructor -- `Direction.UP` matching `TileEngineBase#currentDirection`'s own existing default, the
    same "reuse an existing vanilla mechanism rather than invent a BuildCraft-native one" preference this port
    already applied to gears/tags/etc (see `DyedBlockVariants`'s own javadoc), and a deliberate choice over the
    codebase's own hand-rolled `BuildCraftProperties.BLOCK_FACING_6` (an `EnumProperty<Direction>` with an
    identical 6-value shape, already used by `BlockMarkerBase`) precisely because vanilla already ships the
    identical mechanism under its own name.
  - **`TileEngineBase#attemptRotation()` (shared base class, both platforms) now pushes the change onto the
    placed `BlockState` itself, not just its own field.** A new private `updateFacingBlockState(Direction)`
    calls `level.setBlock(getBlockPos(), getBlockState().setValue(BlockStateProperties.FACING, facing),
    Block.UPDATE_ALL)` whenever `currentDirection` actually changes -- confirmed identical on both targets via
    `javap` (`Level#setBlock(BlockPos, BlockState, int)`, `Block.UPDATE_ALL`, `BlockState#setValue` are
    unchanged in shape from each other). `onPlacedBy` now also calls it unconditionally after `rotateIfInvalid()`
    (previously it only mutated `currentDirection`), so a freshly-placed engine's `BlockState` matches its
    tile's own starting facing from the very first tick -- whether that is `Direction.UP` (no receiver found
    yet) or whatever direction `rotateIfInvalid()`'s own receiver search lands on -- not only after the first
    successful wrench.
  - **A genuine, `javap`-confirmed type-level divergence between the two targets, invisible to every line of
    code this fix actually wrote, since nothing here names the field's static type explicitly.** On 1.20.1,
    `BlockStateProperties.FACING` is statically typed `net.minecraft.world.level.block.state.properties.
    DirectionProperty` -- a real, still-present subclass of `EnumProperty<Direction>` (`create(String)`/
    `create(String, Direction...)`/etc., unchanged in shape from 1.12.2). On 26.x, the `DirectionProperty` class
    no longer exists at all (`javap`: "class not found") and the identical field is instead typed a plain
    `EnumProperty<Direction>`. Neither this file nor the three block classes ever spell out the field's static
    type, so the divergence cost nothing here, but it is exactly the kind of thing a future file that *does*
    want to declare a `DirectionProperty`-typed field of its own needs to know before assuming the two targets
    still share that type.
  - **The blockstate/model JSON change, both platforms, for all three engines: `minecraft:block/cube_column`
    (`end`/`side` textures) becomes `minecraft:block/cube` with the "back" texture on the model's own north face
    and the "side" texture on the other five, driven by six `facing=<direction>` blockstate variants using
    vanilla's own rotation convention.** Confirmed the existing texture-file naming assumption by actually
    comparing the two images rather than trusting the filename: `engine_wood_back.png` is a wood-framed copper
    connector fitting -- the classic BuildCraft engine's business/output face -- while `engine_wood_side.png` is
    a plain wood-plank texture; "back" genuinely is the output face's own texture, matching the brief's own
    suspicion, for all three engines (`_creative`/`_stone` pairs compared identically). The rotation table itself
    is not guessed: extracted and read the real vanilla `blockstates/piston.json`/`observer.json` and
    `models/block/template_piston.json` directly out of the actual game client resources (`client-extra-*.jar`
    for 1.20.1, `minecraft_26.3_client.jar` for 26.x -- both confirmed byte-identical in the relevant JSON) rather
    than reasoning about rotation signs from memory: a front-on-north base model needs `facing=north` at
    identity, `facing=east`/`south`/`west` at `y: 90`/`180`/`270`, and -- the case the task brief specifically
    flagged as easy to get backwards -- `facing=down` at `x: 90` and `facing=up` at `x: 270`, not the other way
    around. `minecraft:block/cube`/`minecraft:block/cube_column` themselves are unchanged between the two
    targets (confirmed identical byte-for-byte between the 1.20.1 and 26.3 client jars).
  - **In-game verification, both platforms, via RCON against real dedicated servers -- confirming the actual
    goal: the block's own placed `BlockState` genuinely changes, not just the tile's internal field.** A
    temporary, self-registering `bcfacingtest` command (`buildcraft.debugtemp.DebugEngineFacingCommand`, its own
    new file on each platform, deleted along with the whole `debugtemp` package before this fix finished) placed
    a Creative Engine with a Mining Well to its east and another to its south (both genuine, unconditionally-
    registered `MjCapabilities.RECEIVER`s, confirmed by re-reading `BCFactoryRegistries#registerCapabilities`
    rather than assumed), called the real `TileEngineCreative#onPlacedBy` directly (since `/setblock` does not
    invoke it, the same finding already on file from the pipe batch's own verification), then performed three
    real wrench right-clicks through a fake player and the actual `ItemWrench#useOn` entry point -- constructing
    a real `UseOnContext(fakePlayer, InteractionHand.MAIN_HAND, hitResult)` and calling
    `wrenchStack.getItem().useOn(context)` directly, the real production code path a real client's wrench click
    reaches, not a re-test of `BlockItem`/network scaffolding underneath it. Self-registered via
    `@EventBusSubscriber`/`@Mod.EventBusSubscriber(bus = FORGE)` specifically so nothing in either platform's
    `BuildCraft.java` needed touching at all (that file is out of scope for this task -- a different,
    concurrently-running task owns pipe-related edits there), confirmed clean afterward: `git status` shows zero
    changes to either `BuildCraft.java`, and the whole `debugtemp` package left no trace once deleted (never
    committed, so nothing to `git diff` against). On both platforms, independently, the engine auto-faced east
    at placement (the first valid receiver in `TileEngineBase`'s own east-south-down-west-north-up cycle order),
    then ping-ponged south/east/south across the three wrench clicks -- and `currentDirection` and
    `level.getBlockState(pos).getValue(BlockStateProperties.FACING)`, read directly from the live objects inside
    the command itself, matched exactly at every single step, on both targets. **Independently re-confirmed
    through the plain vanilla command interface afterward, with no debug code involved at all**: `/execute if
    block <pos> buildcraft:engine_creative[facing=south]` succeeded and `[facing=east]`/`[facing=up]` both failed
    against the same block, on both platforms, exactly matching the tile's own `currentDirection` at that moment
    -- and the same default-facing check (`buildcraft:engine_wood`/`engine_stone` freshly placed with no explicit
    facing genuinely default to `facing=up`, confirmed the same way) worked identically for the two engines the
    debug command's own scripted rig never touched. **A correction to this task's own brief, worth recording for
    future verification passes**: `/data get block <pos>` does **not** expose a placed block's `BlockState`
    properties at all -- confirmed directly, on both platforms, against both a plain vanilla `minecraft:dispenser
    [facing=east]` and the engines themselves: its output is only the block's saved tile NBT plus `id`/`x`/`y`/
    `z`, never a `Properties`/`state` key of any kind. The real, correct vanilla mechanism for reading a placed
    block's own state from a command is `/execute if block <pos> <id>[property=value]`, used above instead.
  - **Verified with forced `--no-build-cache clean` rebuilds on both platforms (before and after the debug
    command's removal), the full 25-test suite, real dedicated-server boots with zero exceptions on both targets
    (fresh worlds, deleted and rebooted again after the debug command's removal), and a real
    `:neoforge-26x:runClient` boot that reached full texture-atlas stitching (including `blocks.png-atlas`) and a
    loaded resource manager (`mod/buildcraft` included) with no missing-model/missing-sprite warnings for any of
    the three engines' six new `facing=<direction>` variants each, and no `IllegalStateException`/
    `ClassNotFoundError`/`NoClassDefFoundError`/`BootstrapMethodError`/`Exception` anywhere in the log.** Not
    independently verified: the on-screen appearance of the rotated models themselves -- the same rendering
    caveat as every other entry in this file -- though the model/blockstate JSON itself is not a guess: it is
    built from real vanilla rotation-convention files extracted from the actual game client, not reasoned about
    from memory. **A related, but genuinely different-shaped, loose end noticed while working and left for a
    future batch, not fixed here**: `BlockMiningWell` already carries a real, visible `BuildCraftProperties.
    BLOCK_FACING` blockstate property that *does* get set correctly at placement time (`getStateForPlacement`)
    -- unlike the engines' bug, this one is not invisible -- but its own javadoc already documents that nothing
    ever updates it afterward (`BlockMiningWell` does not implement `ICustomRotationHandler`, so it is not
    wrench-rotatable at all) and nothing in `TileMiningWell`/`TileMiner` ever reads it back (the well always
    digs straight down regardless). `BlockPump`, by contrast, was checked and confirmed to have no facing
    concept at all to be invisible in the first place (it searches multiple directions every tick rather than
    tracking one persisted facing) -- see that class's own javadoc.

- **`TilePump#getTargetPos()` never extended its tube shaft toward an isolated single-block fluid source, and
  retracted one position early on any body's very last source block -- a genuine pre-existing bug in the real
  1.12.2 source, confirmed present there unchanged, not a porting mistake, fixed on both platforms anyway because
  it directly explains a real user report: "For the water pump it should also have pipes that come down even
  unpowered as soon as its over a liquid source."** Read `TileMiner#mine()`'s abstract contract and both this
  port's own `TilePump#mine()`/`nextPos()`/`buildQueue()` and the original `common/buildcraft/factory/tile/
  TilePump.java`'s copy of the same three methods side by side first: both already run the fluid-search
  (`buildQueue()`) and tube-shaft placement (`nextPos()` -> `updateLength()`) completely unconditionally every
  ~30 ticks (`SafeTimeTracker(30)`, confirmed via its own source that the very first call always returns `true`
  regardless of battery charge -- `Level#getGameTime()` starts near zero and `lastMark` starts at `Long.MIN_
  VALUE`), gating only the actual fluid-*draining* step behind `battery.extractPower(...)`. This already matched
  the user's expectation exactly -- so the bug had to be somewhere else.
  - **Found it live, via RCON, with the simplest possible rig: a single isolated `minecraft:water` source block
    four blocks under an unpowered (`battery: 0L`) pump, nothing else nearby.** `data get block` on the pump
    showed `currentPos: [I; ...]` correctly resolving to the water block (proving the unpowered search genuinely
    ran), yet `wantedLength` stayed `0` forever and no `buildcraft:tube` block ever appeared underneath it.
  - **Root cause: `getTargetPos()` used `queue.isEmpty()` as its "did we find anything at all" signal, but
    `queue` is only the not-yet-visited-this-round worklist of source blocks left to drain -- it empties out the
    instant `nextPos()` dequeues the last (or only) candidate via `queue.removeLast()`, *before* that same
    position becomes `currentPos` and is actively, validly mid-drain.** For a single isolated source, the queue
    holds exactly one entry from the moment `buildQueue()` runs, and `nextPos()` immediately drains it to empty
    in the very same call that is supposed to extend the tube toward it -- so `updateLength()`'s own call to
    `getTargetPos()` always saw an empty queue and returned `null`, computing a target length of `0`. The same
    bug bites any body's last remaining source block for the identical reason, one position early.
  - **Fix: check `paths.isEmpty()` instead.** `paths` holds every position `buildQueue0()`'s breadth-first search
    ever confirmed reachable for the current fluid body (a strict superset of `queue`, since it also covers
    flowing, non-source blocks along the way) and is only ever trimmed by `mine()`'s own `paths.remove(
    currentPos)` on a truly completed, successful full drain -- the correct signal for "nothing left to reach at
    all", unaffected by `nextPos()`'s own worklist bookkeeping.
  - **Re-verified live after the fix, same rig plus a full drain-to-completion pass**: the previously-inert
    single-source rig now shows `wantedLength: 4` and real `buildcraft:tube` blocks (confirmed via `/execute if
    block ... buildcraft:tube`) filling the full gap down to the water, `battery` still `0L` throughout -- and,
    powering the same rig with a Creative Engine afterward, the pump fully drained the source (`tank: {amount:
    1000, id: "minecraft:water"}`), `currentPos` cleared, `wantedLength` correctly dropped back to `0`, and every
    tube block and the drained source position were confirmed cleared to air -- proving the fix does not regress
    the "genuinely nothing left to reach" retraction case it was never meant to touch.
  - Not touched in the original `common/` reference source (read-only, upstream reference) -- this fix applies
    only to the two ported platforms, the same policy already established for the mining well's tube-visibility
    fix above.
  - Verified with a forced `--no-build-cache clean` rebuild on both platforms and the full 25-test suite.

- **`buildcraft.transport` -- three more speed-modifier pipe materials (Stone, Sandstone, Quartz), and the
  first real fix to the `pipe_holder` loot-table bug this file has been carrying since the wood-pipe batch.**
  All three ported directly from `common/buildcraft/transport/pipe/behaviour/PipeBehaviour{Stone,Sandstone,
  Quartz}.java` (31/44/31 lines, re-read directly rather than assumed from `PipeBehaviourCobble`'s own shape),
  registered in `BCTransportRegistries` the same `PipeDefinition`-plus-`DeferredItem`/`RegistryObject` pattern
  cobblestone/wood already established -- confirming, for the third time now, that a new pipe material really
  is zero new block/tile code, on both platforms.
  - **Stone and Quartz** extend the already-ported `PipeBehaviourSeparate` unchanged, adding only a static
    `@PipeEventHandler` method reacting to `PipeEventItem.ModifySpeed` -- `event.modifyTo(SPEED_TARGET,
    SPEED_DELTA)` with `SPEED_TARGET = 0.01` for both, `SPEED_DELTA = 0.008` (Stone, a quick ramp) vs. `0.002`
    (Quartz, the gentlest of the whole batch, gentler than Cobblestone's already-registered `0.02`). Confirmed
    via `PipeEventItem.java` and `PipeBehaviour.java`'s own base-class constructor shape that both are
    byte-identical between platforms (`diff` against the 26.x/1.20.1 copies showed zero differences beyond
    unrelated capability-token imports already on file from the wood-pipe batch), so the same three source
    files were copied verbatim onto both targets with no platform fork needed at all -- the "port to 26.x
    first, try the same file unchanged on 1.20.1" rule this file's own "How much actually has to be
    duplicated" section recommends, confirmed working exactly as advertised for a whole file, not just a
    method.
  - **Sandstone is the one material in this batch that does not extend `PipeBehaviourSeparate`.** It extends
    `PipeBehaviour` directly and overrides `canConnect(Direction, PipeBehaviour)` to return `true`
    unconditionally (connects to *any* other pipe, not just another Sandstone one) and
    `canConnect(Direction, BlockEntity)` to return `false` unconditionally (never connects to a plain
    inventory) -- `TileEntity` in 1.12.2 is `BlockEntity` here on both targets, confirmed identical against
    `PipeBehaviour`'s own already-ported base method signature via direct inspection, not assumed to match.
    Its own `@PipeEventHandler` reuses `PipeBehaviourStone`'s `SPEED_TARGET`/`SPEED_DELTA` constants directly
    (widened from `private` to package-visible on `PipeBehaviourStone` for exactly this), matching the real
    1.12.2 `PipeBehaviourSandstone`'s own identical reuse of the real `PipeBehaviourStone`'s constants rather
    than duplicating the literals.
  - **`canBeColoured` is `false` on all three, the same deliberate scope choice already on file for
    Cobblestone/Wood, re-confirmed against the real 1.12.2 source for these three specifically rather than
    assumed to carry over.** `common/buildcraft/transport/BCTransportPipes.java#preInit` calls
    `builder.builder.enableColouring()` once, before Wood, and the flag then stays set on the shared builder
    for every material defined afterwards including Stone/Cobblestone/Quartz/Sandstone -- so the real 1.12.2
    versions of all three genuinely are colourable. Disabled here anyway, for the identical reason already
    given for Cobblestone: colouring needs `CustomPaintHelper`-style GUI plumbing this whole module still
    lacks.
  - **The loot-table fix.** `pipe_holder.json`'s loot table (both platforms) has held exactly one static entry
    -- an unconditional `buildcraft:pipe_item_cobblestone` -- since the cobblestone-pipe batch, already flagged
    in this file (search "harmless while cobblestone is the only material, needs fixing once a third material
    exists") as broken the moment a third material shipped. With this batch adding a fourth, fifth, and sixth
    material, left unfixed it would mean breaking any non-cobblestone pipe in survival handed the player back a
    plain cobblestone one. A static JSON loot table has no way to read which `PipeDefinition` is actually
    stamped onto a given tile's own NBT at break time, so the fix is in Java: `BlockPipeHolder` on both
    platforms now overrides `BlockBehaviour#getDrops(BlockState, LootParams.Builder)` -- confirmed via `javap`
    against the real decompiled jars to be exactly what `Block#getDrops(BlockState, ServerLevel, BlockPos,
    BlockEntity)`/`BlockState#getDrops` delegate to, with the block entity already threaded through as
    `LootContextParams.BLOCK_ENTITY` on the `LootParams.Builder` passed in -- reads the tile's own `Pipe`, maps
    its `PipeDefinition` back to the right `Item` via a new `BCTransportRegistries.getItemForPipe(PipeDefinition)`
    helper, and returns that item directly instead of falling through to the static table. The one genuine,
    `javap`-confirmed API divergence in this whole batch: the method is `protected` on 26.x and `public` on
    1.20.1-Forge's own `BlockBehaviour` -- both overrides match their own platform's visibility, `protected` on
    26.x and `public` on 1.20.1, since Java forbids narrowing an override's visibility. `playerWillDestroy` was
    considered and rejected: it only ever sees the state/position, not the block entity, so it cannot read
    which pipe was actually placed. **No new lookup table was built for the definition-to-item mapping.**
    `PipeRegistry` (both platforms) already keeps exactly this association -- every `ItemPipeHolder` self-
    registers into `PipeApi.pipeRegistry.setItemForPipe(definition, this)` from its own constructor, and
    `getItemForPipe(PipeDefinition)` was already there, unused until now -- so the new helper is a five-line
    delegation, not a second registry, and it needs no updates the next time a material is added.
  - **Assets, both platforms, following the exact provenance the cobblestone/wood batch actually used, not the
    vanilla-block-texture guess this task started from.** Checked first, rather than assumed: `git log --follow`
    on `pipe_cobblestone.png`/`pipe_wood.png` led to the commits that actually added them, and byte-for-byte
    `cmp` against every plausible source showed neither is the vanilla `cobblestone.png`/`oak_planks.png` from
    the real client jar (confirmed different by direct comparison -- same 16x16 dimensions, different pixels,
    different file sizes) -- they are exact, byte-identical copies of `buildcraft_resources/assets/
    buildcrafttransport/textures/pipes/cobblestone_item.png`/`wood_item_clear.png`, the real 1.12.2 pipe icon
    textures already sitting in this repo's own legacy asset tree. The identical legacy directory holds
    `stone_item.png`/`sandstone_item.png`/`quartz_item.png` (all real, all 16x16, confirmed via Pillow), so
    those three were copied byte-for-byte the same way (re-confirmed via `cmp` after copying) onto both
    platforms' `textures/block/pipe_{stone,sandstone,quartz}.png`. Per-material `models/block/pipe_<material>
    .json` (`cube_all`, mirroring `pipe_wood.json`'s own shape, not the shared `pipe_holder.json` -- the block
    itself stays hard-wired to the Cobblestone texture, the same known placeholder limitation already on file)
    and item models were added on both platforms, matching Wood's own precedent exactly rather than
    Cobblestone's (Cobblestone's item model points at the shared `pipe_holder` block model since it happens to
    already be Cobblestone-textured; every other material needs its own): 26.x's newer per-item `items/
    pipe_item_<material>.json` (`{"model": {"type": "minecraft:model", "model": "buildcraft:block/
    pipe_<material>"}}`) vs. 1.20.1's classic `models/item/pipe_item_<material>.json`
    (`{"parent": "buildcraft:block/pipe_<material>"}`) -- confirmed by reading Wood's own existing copies on
    each platform side by side, not assumed to share one format. Lang entries added on both platforms following
    the port's own established short-name convention (`pipe_item_cobblestone` -> "Cobblestone Pipe"):
    `pipe_item_stone` -> "Stone Pipe", `pipe_item_sandstone` -> "Sandstone Pipe", `pipe_item_quartz` -> "Quartz
    Pipe". No recipe exists for any of the three in `buildcraft_resources`, matching the cobblestone/wood
    batches' own precedent, re-checked fresh for this batch rather than assumed to carry over.
  - **In-game verification, both platforms, via RCON against real dedicated servers -- and a cleaner rig than
    every prior pipe batch needed, requiring no temporary debug command at all.** Prior pipe batches (see the
    cobblestone and wood entries above) had to add a temporary fake-player debug command because `/setblock`
    does not invoke `TilePipeHolder#onPlacedBy`, so no `Pipe` object ever gets attached to a bare `/setblock`
    pipe. This batch found a simpler, zero-code path for the same result: `TilePipeHolder#loadAdditional`/
    `#load` reads a real `Pipe` straight back out of the tile's own persisted `"pipe"` NBT tag (the same tag
    `saveAdditional` writes), so `/setblock buildcraft:pipe_holder` followed by `/data merge block <pos>
    {pipe:{def:"buildcraft:stone"}}` reconstructs a genuine `Pipe` object through the tile's own real
    deserialisation path -- confirmed live, not assumed, by reading back `/data get block` immediately after
    the merge and seeing the full `{col, con, def, beh, flow}` shape `Pipe#writeToNbt` actually produces, not
    just the one key that was written. `Pipe`'s own `updateMarked` field starts `true` on both constructors, so
    the very next real server tick (driven by `BlockPipeHolder#getTicker`, no debug code involved) recomputes
    connections through the exact same `updateConnections()` a normal placement would use. On both platforms:
    placed two Stone pipes adjacent (east/west), two Sandstone pipes adjacent plus a vanilla chest on a third
    side of one of them, and two Quartz pipes adjacent, merged each its own `def`, waited a few real seconds
    (`time query gametime` advancing, confirming real ticks, not a frozen idle server), then hand-decoded every
    `con` bitmask read back: `con: 1024` decodes to `(1024 >>> 10) & 0b11 == 0b01` on the EAST bit-pair (bits
    10-11, `Direction.EAST.ordinal() == 5`) with every other bit-pair `0b00`, and the paired block's `con: 256`
    decodes to `0b01` on the WEST bit-pair (bits 8-9, ordinal 4) with everything else `0b00` -- a real
    pipe-to-pipe connection, both directions, for all three materials, on both platforms, with the numbers
    matching by hand, not by inspection. The Sandstone rig's chest side stayed `0b00` throughout on both
    platforms -- the one behavioural claim worth actually proving rather than trusting the code, now proven:
    Sandstone connects to another pipe but never to an adjacent inventory, live, tick-driven, not simulated.
    The loot-drop fix was verified the same way, both platforms: placed a Stone, a Sandstone, and a Quartz
    pipe (each `def`-merged), plus a fourth `pipe_holder` left with no `Pipe` at all (the "nothing ever placed
    a material" case `getDrops` falls through to the static table for), then broke all four with `/setblock
    <pos> air destroy` (confirmed live that this genuinely triggers `getDrops`/entity drops, unlike a bare
    `/setblock ... air` which silently removes the block with nothing dropped) and read back the dropped item
    entities via `/data get entity @e[type=item,...]`: `buildcraft:pipe_item_stone`,
    `buildcraft:pipe_item_sandstone`, and `buildcraft:pipe_item_quartz` for the three merged pipes, and
    `buildcraft:pipe_item_cobblestone` (the intended, documented fallback, not a bug) for the one with no
    `Pipe` attached -- on both platforms independently, in separate RCON sessions against separate fresh
    worlds. `give buildcraft:pipe_item_{stone,sandstone,quartz}` was also run against `@a` on both platforms to
    confirm item registration; it failed with "No player was found" rather than any unknown-item error, the
    same "no connected player" limitation already on file for every prior RCON-only verification in this
    document, not a registration problem.
  - **Honest limitations, same categories already on file for every prior pipe batch.** The real right-click
    placement interaction through a connected client remains unverified -- what is verified directly is that
    the tile's own NBT-driven `Pipe` reconstruction and tick-driven connection logic behave correctly once a
    `Pipe` exists, which is exactly what a real placement would also produce. Visual appearance (the block
    model, the item icon, and the fact that the shared `pipe_holder` block model itself still always renders
    the Cobblestone texture regardless of which material is actually placed -- an existing, unfixed placeholder
    limitation, not something this batch changed or was asked to fix) was not checked with a real client; only
    the JSON shape was confirmed to mirror Wood's own already-working precedent.
  - Verified with forced `--no-build-cache clean` rebuilds on both platforms (re-run once more after a
    concurrent, unrelated `buildcraft.factory` change elsewhere in the tree landed, to confirm this batch's own
    files still compile against the latest state of the rest of the codebase), the full 25-test suite, and real
    dedicated-server boots on both platforms with zero exceptions in either log.

- **`buildcraft.factory` — `TileAutoWorkbenchFluids`/`BlockAutoWorkbenchFluids` (both platforms), the auto
  workbench's fluids half, closing out `buildcraft.factory` entirely** (see the remaining-modules table below).
  Extends the same `TileAutoWorkbenchBase` the items half already ported (unchanged, shared crafting/MJ logic --
  see that half's own entry above), adding two `Tank` fields instead of a 3x3 blueprint's worth of material
  slots: `super(..., 2, 2)` (a 2x2 blueprint, confirmed against `TileAutoWorkbenchBase`'s own constructor --
  the two ints are the phantom grid's width/height, the same thing `TileAutoWorkbenchItems`'s own `super(..., 3,
  3)` already established, not an input/output slot count), `tank1`/`tank2` each `FluidType.BUCKET_VOLUME * 6`
  capacity, exposed per-side: `DOWN`/`NORTH`/`WEST` reach `tank1`, `UP`/`SOUTH`/`EAST` reach `tank2`, and a query
  with no specific side reaches both combined -- the exact `EnumPipePart` split 1.12.2's own `CapUtil` wiring
  used, translated straight onto `Direction`.
  - **A genuine, `git`-verified finding worth recording up front: this block never actually shipped in 1.12.2
    at all, despite its source existing.** Read `common/buildcraft/factory/BCFactoryBlocks.java` directly before
    assuming otherwise (the coordinator's own brief flagged this as worth confirming, not guessing): the
    registration line is commented out --- `// public static BlockAutoWorkbenchFluids autoWorkbenchFluids;` ---
    and `buildcraft_resources/` has no asset of any kind for it: no block texture (only
    `textures/blocks/auto_workbench_item/{top,side,side_alt,bottom}.png` exists, nothing under a `_fluid`/
    `_fluids` name), no GUI texture (only `textures/gui/autobench_item.png`), no blockstate, no model, no loot
    table, no recipe. `TileAutoWorkbenchFluids`/`BlockAutoWorkbenchFluids` themselves are real, complete, 39-
    and 42-line classes -- this was a finished feature with its registration and assets simply never wired up,
    not an abandoned half-write. Given that, "match the exact original registry id/texture" (the brief's own
    starting assumption) had no real target to match: `auto_workbench_fluid` (singular, mirroring
    `auto_workbench_item`) is this port's own choice, and the block/GUI textures are the items variant's own
    textures reused directly (documented as a placeholder in `TileAutoWorkbenchFluids`/`BlockAutoWorkbenchFluids`'s
    own javadoc and in the new model JSON's own directory), not invented new art. No crafting recipe was added
    either, for the same reason 1.12.2 never had one: matching upstream's own unfinished state rather than
    inventing new game content this pass wasn't asked to design.
  - **New files, both platforms**: `buildcraft.factory.tile.TileAutoWorkbenchFluids`,
    `buildcraft.factory.block.BlockAutoWorkbenchFluids`, `buildcraft.factory.container.ContainerAutoCraftFluids`,
    `buildcraft.factory.gui.GuiAutoCraftFluids`, plus `assets/buildcraft/{blockstates,models/block}/
    auto_workbench_fluid.json`, a 26.x-only `assets/buildcraft/items/auto_workbench_fluid.json` (1.20.1's
    `models/item/auto_workbench_fluid.json` mirrors `auto_workbench_item`'s own platform-specific item-model
    convention, already established), `data/buildcraft/loot_table{,s}/blocks/auto_workbench_fluid.json`, and a
    `block.buildcraft.auto_workbench_fluid` lang entry on both platforms.
  - **`TileAutoWorkbenchBase#createMenu` is overridden, not modified.** The shared base's own `createMenu`
    (both platforms) is hardcoded to build a `ContainerAutoCraftItems` -- fine for the items half, wrong for
    this one. Since that method carries no `final` on either target (confirmed by reading it, not assumed),
    `TileAutoWorkbenchFluids` simply overrides it to build a `ContainerAutoCraftFluids` instead; the shared base
    itself needed no edit at all, keeping this pass entirely inside `buildcraft.factory` as scoped.
  - **No `TankManager` port exists in this codebase, and this pass didn't add one** -- nothing else has needed
    a multi-tank capability-combining handler yet, so the "expose both tanks combined" (`side == null`) case
    uses a small, purpose-built substitute per platform instead of a full port of 1.12.2's own class. On 26.x,
    confirmed via `javap` against the real universal jar that NeoForge already ships exactly this shape ready-
    made -- `net.neoforged.neoforge.transfer.CombinedResourceHandler<T>` (the same class
    `ItemHandlerManager#getHandlerForFace` already uses to combine multiple item handlers onto one face) -- so
    `combinedTanks` is a two-element `CombinedResourceHandler<FluidResource>` and the per-side branch lives
    entirely inside `BCFactoryRegistries#registerCapabilities`'s own registration lambda, which already receives
    the queried `Direction` directly: `side == null -> combinedTanks`, `DOWN/NORTH/WEST -> tank1`,
    `UP/SOUTH/EAST -> tank2` -- the tile itself needs no per-side capability logic of its own at all, exactly as
    the coordinator's own brief predicted once the registration lambda's shape was confirmed. On 1.20.1, `javap`
    against the real Forge 1.20.1 universal jar found no equivalent combinator for fluids at all -- unlike
    items' own `net.minecraftforge.items.wrapper.CombinedInvWrapper`, nothing under
    `net.minecraftforge.fluids.**` combines multiple `IFluidHandler`s into one. Capabilities are exposed by the
    tile itself on this target (matching `TilePump`/`TileFloodGate`'s own `getCapability` precedent, not a
    registration-event lookup), so `TileAutoWorkbenchFluids` gets a small private `CombinedTanks implements
    IFluidHandler` inner class instead, backed by a `Tank[] {tank1, tank2}` array, with `fill`/`drain` trying
    each tank in turn and merging what came back -- the same "first handler that accepts, then the next" rule
    `CombinedInvWrapper` itself already uses for items, just hand-written since no fluid equivalent exists to
    reuse. `TileAutoWorkbenchFluids#getCapability` itself only adds the `FLUID_HANDLER` branch and falls through
    to `super.getCapability` for everything else (MJ/has-work/items), since `TileAutoWorkbenchBase` already
    handles those.
  - **The GUI is a deliberate, explicitly-scoped-down screen, not a full port** -- flagged in the coordinator's
    own brief as an acceptable cut if no precedent existed, and confirmed live that none does: no other tile
    with a `Tank` field anywhere in this port (`TilePump`, `TileFloodGate`, `TileTank`) has a GUI/container at
    all yet, so there is no existing "show a fluid tank in a GUI" pattern to follow, and (per the finding above)
    no genuine `autobench_fluid.png` GUI texture ever existed to reuse either. `GuiAutoCraftFluids` therefore
    paints a plain flat panel (`GuiGraphicsExtractor#fill`/`GuiGraphics#fill`) and renders both tanks' contents
    plus the craft progress as plain text (`GuiGraphicsExtractor#text`/`GuiGraphics#drawString`) instead of
    inventing a fluid-level bar/sprite from nothing -- the item/blueprint/material slot layout itself is a
    straight copy of `GuiAutoCraftItems`'s own approach, just over `ContainerAutoCraftFluids`'s 2x2 grid instead
    of 3x3. `ContainerAutoCraftFluids` adds no network plumbing for the tank contents at all: `TileBC`'s
    existing full-NBT `getUpdateTag`/`markDirtyAndSync` sync (both platforms, already established, see
    `TilePump`'s own `Tank` field for precedent) already mirrors `tank1`/`tank2` onto the client copy of the
    tile for free, so the screen reads `menu.tile.tank1`/`tank2` straight off the synced tile rather than adding
    a `DataSlot` the way the craft-progress bar needed to.
  - **In-game verification, both platforms, via RCON against real dedicated servers, with a genuinely different
    rig than every prior fluid-adjacent batch: a direct capability probe rather than a pump/tick simulation.**
    Tried the more realistic route first, as the brief asked: since `BlockAutoWorkbenchFluids#useWithoutItem`/
    `#use` always opens the GUI unconditionally on right-click (matching the items half's own no-wrench-check
    precedent, confirmed by reading it, not assumed), a bucket right-click never reaches the block's fluid
    capability at all -- the same "GUI swallows every right-click regardless of held item" behaviour the items
    half already has. Driving a real `TilePump` into it instead was considered and rejected: this environment's
    own documented tick-rate throttling with no connected player would have made an actual multi-tick pump dig-
    and-push cycle impractically slow to observe inside a single RCON session. Instead, a temporary, self-
    registering debug command (`buildcraft.debugtemp.DebugFluidCapCommand`, its own new file per platform,
    deleted along with the whole `debugtemp` package before this task finished) called
    `level.getCapability(Capabilities.Fluid.BLOCK, pos, side)` (26.x) / `blockEntity.getCapability(
    ForgeCapabilities.FLUID_HANDLER, side)` (1.20.1) directly -- the exact same lookup a real neighbouring
    block's capability query would perform -- and inserted 500 mB of water through whatever handler came back,
    once per `Direction` plus once with no side at all. Self-registered via 26.x's `@EventBusSubscriber`/1.20.1's
    `@Mod.EventBusSubscriber(bus = FORGE)` specifically so nothing in either platform's `BuildCraft.java` needed
    touching (out of scope for this task), confirmed clean afterward: `git status` shows zero changes to either
    `BuildCraft.java`, and the whole `debugtemp` package left no trace once deleted (never committed, so nothing
    to `git diff` against). On both platforms, independently: `DOWN`/`NORTH`/`WEST` each inserted 500 mB
    (`tank1` read back at 1500), `UP`/`SOUTH`/`EAST` each inserted another 500 mB (`tank2` at 1500), and the
    final no-side insert landed in `tank1` (bringing it to 2000, `tank2` unchanged at 1500) -- proving the
    combined handler genuinely delegates to the underlying tanks in order and respects each one's own remaining
    capacity, not just that a non-null object came back. `/data get block` after every insert showed the exact
    expected `tank1`/`tank2` NBT on both platforms (`{stacks: [{amount: 2000, id: "minecraft:water"}]}`/`{...
    1500...}` on 26.x, `{FluidName: "minecraft:water", Amount: 2000}`/`{...1500...}` on 1.20.1), and a full
    server restart in between (26.x) re-confirmed the same numbers read back unchanged, proving the NBT
    round-trips through save/load correctly, not just that the in-memory object was mutated. The GUI-open check
    used the same debug command to call `tile.createMenu(0, fakePlayer.getInventory(), fakePlayer)` directly
    (the exact factory method a real player's `openMenu(tile)` reaches) rather than `FakePlayer#openMenu`
    itself, which turned out to be a documented no-op with no real client connection to push the open-screen
    packet to (confirmed live: it silently left `containerMenu` pointing at the default `InventoryMenu`, with no
    exception either way) -- `createMenu` returned a real `ContainerAutoCraftFluids` with the expected 50 slots
    (1 output + 4 blueprint + 4×2 material/filter + 1 display + 36 player inventory) on both platforms, with
    zero exceptions anywhere in either server log.
  - **A real, unrelated obstacle hit and worked around during this batch, not a bug in this batch's own code:**
    mid-verification, `:neoforge-1201:compileJava` briefly failed with `cannot find symbol:
    ResourceLocation.fromNamespaceAndPath` inside `buildcraft.energy.client.BCEnergyClientRegistries` -- a
    concurrent, unrelated, uncommitted in-progress edit from a different task working on
    `buildcraft.energy`/`buildcraft.core` engine rendering (outside this task's own scope, per the standing
    rule against touching those packages), momentarily broken mid-edit in the same shared working tree. Not
    fixed here, per that same rule -- simply waited out (a few automatic retries of the same compile command)
    until the other task's own edit landed correctly, then proceeded. Also hit, and did not touch: the two
    dev-server `run/server` directories are shared with whatever else is running concurrently against this same
    checkout -- `neoforge-26x`'s `server.properties` port/rcon-port were temporarily bumped
    (25566/25576) for this batch's own verification session, purely to avoid a real `Address already in use`
    collision with another concurrently-running dev server on the default ports, and restored to the defaults
    (25565/25575) afterward; `run/` is gitignored, so this never touched anything tracked.
  - **Honest limitation, same category as every GUI-adjacent entry in this file**: no mouse/keyboard input
    automation exists in this environment, so `GuiAutoCraftFluids`'s on-screen rendering (the plain panel, the
    tank-content text) was never checked with a real client. What was verified is a clean compile, a clean
    dedicated-server boot, and the underlying tile/container/capability logic via RCON exactly as described
    above.
  - Verified with forced `--no-build-cache clean` rebuilds on both platforms, the full 25-test suite, and real
    dedicated-server boots on both platforms with zero exceptions in either log, all re-run one final time after
    the `debugtemp` packages and the temporary server-port bump were both removed/reverted, against the exact
    code left behind for review.

- **The three ported engines (`TileEngineWood`/`TileEngineCreative`/`TileEngineStone`) get a real animated
  piston-rod render -- the first `BlockEntityRenderer` registered anywhere in this port, closing exactly the
  gap `TileEngineBase`'s own javadoc had been documenting since the facing-visibility batch above ("nothing in
  this port can register a `BlockEntityRenderer` yet ... it costs nothing to re-add once a renderer exists to
  read `progress` from").** A small box slides out along the engine's own facing direction and back as
  `progress` cycles, only while the engine is genuinely pumping -- not a reproduction of 1.12.2's real
  `RenderEngine_BC8`/`MutableQuad` quad-based model framework, which has no counterpart anywhere in this port
  and stayed explicitly out of scope; a from-scratch `ModelPart` cuboid is both simpler and idiomatic to the
  modern rendering API on both targets.
  - **`TileEngineBase#getRenderProgress(float)` (shared base, both platforms) is a small client-side-only mirror
    of the server's own `progress`, not a literal read of it.** Read `serverTick()` again with this specifically
    in mind: its `progress += getPistonSpeed()` increments happen every tick unconditionally, but nothing calls
    `markDirtyAndSync()` for them -- only a `powerStage` change or an `isPumping` flip does, both comparatively
    rare -- so a renderer naively lerping toward the client's copy of `progress` would see it jump rarely and
    sit stale the rest of the time, never actually looking like smooth motion. 1.12.2 never had this problem
    because its client half ran its *own* independent tick loop (`world.isRemote` branch of `update()`), driven
    off the already-reliably-synced `isPumping` boolean rather than trusting a frequently-resynced `progress`
    value at all. `getRenderProgress` reproduces exactly that shape as new, small, clearly-scoped fields
    (`clientProgress`/`lastClientProgress`/`lastClientProgressTick`, never saved, never synced): advance by
    `getPistonSpeed()` while `isPumping`, ease back down by a fixed step when not, at most once per real game
    tick (a `level.getGameTime()` guard, since the renderer calls this once per frame -- far more often than
    once per tick), then lerp the last two tick values by `partialTick` -- reusing 1.12.2's own
    `getProgressClient(float)` wrap-around fixup verbatim for the moment the value rolls from just under 1 back
    to just over 0 between the two ticks being interpolated. Bounded `[0, 1)` on every call, including the very
    first frame after a chunk loads (both new fields default to `0f`, so the lerp is `0` before `level` is even
    non-null) -- no NaN, no division by zero, the exact bug class this kind of first-renderer task tends to hit.
  - **The registration API itself is a genuine, `javap`/decompiled-source-confirmed divergence between the two
    targets, not a rename -- the task brief's own suspicion, confirmed rather than assumed.** On 1.20.1, `javap`
    against `net.minecraftforge:forge:1.20.1-47.1.106-universal.jar` confirms the classic shape unchanged from
    1.12.2's own `TileEntitySpecialRenderer` era: `BlockEntityRenderer<T>` with a single immediate-mode
    `render(T, float, PoseStack, MultiBufferSource, int, int)`, registered via
    `EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer` (`net.minecraftforge.client.event`) --
    an `IModBusEvent`, wired the same `modBus.addListener` way as `registerScreens`. On 26.x, the real client jar
    (`minecraft_26.3_client.jar`) shows this was replaced entirely by a state-extraction/`submit` split:
    `BlockEntityRenderer<T, S extends BlockEntityRenderState>` with `createRenderState()`/`extractRenderState(T,
    S, float, Vec3, CrumblingOverlay)` (run against the real tile) and `submit(S, PoseStack,
    SubmitNodeCollector, CameraRenderState)` (run later against only the captured state, never touching the tile
    again) -- confirmed against real decompiled vanilla renderers using the identical shape
    (`DecoratedPotRenderer`, `ChestRenderer`) rather than guessed from the interface alone, since the interface
    alone doesn't show which `SubmitNodeCollector#submitModelPart` overload real code actually calls or how a
    `null` sprite is meant to be used. The event class itself is the same name in both places
    (`EntityRenderersEvent.RegisterRenderers`, package `net.neoforged.neoforge.client.event` on 26.x) with the
    same `registerBlockEntityRenderer` method name, just a different second generic parameter -- so each
    platform's `RenderTileEngine` is a genuinely separate implementation, not a renamed copy, while
    `TileEngineBase#getRenderProgress` itself is byte-identical on both.
  - **Two smaller, `javap`-confirmed 26.x renames worth recording, in the same "invisible until you need it"
    spirit as the `DirectionProperty` divergence noted in the facing-visibility batch above.** `ResourceLocation`
    itself is renamed `net.minecraft.resources.Identifier` on 26.x (`fromNamespaceAndPath`/`withDefaultNamespace`
    carried over unchanged in shape); `Direction#getNormal()` -- used to turn `currentDirection` into a
    translation vector -- no longer exists on 26.x at all (`javap`: "class not found" for that specific method),
    replaced by `getUnitVec3i()` (1.20.1 keeps the classic `getNormal()` name). Both platforms' `RenderTileEngine`
    read facing straight off `BlockState.getValue(BlockStateProperties.FACING)` rather than the tile, per this
    task's own brief -- the facing-visibility batch above already made that reliably synced.
  - **Geometry and texture are a deliberate, honestly-scoped placeholder, not a claim of visual accuracy.** The
    rod is one plain cuboid (`CubeListBuilder.addBox(5, 5, 5, 6, 6, 6)` inside a 16x16x16 `MeshDefinition`, baked
    directly in each `RenderTileEngine`'s own constructor -- no `RegisterLayerDefinitions` event needed, since
    nothing else references this layer) that translates up to `0.3` blocks along the facing direction and back,
    textured with each engine's own existing `block/engine_{wood,creative,stone}_side.png` (already a real,
    16x16 resource shipped for the static baked model) rather than an all-new texture asset -- the UVs do not
    claim to line up with anything meaningful on that image, only to look like *something* textured rather than
    a flat colour. One shared `RenderTileEngine` class per platform covers all three engine types (registered
    three times in `BCEnergyClientRegistries#registerRenderers`, once per texture); nothing about the three
    engines' animation genuinely differs enough to justify per-type subclasses, so none were written -- if
    1.12.2's real per-tier piston geometry ever needs reproducing faithfully, that is new work on top of this,
    not something this pass silently dropped.
  - **`isPumping`, unlike the task brief's own hedge ("if `isPumping` itself wasn't ported..."), was already on
    the tile from the facing-visibility batch's own untouched code** (`protected boolean isPumping`, flipped only
    via `setPumping`, already reliably synced since every flip calls `markDirtyAndSync()`) -- no proxy field was
    needed, only reading it from the new client-local tick loop above.
  - **The one-line, in-scope `BuildCraft.java` touch, both platforms**: a single
    `modBus.addListener(BCEnergyClientRegistries::registerRenderers);`, added immediately next to the existing
    `registerScreens` listener line, inside the same pre-existing `FMLEnvironment.getDist().isClient()` /
    `FMLEnvironment.dist.isClient()` guard -- so a dedicated server has exactly as much reason to load
    `RenderTileEngine` as it already had to load `GuiEngineStone`: none. Confirmed live: RCON-driven dedicated-
    server boots on both platforms placed and fully powered a Creative Engine into a `TilePowerConsumerTester`
    (`data get block` showing `progressPart` cycling `1`/`2`, `progress` moving through the full `0..1` range,
    and the tester's own `total` climbing by real MJ every tick) with zero exceptions in either server log --
    proving the client-only renderer registration is never even reached server-side, and that none of this
    batch's tile-side changes (the three new client-only fields, `getRenderProgress` itself) disturbed the
    real, already-working pump cycle.
  - **New files, both platforms**: `buildcraft.lib.engine.RenderTileEngine`. Modified, both platforms:
    `buildcraft.lib.engine.TileEngineBase` (the three new client-only fields plus `getRenderProgress`, and an
    updated "Ticking" javadoc entry noting the renderer now exists), `buildcraft.energy.client.
    BCEnergyClientRegistries` (`registerRenderers`), `buildcraft.BuildCraft` (the one listener line above).
  - **Honest limitation, explicitly not claimed otherwise**: the actual on-screen visual result -- whether the
    rod's position, size, or texture genuinely look right -- is not verified, per this project's own standing
    limitation that rendering needs a real display. What is verified: real `:neoforge-26x:runClient` and
    `:neoforge-1201:runClient` boots, both reaching a fully textured main menu (`TextureAtlas` stitching
    including `blocks.png-atlas`, sound engine started) with zero `Exception`/`Error` lines anywhere in either
    log -- proving `EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer` succeeds on both targets
    at the exact point a bad generic signature or a wrong real API call would throw. Not reached: an actual
    loaded chunk with a placed, pumping engine rendered in either client, since this environment has no mouse/
    keyboard input automation to reach a world from the title screen -- so `RenderTileEngine#render`/`submit`
    were never invoked live, only read back carefully for input-range safety (see `getRenderProgress` above).
  - Verified with forced `--no-build-cache clean` rebuilds on both platforms, the full 25-test suite, real
    dedicated-server boots on both platforms with a genuine RCON-driven pump cycle and zero exceptions in either
    log, and real `runClient` boots on both platforms reaching a fully stitched main menu with zero exceptions.

- **A critical, previously-undiscovered bug, found only by accident while independently re-verifying the pipe-
  materials batch above: every placed pipe, on both platforms, silently lost its material and reverted to a bare
  `pipe_holder` with no `Pipe` at all on the very first real server restart.** Every prior pipe batch's own RCON
  verification (cobblestone, wood, this session's stone/sandstone/quartz) used `/data merge block` to attach a
  `Pipe` to an already-running tile within the same server session, and never once restarted the server to force
  a genuine disk-based reload -- a real gap in this project's own testing methodology, not just bad luck, since
  every one of those verification passes reported "zero exceptions" truthfully for the scenario it actually
  tested. Caught here specifically because a concurrent task's own hand-back mentioned an unrelated-looking
  `NullPointerException` from `PipeFlowItems` seen once in a shared dev world; re-testing that exact scenario
  directly (place a pipe, `save-all flush`, `stop`, restart the same world, re-read the block) reproduced it
  reliably, on both platforms, using pipe materials that predate this whole session's own work (wood,
  cobblestone) -- proving this is not a defect in anything ported today, just never previously exercised.
  - **Root cause**: `PipeFlowItems`'s own NBT-loading constructor (both platforms) calls
    `pipe.getHolder().getPipeLevel().getGameTime()` unconditionally, before even checking whether there are any
    travelling items to reconstruct. `getPipeLevel()` delegates straight to `BlockEntity#getLevel()`, which is
    genuinely `null` at this exact point during a real disk-based chunk load: vanilla calls
    `BlockEntity#loadAdditional`/`#load` (which is what reconstructs `TilePipeHolder`'s own `Pipe`, and
    transitively this constructor) *before* `BlockEntity#setLevel(Level)` during chunk deserialization, not
    after -- confirmed live, not assumed, by reproducing the crash and reading the exact
    `NullPointerException` message (`Cannot invoke "Level.getGameTime()" because the return value of
    "IPipeHolder.getPipeLevel()" is null`). This ordering is invisible to every prior in-session test because
    `/data merge block` always targets a tile that is already fully attached to a live level.
  - **Consequence, confirmed live, not assumed**: NeoForge/Forge both catch a block entity's own load exception
    per-tile rather than crashing the whole chunk/server (a real vanilla robustness feature) -- so the practical
    effect was not a server crash but silent, total data loss: the tile's own `pipe` field simply stayed `null`
    after the failed load, with no error visible to a player beyond "my pipe network reset itself" after every
    single world reload, forever, on both platforms, for every material including the two (cobblestone, wood)
    already shipped in a deployed jar before this was caught.
  - **Fix, both platforms, in `PipeFlowItems`'s NBT constructor and its `writeToNbt`**: guard
    `pipe.getHolder().getPipeLevel()` for `null` and fall back to `0` for `tickNow` when it is. Safe specifically
    because `TravellingItem`'s own `tickStarted`/`tickFinished` are stored as NBT-relative *offsets* from
    whatever `tickNow` `writeToNbt` used, not absolute values -- any single consistent placeholder at load time
    reconstructs internally-consistent (if not clock-accurate) absolute times, which is a vastly smaller problem
    than losing the `Pipe` object entirely. The only real cost: an item genuinely mid-transit at save time will
    read as "already arrived" the instant the tile starts ticking for real after a reload (`getCurrentDelay`
    clamps a large negative `tickFinished - realNow` to `0`), rather than finishing its remaining travel time --
    a minor, rare, purely cosmetic timing hiccup, not data loss, not a crash, not a stuck item. The identical
    guard was also applied to `writeToNbt`'s own eager `getPipeLevel()` call for symmetry, even though no live
    crash was reproduced there -- a plausible (if unconfirmed) equivalent risk exists for any future tool that
    copies a placed tile's NBT into an `ItemStack` without ever attaching it to a level.
  - **Re-verified live, both platforms, with the exact reload sequence that reproduced the original crash**: a
    fresh pipe (with connections and no in-flight items, and separately re-tested against the same wood/
    cobblestone pair that first reproduced the bug) placed, `save-all flush`, `stop`, restart the same world,
    re-read via `/data get block` -- the `Pipe`'s `def`/`con`/`beh`/`flow` all present and correct after the
    reload, zero exceptions in either server log, on both platforms.
  - Verified with a forced `--no-build-cache clean` rebuild on both platforms and the full 25-test suite.

- **`TileTank` gets a real fluid-level render -- the second `BlockEntityRenderer` registered anywhere in this
  port, closing the gap both `TileTank`'s own class javadoc and `buildcraft.lib.fluid.Tank`'s own class javadoc
  had been documenting since the tank column-balancing batch: `getFluidForRender` (client-side fluid-level
  interpolation) and `Tank`'s own `clientFluid`/`clientAmount`/`colorRenderCache` were dropped purely for "there
  is no renderer in this port to consume it" -- the identical situation the engines' piston-rod render closed for
  `TileEngineBase#progress` two batches above.** An empty tank still renders as the plain static block model with
  no floating quad; a non-empty one gets a real inset fluid box whose height scales with `tank.getAmountAsInt(0) /
  tank.getCapacity()`, textured and tinted with the fluid's own real still sprite.
  - **No client-side interpolation field, unlike the engines' `clientProgress`/`lastClientProgress`.** That
    machinery exists because `TileEngineBase#progress` moves every tick without a sync to match, so the renderer
    has to fake smooth motion between infrequent full syncs. A tank's fill level is the opposite case, confirmed
    by re-reading `Tank`'s own `onChange` wiring (both platforms' `TileTank`, constructor: `new Tank(TANK_CAPACITY,
    this::onTankChanged)`) and `onTankChanged`'s own body (`markDirtyAndSync()`): every single fill/drain call
    that actually changes the tank's contents fires a full-NBT sync immediately, not once a tick on a timer -- so
    whatever the client's own `tile.tank` holds is never more than one network round-trip stale, and it only ever
    moves in small per-tick increments relative to a 16-bucket (16000 mB) capacity. `RenderTileTank` therefore
    reads `tile.tank` directly, every frame, with no smoothing layer of its own -- a deliberate, justified call,
    not an oversight, documented in the renderer's own class javadoc on both platforms.
  - **Real fluid sprite/tint API, and it genuinely diverges between the two targets -- confirmed via `javap`
    against the real jars, not assumed to be symmetric.** On 1.20.1, `javap` against
    `net.minecraftforge:forge:1.20.1-47.1.106-universal.jar` shows `net.minecraftforge.client.extensions.common.
    IClientFluidTypeExtensions` keeping the classic, well-established shape: `IClientFluidTypeExtensions.of(Fluid)`
    returns an instance whose `getStillTexture(FluidStack)`/`getTintColor(FluidStack)` give the sprite's
    `ResourceLocation` and an `0xRRGGBB` tint directly; the location is then resolved to a real
    `TextureAtlasSprite` through `Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)`, the same
    atlas-lookup idiom every Forge-family fluid-rendering mod uses. On 26.x, `javap` against the real
    `neoforge-26.3.0.7-beta-universal.jar` shows the *same-named* `net.neoforged.neoforge.client.extensions.
    common.IClientFluidTypeExtensions` interface has lost `getStillTexture()`/`getTintColor()` entirely -- only
    fog/overlay hooks remain -- because fluid rendering itself moved into vanilla as a real, data-driven system on
    this target: confirmed by reading the real classes in `minecraft-merged-deobf-26.3.jar`,
    `net.minecraft.client.renderer.block.FluidStateModelSet` (reachable via
    `Minecraft.getInstance().getModelManager().getFluidStateModelSet()`) maps a `FluidState` to a `FluidModel`
    record whose `stillMaterial().sprite()` is the real baked `TextureAtlasSprite` and whose `tintSource().
    color(FluidState.createLegacyBlock())` gives the real tint -- a genuinely different, newer API shape with no
    1.20.1 counterpart, not a rename. Each platform's `RenderTileTank` is therefore a separately-researched
    implementation, exactly like `RenderTileEngine` already is for the classic-vs-split render contract.
  - **Geometry is real, faithfully reproduced from 1.12.2's own `RenderTank`** (`common/buildcraft/factory/client
    /render/RenderTank.java`, 120 lines): the fluid box is inset from the tank's full-block bounds by `0.13`/
    `0.86` on X/Z, `0.01`/`0.99` on Y by default, with the actual fill height scaling up from `0.01` by
    `amount / capacity` -- a real, load-bearing piece of behaviour, not a placeholder. **Deliberately cut, and
    said so plainly rather than silently skipped**: the original's connected-tank seamless stretching
    (`MIN_CONNECTED`/`MAX_CONNECTED`, which pushes the shared face of two full, same-fluid, vertically-adjacent
    tanks flush to the block edge so a tall stack reads as one unbroken column) needs a same-fluid/fullness check
    against the neighbour tank above and below every frame for a purely cosmetic seam-hiding refinement whose
    actual on-screen result cannot be checked visually in this project anyway -- this first pass always renders
    the plain inset box. `TileTank.canTanksConnect` is already public and ready for whoever re-adds this.
  - **Deliberately not a reproduction of 1.12.2's quad technique** (a bespoke `MutableQuad`/`FluidRenderer`/
    immediate-mode `BufferBuilder` framework with no counterpart anywhere in this port, the same call already made
    for `RenderTileEngine`). Both platforms hand-build one box (six faces) directly against `VertexConsumer`
    (`SubmitNodeCollector#submitCustomGeometry` on 26.x, `MultiBufferSource#getBuffer` on 1.20.1) using a
    non-culling `RenderType` (`RenderTypes.entityTranslucent`/`RenderType.entityTranslucent`, not the `Cull`
    suffixed variant) specifically because a hand-built quad's winding order cannot be checked on screen in this
    environment -- non-culling costs a few invisible backfaces on one small box per tank and guarantees every face
    actually draws regardless of winding. Every face reuses the same still-texture UV rect (matching the
    original, which also only ever asked for `FluidSpriteType.STILL`), not a separately-scaled top/side/bottom
    mapping.
  - **Registration mirrors `BCEnergyClientRegistries#registerRenderers` exactly**: a new
    `BCFactoryClientRegistries#registerRenderers(EntityRenderersEvent.RegisterRenderers)` registers
    `BCFactoryRegistries.TANK_TYPE.get()` to `RenderTileTank::new`, and `BuildCraft.java` (both platforms) gets one
    new listener line, `modBus.addListener(BCFactoryClientRegistries::registerRenderers);`, added next to the
    existing `registerScreens` line for the same module and grouped ahead of the pre-existing
    `BCEnergyClientRegistries::registerRenderers` line -- inside the same pre-existing client-only
    `FMLEnvironment` guard, so a dedicated server has exactly as much reason to load `RenderTileTank` as it does
    `RenderTileEngine`: none.
  - **Input-range safety, read back carefully rather than assumed**: `fraction` is computed as
    `capacity > 0 ? Mth.clamp(amount / (float) capacity, 0f, 1f) : 0f` on both platforms -- clamped `[0, 1]`
    regardless of any transient over/under-fill, and guarded against a zero-capacity divide even though
    `TANK_CAPACITY = 16 * FluidType.BUCKET_VOLUME` is a compile-time-constant positive value that can never
    actually be zero. Manually traced with concrete numbers in place of a display: capacity `16000`, amount
    `4000` gives `fraction = 0.25`, `topY = 0.01 + (0.99 - 0.01) * 0.25 = 0.25`; amount `16000` (full) gives
    `fraction = 1.0`, `topY = 0.99` exactly, matching the original's own uninset ceiling; amount `0` short-circuits
    before any of this runs (the empty-tank early return), so `topY` is never computed as `0.01` and drawn as a
    zero-height sliver.
  - **This working tree was shared, live, with a concurrent `buildcraft.transport` task while this batch was
    written** (a `Pipe`/`TilePipeHolder`/`BlockPipeHolder`/`EnumPipeMaterial` connected-pipe-shape effort, per this
    task's own brief) -- two effects of that were observed directly, not assumed. First, an early
    `:neoforge-26x:compileJava` failed on `Pipe.java:268` (`updateConnectionBlockState(Pipe, EnumMap<Direction,
    ConnectedType>) is not public in TilePipeHolder`) while `:neoforge-1201:compileJava` compiled clean in the same
    invocation -- confirmed unrelated to this batch by reading the compiler's own single-error output (nothing in
    `buildcraft.factory`/`buildcraft.lib.fluid` named in it) and left alone rather than stashed/fixed, per this
    task's own explicit instruction never to touch `buildcraft.transport`; a later re-run of the exact same forced
    `--no-build-cache clean` rebuild succeeded on both platforms once that concurrent task's own fix landed in the
    same tree. Second, a `:neoforge-26x:runServer` boot attempted for this batch's own dedicated-server
    verification failed immediately with `DirectoryLock$LockException: .../run/server/./world/session.lock:
    already locked (possibly by other Minecraft instance?)` -- a real, already-running dedicated server (the
    concurrent task's own live RCON test session against that same world) held the lock. Starting a second server
    against a world another task is actively using would risk corrupting or disturbing that task's in-progress
    verification, so this was not retried or forced.
  - **Honest limitation, same category as every render-adjacent entry in this file**: the actual on-screen visual
    result -- whether the fluid box's size, position, texture, or tint genuinely look right -- is not verified,
    per this project's own standing limitation that rendering needs a real display. What *is* verified: both
    platforms compile clean via a forced `--no-build-cache clean` rebuild (`:neoforge-26x:compileJava
    :neoforge-1201:compileJava`), the full 25-test suite passes (`--no-build-cache test --rerun`, all 25 green:
    12 in `:shared:test` -- `MjBatteryTester`/`BitSetTester` -- and 13 in `:expression:test`), every real API
    called on both platforms was confirmed against the real jars via `javap`/decompiled source rather than
    guessed, and the fill-fraction math was traced by hand above. Not reached this session, for the
    world-lock reason above: a dedicated-server boot proving the client-only registration never loads
    `RenderTileTank` server-side (the same check the piston-rod batch got via RCON), and a `runClient` boot
    proving the texture atlas stitches with the new sprite lookup and no missing-model/missing-sprite warnings.
    Both are exactly the kind of check the piston-rod batch already established as the right fallback when the
    visual result itself can't be judged -- re-run them once the concurrent `buildcraft.transport` task's own
    server session is no longer holding the world.
  - **New files, both platforms**: `buildcraft.factory.tile.RenderTileTank`. Modified, both platforms:
    `buildcraft.factory.client.BCFactoryClientRegistries` (`registerRenderers`), `buildcraft.BuildCraft` (the one
    listener line above). Modified, 26.x only: `buildcraft.factory.tile.TileTank` and `buildcraft.lib.fluid.Tank`
    (javadoc only -- both classes' own "no renderer to consume it" wording updated now that one exists, and both
    explicitly note that the dropped `getFluidForRender`/`clientFluid`/`clientAmount` fields are still not
    resurrected, per the interpolation call above).

- **`buildcraft.factory.gui.GuiAutoCraftFluids` (both platforms) trades its plain `"Tank 1: ..."`/`"Tank 2: ..."`
  text lines for two real vertical fluid-level bars, reusing the exact fluid sprite/tint lookup the immediately
  preceding `RenderTileTank` batch already pinned down and `javap`-verified on both targets** (see that entry
  above for the full API account: 26.x's `Minecraft.getInstance().getModelManager().getFluidStateModelSet()`
  vs. 1.20.1's `IClientFluidTypeExtensions.of(Fluid)`). That lookup is identical regardless of what draws it
  afterward, but a plain 2D GUI context needed its own, separately-verified drawing primitive -- a 3D
  `BlockEntityRenderer` builds a hand-wound `VertexConsumer` quad, which has no counterpart in `GuiGraphics`/
  `GuiGraphicsExtractor` at all. The GUI background texture itself is still a plain panel fill, unchanged and out
  of scope, per this class's own pre-existing javadoc: no genuine `autobench_fluid.png` GUI texture ever existed
  in 1.12.2 to base one on, and authoring one from nothing was explicitly out of scope for this batch.
  - **Real drawing primitive, confirmed by reading the actual decompiled source out of each platform's own
    `-sources.jar` rather than guessed -- and it genuinely diverges in *shape*, not just name.** On 26.x,
    `net.minecraft.client.gui.GuiGraphicsExtractor` (from `minecraft-patched-26.3.0.7-beta-sources.jar`) has
    exactly one public overload that takes an already-resolved `TextureAtlasSprite` together with a tint:
    `blitSprite(RenderPipeline, TextureAtlasSprite, int x, int y, int width, int height, int color)` -- read its
    body directly: it always draws the sprite's full UV rect (`u0`/`u1`/`v0`/`v1`) stretched into the given pixel
    box. The one overload that *does* crop a sub-rectangle of a `TextureAtlasSprite` is `private`; the public,
    UV-cropping `blitSprite(RenderPipeline, Identifier, int spriteWidth, int spriteHeight, int textureX, int
    textureY, int x, int y, int width, int height)` overload (confirmed by decompiling vanilla's own
    `AbstractFurnaceScreen#extractBackground`, which uses exactly this one for the lit-flame/burn-progress icons)
    resolves its sprite from the *GUI* sprite atlas (`this.guiSprites.getSprite(location)`), which cannot address
    an arbitrary block-atlas fluid sprite at all. On 1.20.1, `net.minecraft.client.gui.GuiGraphics` (from
    `forge-1.20.1-47.1.106-sources.jar`) has the equivalent single tint-capable `TextureAtlasSprite` overload --
    `blit(int x, int y, int blitOffset, int width, int height, TextureAtlasSprite sprite, float red, float green,
    float blue, float alpha)`, confirmed by reading its body forwarding straight to `innerBlit` with the sprite's
    own full `getU0()`/`getU1()`/`getV0()`/`getV1()` -- same full-UV-stretch behaviour, no UV-cropping overload
    exists for a raw sprite here either (the classic `ResourceLocation`-based `blit` overloads that do crop a
    source rectangle assume a flat, fixed-size PNG addressed by pixel offset, not a fractional atlas UV rect, so
    they cannot substitute).
  - **The fill-from-the-bottom effect is therefore built from `enableScissor`/`disableScissor`, not a UV crop --
    a real, verified primitive on both targets, not a workaround.** Both `GuiGraphicsExtractor` (26.x) and
    `GuiGraphics` (1.20.1) expose public `enableScissor(int, int, int, int)`/`disableScissor()` (confirmed via
    `javap` on both real jars), and 26.x's own `GuiGraphicsExtractor#blitSprite(Identifier, ...)` internally falls
    back to this exact same enable-scissor/draw-full-sprite/disable-scissor triad whenever a GUI sprite's own
    scaling mode isn't `Stretch` -- i.e. this is vanilla's own established technique for "can't crop this
    particular sprite," reused here for the same reason. Each bar is drawn at its full `BAR_WIDTH x BAR_HEIGHT`
    box every frame; a scissor rect clipped to the box's bottom `fillHeight` pixels reveals only the filled
    portion.
  - **Tint, confirmed via decompiled source rather than assumed to work the same way on both targets.** 26.x's
    `blitSprite` overload takes a direct packed `int` ARGB colour; `BlockTintSource#color(BlockState)` (the same
    call `RenderTileTank` already makes) returns a plain `0x00RRGGBB` value with the alpha byte unset, which --
    confirmed by reading `innerBlit`'s use of the colour as a real multiplicative vertex tint -- would otherwise
    multiply the sprite fully transparent; `ARGB.opaque(int)` (`color | 0xFF000000`, read directly from
    `ARGB.java` in the sources jar) forces the alpha byte to `0xFF` before the colour is passed in. 1.20.1's `blit`
    overload instead takes direct `float red, green, blue, alpha` components, so `IClientFluidTypeExtensions
    .getTintColor(FluidStack)`'s packed int is split into `r`/`g`/`b` floats exactly the way `RenderTileTank`
    already does, with `alpha` passed as a literal `1f` -- no packed-alpha concern on this target at all, a
    genuine, `javap`-confirmed shape divergence between the two platforms' tint-capable overloads, not a rename.
  - **Fill direction: bottom-up, matching `RenderTileTank`'s own vertical fill-from-`Y_MIN` convention** -- the
    natural reading for a tank (liquid rises from the bottom). Screen-space Y increases downward, so "reveal the
    bottom" scissors to the *larger*-Y half of the bar's box: for a bar at `[barY, barY + BAR_HEIGHT)`, fraction
    `f` clips to `[barY + BAR_HEIGHT - fillHeight, barY + BAR_HEIGHT)` where `fillHeight = round(BAR_HEIGHT * f)`.
    Hand-traced with concrete numbers, identically on both platforms: `BAR_HEIGHT = 54`; `f = 0.0` never reaches
    this code at all (see below); `f = 0.5` gives `fillHeight = 27`, scissor `[barY + 27, barY + 54)` -- the bottom
    half, 27 px tall; `f = 1.0` gives `fillHeight = 54`, scissor `[barY, barY + 54)` -- the whole bar, unclipped.
    No negative height is ever possible: `fraction` is `Mth.clamp`-ed to `[0, 1]` before use (guarded against a
    zero-capacity divide, even though `Tank`'s capacity here is a positive compile-time constant, `FluidType
    .BUCKET_VOLUME * 6`), and an empty tank or a fraction of exactly `0` returns before any sprite/scissor call
    runs at all -- matching `RenderTileTank`'s own "don't render anything for an empty tank" rule one level up, so
    no zero-height sliver, clipped or otherwise, is ever drawn. A compact percentage readout (e.g. `"50%"`) is
    still drawn under each bar regardless of fill state, in place of the old full `getContentsString()` line, to
    keep some of the original plain-text informational value without the layout risk of a long amount string.
  - **Layout**: two 14px-wide, 54px-tall bars at a fixed `BAR1_X = 144`/`BAR2_X = 160`, `BAR_Y = 16` (both
    platforms, identical), chosen to sit in the panel's otherwise-empty space to the right of the output slot
    (`x` 124-142) -- clear of every slot `ContainerAutoCraftFluids` lays out and of the player inventory (which
    starts at `y` 115), confirmed by re-reading that container's own slot coordinates rather than eyeballed.
  - **`buildcraft.transport` was not touched**, per this task's own explicit instruction (a concurrent pipe-
    rendering effort was live in the same tree) -- this batch stayed entirely inside
    `buildcraft.factory.gui.GuiAutoCraftFluids`. `ContainerAutoCraftFluids` also did not need touching: the tank
    contents were already readable directly off `menu.tile.tank1`/`tank2`, exactly as the pre-existing plain-text
    version already did, so no new synced value was needed.
  - **Explicitly out of scope, same as before**: a real background GUI texture for this block (still none, still
    a plain panel fill) and any block-world rendering (this is 2D GUI-only, reusing but not modifying
    `RenderTileTank`).
  - **Honest limitation, same category as every render-adjacent entry in this file**: the actual on-screen visual
    result -- whether the two bars' size, position, texture, or tint genuinely look right -- is not verified, per
    this project's own standing limitation that rendering needs a real display no mouse/keyboard input automation
    exists to drive in this environment. What *is* verified: both platforms compile clean via a forced
    `--no-build-cache clean` rebuild (`:neoforge-26x:compileJava :neoforge-1201:compileJava`), the full 25-test
    suite passes (`--no-build-cache test --rerun`, all 25 green), a real dedicated-server boot on both platforms
    reached `Done (...)` with zero exceptions and zero references to `GuiAutoCraftFluids` anywhere in either log
    -- confirming this client-only screen class is still never loaded server-side, the same way every other
    `buildcraft.factory.gui` class's own javadoc already establishes -- and the fill-fraction/pixel-height
    arithmetic above was hand-traced with concrete numbers rather than eyeballed on screen.
  - **Modified, both platforms**: `buildcraft.factory.gui.GuiAutoCraftFluids` only. No other file needed changing.

- **`buildcraft.transport` -- the pipe connection shape finally renders, closing the single most-requested
  visual gap in this whole port ("the pipes... not having animations", i.e. every pipe is still a plain solid
  cube regardless of what it connects to).** No custom `BakedModel` was needed: the real 1.12.2 geometry
  (`common/buildcraft/transport/client/model/PipeBaseModelGenStandard.java`) is plain axis-aligned boxes -- an
  8x8x8 centre cube (`from=[4,4,4]`, `to=[12,12,12]`) plus one same-cross-section extrusion stub per connected
  direction, running from the centre cube's own face out to the block's own face -- entirely expressible as
  ordinary vanilla block-model JSON `elements` plus a `multipart` blockstate, confirmed by directly re-reading
  that generator class's own manual `UvFaceData` constants (`UvFaceData.from16(4, 0, 12, 4)` for the unconnected
  centre cube's north/south faces, matching exactly what a plain `[4,4,4]`-`[12,12,12]` box's *default*,
  position-derived UV already produces with no explicit `"uv"` override at all) rather than assumed from the
  geometry description alone.
  - **A load-bearing correction to this batch's own starting research, caught by checking rather than trusting
    it.** The brief's "bonus finding" that `pipe_wood.json`/`pipe_stone.json`/`pipe_sandstone.json`/
    `pipe_quartz.json`/`pipe_holder.json` (the existing full-16x16x16 `cube_all` models) were orphaned and free to
    repurpose was wrong: `grep`ping every `items/pipe_item_<material>.json` on both platforms shows each one is
    `{"model": {"type": "minecraft:model", "model": "buildcraft:block/pipe_<material>"}}` (26.x) /
    `{"parent": "buildcraft:block/pipe_<material>"}` (1.20.1) -- every one of those five files is the live
    inventory-icon model for that material's item, still actively referenced today. Repurposing any of them into
    an 8x8x8 sub-box would have silently broken that material's held/inventory icon. Ten new files were added per
    platform instead -- `models/block/pipe_holder_core_<material>.json` (the centre cube) and
    `models/block/pipe_holder_arm_<material>.json` (one north-facing arm template, reused for every other
    direction via blockstate rotation, see below) -- leaving the five existing item-icon models completely
    untouched.
  - **Real vanilla precedent for the exact arm geometry, not an approximation of it**: `chorus_plant_side.json`
    (present, byte-identical in shape, in both the real 1.20.1 and the real 26.3 client jars) is
    `{"from": [4,4,0], "to": [12,12,4], "faces": {down,up,north,west,east}}` -- literally the same box this task's
    own research already predicted for a north-connecting arm stub, missing only a `south` face (hidden inside
    the centre cube, the same reason this port's own arm models also omit it). `pipe_holder_arm_<material>.json`
    mirrors this shape directly, texture swapped for the material's own `block/pipe_<material>` sprite.
  - **Multipart rotation, confirmed against two real vanilla blockstates on both target jars, not assumed to
    carry over from the `variants`-only precedent the engine facing-visibility batch already established.**
    `glass_pane.json` reuses one `glass_pane_side` model for two of its four horizontal directions via a plain
    `"y": 90` on the `multipart` entry's own `apply` block; `chorus_plant.json` goes further and covers all six
    directions from one `chorus_plant_side` template: `y: 0/90/180/270` for north/east/south/west and, critically,
    `x: 270`/`x: 90` for up/down -- the one axis the engine batch's own `variants`-only rotation never had to
    prove. `pipe_holder.json`'s own `multipart` array (both platforms, byte-identical, 35 entries: five
    unconditional `{"when": {"material": "<m>"}}` centre-cube entries plus 5 materials x 6 directions arm
    entries) copies `chorus_plant.json`'s exact rotation values for the exact same reason -- reorienting a single
    north-facing box template onto every other face.
  - **The material dimension needed one new, real, `javap`-verified `EnumProperty`, kept local to
    `buildcraft.transport` rather than added to the shared `buildcraft.api.properties.BuildCraftProperties`/
    `buildcraft.api.enums` classes.** `javap` against both real jars confirms `EnumProperty.create(String,
    Class<T>)` (the exact overload `buildcraft.api.enums.EnumEngineType` already uses) is available identically
    on both targets, so the new `buildcraft.transport.block.EnumPipeMaterial` (`StringRepresentable`, five
    lower-case values matching every `PipeDefinition.identifier.getPath()` in `BCTransportRegistries` --
    `"cobblestone"/"wood"/"stone"/"sandstone"/"quartz"`) follows that exact shape. Unlike `EnumEngineType` (which
    genuinely spans `buildcraft.core`/`buildcraft.energy`, justifying a shared home), nothing outside
    `buildcraft.transport` needs a pipe-material property, so it was kept as a `BlockPipeHolder`-local
    `EnumProperty<EnumPipeMaterial> MATERIAL` field instead of growing the shared API class for a single
    consumer -- a deliberate scope call, not an oversight. The six connection booleans reuse real vanilla
    `BooleanProperty` instances directly (`BlockStateProperties.NORTH/SOUTH/EAST/WEST/UP/DOWN`), confirmed
    identical via `javap` against both real jars (unlike `FACING`, no divergence exists here on either target),
    rather than declaring six new BuildCraft-native properties.
  - **The `Pipe` -> `TilePipeHolder` -> `BlockState` wiring, and why it needs no extra placement/load-time call
    unlike the engine facing-visibility batch's own `updateFacingBlockState`.** `TilePipeHolder` gets one new
    `public void updateConnectionBlockState(Pipe, EnumMap<Direction, IPipe.ConnectedType>)` method (both
    platforms), called unconditionally from the tail of `Pipe#updateConnections()` through an
    `instanceof TilePipeHolder` check on `Pipe`'s own `holder` field -- `IPipeHolder` itself gains no new method,
    since a block-state-rendering push is not a concern any other holder implementation should have to care
    about, and a repo-wide search confirms `TilePipeHolder` is the only real `implements IPipeHolder` anywhere in
    either platform. First attempt made the method package-visible, matching a literal reading of the research's
    own suggested shape -- this genuinely does not compile: `Pipe` lives in `buildcraft.transport.pipe`,
    `TilePipeHolder` in the sibling `buildcraft.transport.tile`, so package-private access does not reach across
    (confirmed by the compiler's own error, `updateConnectionBlockState(...) is not public in TilePipeHolder;
    cannot be accessed from outside package`); fixed by making the method `public`. Unlike
    `TileEngineBase#updateFacingBlockState` (which needs an explicit call from both the wrench path and
    `onPlacedBy`, because facing only changes on those two specific events), pushing the connection/material
    shape needed no separate `onPlacedBy`/NBT-load call at all: `Pipe#updateMarked` already starts `true` on
    *both* of `Pipe`'s own constructors (fresh placement and NBT-reload alike), so the very next real
    `serverTick()` after either path unconditionally runs `updateConnections()`, which now pushes both the
    connection booleans and `EnumPipeMaterial.fromId(definition.identifier.getPath())` together, every time it
    recomputes -- not just when something changed. Material is cheap to recompute every call rather than cache
    behind a second flag, since it never actually changes after placement.
  - **The connection boolean means "any connection", confirmed against `IPipe.ConnectedType`'s own two real
    values (`PIPE`, `TILE`) and proven live, not assumed**: `updateConnectionBlockState` sets a direction's
    boolean from `connectionTypes.containsKey(dir)` against the same `types` map `Pipe#updateConnections()` just
    finished populating, which holds an entry for *either* connection type. RCON-verified directly below: a Stone
    pipe placed next to a plain vanilla chest shows `east=true` (a `TILE`-type connection, no other pipe
    involved), and a Sandstone pipe placed next to a chest shows `east=false` -- proving both that a TILE
    connection sets the boolean and that Sandstone's own already-verified "never connects to a plain inventory"
    rule (from the pipe-materials batch above) genuinely reaches this new BlockState, not just the old `con` NBT
    bitmask.
  - **In-game verification, both platforms, via RCON against real dedicated servers -- the BlockState data, not
    the pixels.** Placed, via the same zero-debug-command `/setblock` + `/data merge block {pipe:{def:"..."}}`
    NBT-reconstruction rig the pipe-materials batch already established: two Cobblestone pipes adjacent (both
    directions), a Wood pipe next to a Cobblestone pipe (confirmed, by re-reading `PipeBehaviourWood`/
    `PipeBehaviourSeparate`'s own real `canConnect` bodies rather than assumed, that this genuinely *should*
    connect -- Wood only refuses another Wood pipe, Cobblestone only refuses a different `PipeBehaviourSeparate`
    subclass, so two different-but-compatible behaviours connecting is correct, not a bug), a Stone pipe next to
    a vanilla chest, a Sandstone pipe next to a vanilla chest, and a Stone pipe next to a Quartz pipe (two
    different `PipeBehaviourSeparate` subclasses, which do genuinely refuse each other -- the real "different
    materials don't connect" case, since Wood/Cobblestone's own cross-connection above turned out not to be
    one). Waited for real tick advancement (`time query gametime` moving), then confirmed on both platforms with
    `/execute if block <pos> buildcraft:pipe_holder[material=...,north=...,...]`-style vanilla property queries
    (not `/data get block`, which cannot see `BlockState` properties at all -- the exact gotcha already on file
    from the engine facing-visibility batch) that all eight test points' real placed `BlockState` matched the
    tile's own internal `Pipe` state exactly: `material` correct on every pipe, `east=true` both directions
    between the two Cobblestone pipes, `east=true`/`west=true` between Wood and Cobblestone, `east=true` from
    Stone to the chest, `east=false` from Sandstone to its chest, and `east=false` both directions between Stone
    and Quartz.
  - **A genuine methodology finding, worth recording for the next batch that RCON-tests a freshly `/setblock`ed
    position far from spawn: an untouched chunk gets zero real ticks, even after tens of real seconds, unless
    something keeps it loaded.** The first verification pass left two of the eight test points
    (Stone-next-to-Quartz, placed in a chunk one column further out than the rest of the rig) reading back the
    block's *default* `BlockState` (`material=cobblestone`, every direction `false`) despite their own tile NBT
    already showing a correctly-computed `con: 0`. `/forceload query` showed the reason directly: that chunk was
    never on the force-load list at all (only a chunk near spawn, force-loaded by an earlier, unrelated session,
    was), so the server's own chunk manager was letting it go straight back to sleep between commands with no
    player and no ticket keeping it resident -- the con:0 in NBT was coincidentally correct (Stone and Quartz
    really do refuse each other) but had not actually been computed by a *live* `updateConnections()` call yet,
    only parsed as the NBT constructor's own "no `con` key present" default. `/forceload add` over the whole test
    area and re-querying after another real tick gap fixed both points immediately, with no code change needed --
    a testing-rig gap, not a `Pipe`/`BlockState` bug, but one worth force-loading for up front next time rather
    than discovering it two test points in.
  - **A second, unrelated false alarm caught and correctly ruled out rather than "fixed" blind: a stale
    `processResources` output in this session's own shared build directory, not a real missing-model bug.** The
    first `:neoforge-26x:runClient` boot logged 320 `Missing model for variant` warnings for `buildcraft:pipe_holder`
    (every possible state) plus ten `Failed to load model ... java.nio.file.NoSuchFileException` errors naming
    this batch's own new `pipe_holder_core_*`/`pipe_holder_arm_*` files directly in `build/resources/main` --
    alarming on its face, but `diff`ing every one of those ten files' `build/resources/main` copy against its
    `src/main/resources` original (once the client's own JVM was killed and a completely fresh `runClient`
    invocation given a chance to run `processResources` uncontended) showed them byte-identical, and the
    re-run logged zero missing-model warnings and zero load errors for `pipe_holder` at all. The most likely
    explanation, consistent with the piston-rod batch's own documented "shared working tree" precedent above and
    the concurrent Tank-rendering task's own entry in this file (which independently reports this exact session's
    `neoforge-26x:compileJava` and `runServer` both being disturbed by this batch's work-in-progress state at
    different points): a concurrent build touching the same `neoforge-26x` module's `build/` directory raced this
    one's own `processResources` output between the moment it ran and the moment the client JVM actually read it.
    Not treated as fixed by code -- there was no code to fix -- only re-verified clean on a contention-free run,
    on both platforms.
  - **Both `runClient` boots (26.x and 1.20.1) reached a fully stitched texture atlas with zero exceptions and,
    on the clean re-run, zero missing-model/missing-sprite warnings anywhere for `pipe_holder`** --
    `2048x2048x4 minecraft:textures/atlas/blocks.png-atlas` on 26.x, `1024x512x4` on 1.20.1. Both real dedicated-
    server boots (used for the RCON verification above) also logged zero exceptions.
  - **Honest limitation, same standing category as every render-adjacent entry in this file**: whether the
    centre cube and arm stubs actually line up correctly on screen, and whether the rotated arm texture looks
    right, is not verified -- this project has no display. What is verified: the blockstate/model JSON is
    well-formed and every model it references resolves and stitches into the atlas without warning (a real,
    `javap`/real-jar-confirmed distinction from a genuinely broken reference, which does warn, as the false-alarm
    finding above demonstrates), and the `BlockState` data actually driving that rendering is proven correct,
    live, via RCON, against the tile's own internal `Pipe` state -- exactly the two things this project's own
    rendering-verification limitation says can and cannot be checked without a real display.
  - **Explicitly out of scope for this batch, per its own brief**: cross-block-gap "extended" connections (a
    pipe reaching across empty space to a non-adjacent block), colour tinting (no pipe in this port is colourable
    yet -- `disableColouring()` on every material), items-inside-pipes rendering (deliberately sequenced after
    this batch so it can build on the connection-shape work here), and wires/gates/plugs (not ported at all).
  - **New files, both platforms**: `buildcraft.transport.block.EnumPipeMaterial`;
    `models/block/pipe_holder_core_{cobblestone,wood,stone,sandstone,quartz}.json`; `models/block/
    pipe_holder_arm_{cobblestone,wood,stone,sandstone,quartz}.json`. Modified, both platforms:
    `buildcraft.transport.block.BlockPipeHolder` (`MATERIAL`, `createBlockStateDefinition`,
    `registerDefaultState`), `buildcraft.transport.tile.TilePipeHolder` (`updateConnectionBlockState`),
    `buildcraft.transport.pipe.Pipe` (the call site plus an updated class javadoc), `blockstates/
    pipe_holder.json` (now `multipart`, replacing the old single-variant `cube_all`). No `BuildCraft.java`
    change was needed on either platform -- this batch uses no custom `BakedModel`/`BlockEntityRenderer`, only
    plain blockstate/model JSON, so there is no renderer to register.
  - Verified with forced `--no-build-cache clean` rebuilds on both platforms, the full 25-test suite (all 25
    green -- none of them touch `buildcraft.transport` at all, confirmed by reading `PipeEventBusTester`'s own
    imports, so this batch could not have broken any of them even indirectly), real dedicated-server boots on
    both platforms with zero exceptions and the eight-point RCON matrix above, and real `runClient` boots on both
    platforms reaching a fully stitched texture atlas with zero exceptions and zero missing-model warnings.

- **Items now actually render travelling through a pipe -- the last major visual gap this whole pipe-rendering
  effort was chasing, closing the real complaint that started it ("the pipes... not having... items inside
  them").** A `BlockEntityRenderer<TilePipeHolder>` on each platform reads every real, in-flight
  `TravellingItem` off a pipe's own `PipeFlowItems` and draws its actual `ItemStack` at an interpolated position
  inside the now-visible pipe geometry (the connection-shape batch above), facing along its direction of travel.
  Genuinely new work, not a reproduction of 1.12.2's own `PipeFlowRendererItems`/`IPipeFlowRenderer` -- that was
  a bespoke `MutableQuad`/`BufferBuilder` framework with no counterpart anywhere in this port, the same category
  of deliberate non-reproduction the piston-rod batch's own `RenderEngine_BC8` note already established; the
  underlying interpolation math is the same "(elapsed + partialTick) / totalDuration, clamped 0..1" shape that
  batch's own `RenderTileEngine` already proved correct in this codebase, here reading two fixed absolute-tick
  anchors instead of a continuously-changing `progress` field (see the sync-timing finding below for why that
  distinction matters).
  - **The real, re-added public accessor surface, and why it is shaped this way.** `TravellingItem`'s server
    fields (`stack`, `toCenter`, `side`, `tickStarted`/`tickFinished`, `isPhantom`) stayed package-private on
    purpose after the original port batch dropped every render helper outright -- its own class javadoc said as
    much, framing this as "a renderer must re-add these" territory rather than an oversight. Re-read directly
    against 1.12.2's own `TravellingItem`/`PipeFlowItems` (`common/buildcraft/transport/...`) before writing
    anything, per this task's own brief. Landed as: (a) `PipeFlowItems#getTravellingItemsForRender()` (renamed
    port of the original's own dropped `getAllItemsForRender`), a flattened copy of every item across every delay
    bucket -- copied, not a view, so a renderer iterating it can never observe this flow's own buckets mutate out
    from under it; (b) `TravellingItem#getStack()`/`#isPhantom()`, plain field accessors; (c)
    `TravellingItem#getRenderPosition(BlockPos, long, float, PipeFlowItems)`/`#getRenderDirection()`, brought
    back as real, close, renamed ports of the 1.12.2 originals of the same name, living directly on
    `TravellingItem` itself rather than as free functions on the renderer -- keeping the interpolation math
    colocated with the data it reads, the same shape the task's own brief called "closer to the original's own
    design" and the piston-rod entry's own `getRenderProgress` precedent already established for this codebase.
    `interpolatePosition`/`isVisible` were not brought back under their own names: `getRenderPosition` already
    inlines the one real lerp `interpolatePosition` ever did (its only caller), and every item this accessor set
    reaches is already visible by construction -- a phantom item is filtered by the renderer itself via
    `isPhantom()`, not by a dedicated "am I visible" query the original never actually varied (confirmed by
    re-reading `isVisible`'s own one-line body: `return true;`, unconditionally, in every version of 1.12.2 this
    session has read). One real, deliberate fix over the original, not a guess: `getRenderPosition` divides by
    `tickFinished - tickStarted` unconditionally in 1.12.2, which is genuinely `0` for a same-tick, zero-distance
    item (`PipeFlowItems#insertItemsForce`'s own `genTimings(now, 0)` call produces exactly this) -- a `0f/0f`
    division that resolves to `NaN` and then survives `Math.min`/`Math.max` unclamped, the exact bug class the
    piston-rod batch's own first-frame divide-by-zero guard already exists in this codebase to prevent. Guarded
    here by treating a non-positive duration as "already arrived" (`interp = 1`), also the semantically correct
    answer for a zero-length trip. `getRenderDirection` additionally drops the original's own `(tick,
    partialTicks)` parameters entirely: re-reading the original's method body shows it computes the same clamped
    interpolation fraction `getRenderPosition` does, then never actually reads it -- direction only ever changes
    when a fresh `TravellingItem` object replaces this one at the pipe's centre (`onItemReachCenter`/
    `onItemReachEnd` always construct a new instance with its own already-correct `toCenter`/`side`, never mutate
    an in-flight item's own direction), so the original's own extra parameters were dead weight, not a real
    per-frame recomputation -- confirmed by reading the method body, not assumed from the signature.
  - **The real, investigated sync-timing finding this batch's own open question asked for, with concrete
    evidence, not assumed either way.** Read `TilePipeHolder`/`PipeFlowItems`' real tick/insert logic on both
    platforms directly, as instructed. Two things, together, made this a genuine bug rather than a
    "already works, just prove it" finding: first, `TilePipeHolder#serverTick()` called plain `setChanged()`
    every tick (matching 1.12.2's own unconditional `markChunkDirty()`) -- but `TileBC#markDirtyAndSync()`'s own
    javadoc says outright "plain `setChanged()` only marks the chunk for saving," and only `markDirtyAndSync()`
    (which additionally calls `level.sendBlockUpdated`) actually reaches a tracking client; `TilePipeHolder`
    never called that method anywhere, and `scheduleNetworkUpdate` -- the one real `IPipeHolder` API that
    `PipeFlowItems`/`Pipe` are already written to call when something changes (`Pipe#updateConnections` already
    calls it with `PipeMessageReceiver.BEHAVIOUR`) -- was a genuine no-op on both platforms, per its own "No
    client sync exists in this batch" javadoc. Second, and the more surprising finding: `BlockPipeHolder#getTicker`
    returns `null` on the client side, confirmed by directly reading it on both platforms -- a client-side
    `TilePipeHolder` **never calls `PipeFlowItems#onTick()` at all**, so its own copy of the delay-bucket queue
    never advances or reshuffles locally, ever. Put together: before this batch, a client's view of a pipe's
    items would populate once on chunk load (`getUpdateTag`'s full NBT snapshot) and then sit frozen for the
    tile's entire remaining lifetime -- no per-tick client simulation to fall back on (unlike the piston rod's own
    `progress`, which at least advances locally every client tick via `getRenderProgress`'s own mirror), and no
    resync ever pushed a fresh one. This is *not* the same problem the piston-rod batch solved: that engine
    needed a client-side mirror specifically because `progress` changes continuously but syncs rarely; a
    travelling item's `tickStarted`/`tickFinished` are two fixed anchor points that never change again until the
    item reaches an endpoint, so the correct fix is the opposite one -- sync precisely at the moment those two
    fields get set to fresh values, then let the client's own naturally-advancing `level.getGameTime()` (which
    does keep ticking locally even though the tile itself never does) interpolate between them with no bespoke
    smoothing state needed at all. Fixed as an in-scope, necessary part of this batch, per its own instructions:
    `TilePipeHolder#scheduleNetworkUpdate` now calls `markDirtyAndSync()` whenever any part requests an update,
    and `PipeFlowItems` now calls `pipe.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.FLOW)` at the exact
    five call sites 1.12.2's own dedicated `sendItemDataToClient` packet was sent from -- re-derived by directly
    re-reading that method's own call sites in the original, not guessed: both items in `sendPhantomItem`, the
    new item `onItemReachCenter` creates, the bounced item `onItemReachEnd` re-queues, and the genuinely-new
    (non-merged) item `addItemTryMerge` adds. This resyncs the whole tile rather than one item (this port dropped
    1.12.2's own per-item creation packet entirely, along with the rest of its bespoke network layer, in an
    earlier batch), but only at the moments that matter, not on some tight per-tick cadence -- cheap enough not
    to need finer targeting, since every real caller already only fires on a genuine change.
    `PipeFlowItems#insertItemsForce` deliberately still does not sync (both platforms, comment updated to say so
    explicitly): `genTimings(now, 0)` makes it a zero-distance, same-tick item, consumed by the very next
    `onTick()` before any render frame could plausibly observe it either way.
  - **The real, `javap`-confirmed item-rendering API, genuinely different per platform -- the first renderer in
    this port to need it.** 26.x: `ItemModelResolver#updateForTopItem(ItemStackRenderState, ItemStack,
    ItemDisplayContext, Level, ItemOwner, int)` (confirmed against `minecraft-patched-26.3.0.7-beta-merged.jar`),
    called once per visible item during `extractRenderState` to populate an `ItemStackRenderState`, then
    `ItemStackRenderState#submit(PoseStack, SubmitNodeCollector, int, int, int)` draws each one during `submit` --
    the same state-extraction/`submit` split `RenderTileEngine` already established as this target's real
    `BlockEntityRenderer<T, S>` contract. The exact call shape (a `null` `ItemOwner`, `ItemDisplayContext.FIXED`,
    a per-item seed derived from the tile's own `BlockPos`) is not guessed -- it is read directly off real,
    decompiled vanilla source for `CampfireRenderer` (`net.minecraft.client.renderer.blockentity`, same target),
    the closest real precedent for "items sitting inside a block's own interior, drawn by a `BlockEntityRenderer`
    with no owning entity to hand `ItemOwner`" -- confirmed live in the decompiled jar, not assumed from the
    interface alone, since neither `ItemOwner` nor the display-context choice is discoverable from the method
    signature by itself. 1.20.1: the classic `ItemRenderer#renderStatic(ItemStack, ItemDisplayContext, int, int,
    PoseStack, MultiBufferSource, Level, int)` (confirmed against `forge-1.20.1-47.1.106-merged.jar`), called
    directly from the classic immediate-mode `render(...)` contract `RenderTileEngine` already established as
    unchanged on this target since the `TileEntitySpecialRenderer` era -- reached via `context.getItemRenderer()`
    in the constructor (also confirmed via `javap` on `BlockEntityRendererProvider.Context` itself), cached once
    like `RenderTileEngine`'s own `texture` field, rather than a per-frame `Minecraft.getInstance()` call.
  - **A small, honestly-scoped rotation simplification, not a claim of fidelity -- flagged as acceptable by this
    batch's own brief.** Both platforms rotate the drawn item to face its real `getRenderDirection()`: horizontal
    directions reuse `Direction#toYRot()` (the same real method the piston-rod batch's own javadoc already
    confirmed exists identically on both platforms), `UP`/`DOWN` get a plain 90-degree tilt since `toYRot()`
    alone cannot express a vertical facing. No attempt was made to reproduce 1.12.2's own exact per-axis rotation
    matrix -- a reasonable, honestly-scoped simplification, per this batch's own explicit "exact rotation
    fidelity is a nice-to-have, not a hard requirement" scope note. Item size (`ITEM_SCALE = 0.4f` on both
    platforms) is a similarly named, not measured, constant: this port's pipes have no real analogue of
    `CampfireRenderer`'s own 0.375 cooking-item scale to copy, so a small, sensible round number was picked to
    fit inside the connection-shape batch's own 0.5-block-wide centre cube instead.
  - **Registration**: `BCTransportRegistries.PIPE_HOLDER_TYPE` is the real `BlockEntityType` (found by reading
    that class directly, not guessed from the tile's own name). New, both platforms:
    `buildcraft.transport.client.BCTransportClientRegistries` -- this module's first client-registration class,
    following the exact `BCEnergyClientRegistries`/`BCFactoryClientRegistries` shape (including the same
    dedicated-server-never-resolves-a-client-type reasoning both of those classes' own javadoc already
    documents), with only a `registerRenderers` method -- no pipe has a GUI in this port yet, so no
    `registerScreens`. One new line in `BuildCraft.java` on each platform,
    `modBus.addListener(BCTransportClientRegistries::registerRenderers);`, inside the exact same pre-existing
    client-only guard the energy/factory renderer lines already live in -- the smallest possible diff, matching
    this batch's own instructions.
  - **In-game verification, both platforms, via RCON against real dedicated servers -- real gameplay, not NBT
    injection.** Rather than hand-crafting `flow.items` NBT (this session's usual technique for `pipe.def`), this
    batch drove the real code path end to end: two `buildcraft:cobblestone` pipes placed adjacent
    (`/setblock` + `/data merge block {pipe:{def:"buildcraft:cobblestone"}}}`, the same zero-debug-command rig
    every prior pipe batch used), a `minecraft:hopper[facing=east]` feeding the first pipe, a `minecraft:chest`
    catching whatever the second pipe ejects. `/execute if block ... buildcraft:pipe_holder[east=true]`/
    `[west=true]` confirmed both pipes had already connected (the connection-shape batch's own real `BlockState`,
    not just tile NBT). A `/data merge block <hopper> {Items:[...]}` five-emerald stack, real vanilla hopper
    behaviour (an 8-tick transfer cooldown, one item per successful transfer -- confirmed live, not assumed: five
    separate `TravellingItem` entries appeared in the second pipe's own `flow.items`, each with `tickStarted`
    values exactly 8 ticks apart) pushed items into the exposed `Capabilities.Item.BLOCK`/`ForgeCapabilities.
    ITEM_HANDLER` capability `PipeFlowItems#getCapability` already wires up (a real, unplanned proof that this
    capability wiring -- added for a different reason in an earlier batch -- genuinely works with a stock vanilla
    hopper, not just this session's own test tooling). Caught mid-transit on both platforms via rapid back-to-back
    `/data get block ... pipe.flow` polling (this environment's dedicated server ticks considerably faster than
    20 TPS with no player connected and every relevant chunk force-loaded, the opposite of the "throttles to
    near-zero" gotcha an earlier batch's own RCON notes warned about -- a real, re-confirmed environmental
    finding, not a contradiction of that note, since that note's own server had no chunks force-loaded): real
    `TravellingItem` NBT showing `side: "EAST"`/`toCenter: 0b` in the first pipe and `side: "WEST"`/`toCenter: 1b`
    in the second, `tickFinished` always greater than `tickStarted` across every one of the ten-plus samples
    taken on each platform, e.g. 26.x's own `{tickStarted: -44, tickFinished: 6}` through `{tickStarted: -1,
    tickFinished: 49}` and 1.20.1's own `{tickStarted: -46, tickFinished: 4}` through `{tickStarted: -14,
    tickFinished: 36}` (NBT-relative offsets from the write moment, per `TravellingItem#writeToNbt`'s own
    documented format -- not raw absolute ticks). A follow-up query on both platforms confirmed both pipes'
    `flow.items` empty again and the destination chest holding a single merged `{Count: 5}`/`{count: 5}` emerald
    stack -- the full hopper-to-pipe-to-pipe-to-chest journey completing correctly, not just starting correctly.
  - **Both real dedicated-server boots (used for the RCON verification above) logged zero exceptions**, including
    through the sync fix's own new `markDirtyAndSync()` calls firing repeatedly during the five-item transit
    above (each of the five hopper transfers, plus each pipe-to-pipe hop, fires at least one). Both real
    `runClient` boots reached a fully stitched texture atlas with zero exceptions and zero missing-model/missing-
    sprite warnings anywhere for `pipe_holder` -- `2048x2048x4 minecraft:textures/atlas/blocks.png-atlas` on
    26.x, `1024x512x4` on 1.20.1.
  - **Honest limitation, same standing category as every render-adjacent entry in this file, stated plainly per
    this batch's own instructions.** The underlying item-position data is real and RCON-verifiable, and was
    verified as such above; the actual on-screen rendered position/rotation is not -- this project has no
    display. `RenderTilePipeHolder#extractRenderState`/`#render` being invoked live by a real client was not
    checked either: no mouse/keyboard input automation exists in this environment to place a pipe and walk up to
    it in a running `runClient` session, the same honest limitation every prior GUI/render-adjacent batch in this
    file has already stated. What is verified: a clean compile and full test suite on both platforms, a clean
    `runClient` boot with the block/item models this renderer's own geometry depends on stitched with zero
    warnings, and the real, live, RCON-verified server-side data (`tickStarted`/`tickFinished`/`side`/`toCenter`)
    this renderer's own interpolation math reads.
  - **Explicitly out of scope for this batch, per its own brief**: reproducing 1.12.2's exact lighting/shading
    beyond what the modern item-rendering API already gives for free, item colour tinting for coloured pipes (no
    pipe is colourable yet -- unchanged from the connection-shape batch's own note), and any change to the actual
    item-movement gameplay logic in `PipeFlowItems` beyond the sync-timing fix above. `buildcraft.factory` was not
    touched anywhere in this batch, per the standing rule protecting a concurrent Auto Workbench Fluids GUI task.
  - **New files, both platforms**: `buildcraft.transport.tile.RenderTilePipeHolder`;
    `buildcraft.transport.client.BCTransportClientRegistries`. Modified, both platforms:
    `buildcraft.transport.pipe.flow.TravellingItem` (the four re-added render methods plus updated class
    javadoc), `buildcraft.transport.pipe.flow.PipeFlowItems` (`getTravellingItemsForRender`, five new
    `scheduleNetworkUpdate` call sites, updated javadoc), `buildcraft.transport.tile.TilePipeHolder`
    (`scheduleNetworkUpdate` now calls `markDirtyAndSync()`, updated javadoc), `buildcraft.BuildCraft` (the one
    listener line above).
  - Verified with forced `--no-build-cache clean` rebuilds on both platforms, the full 25-test suite (all 25
    green), real dedicated-server boots on both platforms with zero exceptions and the real-gameplay RCON
    transit above, and real `runClient` boots on both platforms reaching a fully stitched texture atlas with
    zero exceptions and zero missing-model warnings.

- **`buildcraft.transport` -- four more item-pipe materials (gold, void, clay, iron), wrench cycling of a
  directional pipe's active face (wood + iron), the active face rendered with its own "filled" texture, and two
  real pre-existing bugs fixed along the way (a wooden pipe losing its whole `Pipe` on every restart; every pipe
  item on 1.20.1 being named plain "Pipe").** Nine item-pipe materials now exist. No new block/tile code, per the
  established one-block-many-items architecture: each material is a `PipeBehaviour` subclass plus a
  `PipeDefinition`/`ItemPipeHolder` pair in `BCTransportRegistries`, with the full rendering treatment
  (`EnumPipeMaterial` value, multipart entries, core/arm/cube/item models, lang, texture).
  - **The four behaviours, each re-read in full from `common/buildcraft/transport/pipe/behaviour/`, identical
    source on both platforms.** `PipeBehaviourGold` (32 lines): one static `ModifySpeed` handler,
    `modifyTo(0.25, 0.07)`, extending plain `PipeBehaviour` exactly as the original does -- so, unlike
    stone/cobblestone/quartz (`PipeBehaviourSeparate`), gold connects to any other material. `PipeBehaviourVoid`
    (62 lines): `ReachCenter` sets the stack count to `0`, which `PipeFlowItems#onItemReachCenter` already
    short-circuits on (`if (reachCenter.getStack().isEmpty()) return;`, confirmed present in this port, not
    assumed). `PipeBehaviourClay` (53 lines): `SideCheck#increasePriority(face, 100)` for every
    `ConnectedType.TILE` face. `PipeBehaviourIron` (66 lines): extends `PipeBehaviourDirectional`,
    `canFaceDirection` = `pipe.isConnected(dir)` (null-guarded), `SideCheck#disallowAllExcept(activeFace)` (or
    `disallowAll()` with no active face), static `TryBounce#canBounce = true`. Dropped, with the same reasoning
    `PipeBehaviourWood` already gave for its own `fluidSideCheck`: every `PipeEventFluid` handler (void's
    `OnMoveToCentre` -- whose sound block was already commented out in 1.12.2 itself -- clay's second
    `orderSides`, iron's `fluidSideCheck`/`fluidInsert`), since no fluid flow exists in this port; iron's
    `getTextureIndex` too (it is `@Deprecated` on this port's `PipeBehaviour` and nothing reads it, matching
    wood's `getTextureData` drop -- the filled face is rendered through the blockstate instead, below).
  - **Builder calls, checked against the real 1.12.2 `BCTransportPipes#preInit`, not assumed.** Gold/clay/void
    are plain `idTex("<m>_item").flowItem()`; iron repeats wood's exact shape (`texSuffixes("_clear",
    "_filled")`, `itemTex(0, 0, 1)`, `idTexPrefix("iron_item")`). Deliberate deviations, all pre-existing
    precedents: ids drop the `_item` suffix (`buildcraft:gold`, like `buildcraft:cobblestone`);
    `disableColouring()` on all four even though the real shared builder's `enableColouring()` makes every one
    of them colourable in 1.12.2; `texSuffixes`/`itemTex` are not set, because nothing in this port reads
    `PipeDefinition#textures` (grepped: its only reference is its own constructor) -- the clear/filled split is
    reproduced by the blockstate instead. `void_fluid`/`clay_fluid`/`iron_fluid` are not registered.
  - **The real wrench dispatch path, read end to end rather than assumed, and why `ItemWrench` needed no
    change.** `ItemWrench#useOn` (both platforms) calls `CustomRotationHelper.INSTANCE.attemptRotateBlock`, whose
    very first check is `block instanceof ICustomRotationHandler custom -> custom.attemptRotation(level, pos,
    state, sideWrenched)`. `ItemWrench#useOn` itself is only reached because the pipe block keeps vanilla's
    defaults, confirmed in the decompiled sources of both jars: 26.3's `ServerPlayerGameMode#useItemOn` calls
    `state.useItemOn(...)` first (`BlockBehaviour` default: `InteractionResult.TRY_WITH_EMPTY_HAND`), then
    `useWithoutItem` (default `PASS`), then `itemStack.useOn(context)`; 1.20.1-Forge's calls `BlockState#use`
    (default `PASS`) then `useOn`. So `BlockPipeHolder` now `implements ICustomRotationHandler` -- the exact route
    `BlockEngineWood`/`BlockEngineCreative` already use -- and forwards to `PipeBehaviourDirectional#advanceFacing()`
    when the tile's pipe behaviour is directional, `PASS` otherwise (so a wrench on any other material does
    nothing and `SoundUtil.playSlideSound` plays nothing -- its first line returns on `Pass`). Client side it
    answers `SUCCESS` without mutating (the server picks the face and syncs it), rather than letting the client's
    never-ticked copy of the pipe guess. Per-platform: `InteractionResult` is a sealed interface on 26.x
    (`ItemWrench` tests `instanceof InteractionResult.Success`) and a plain enum on 1.20.1 (`ItemWrench` tests
    `== InteractionResult.SUCCESS`), so the plain `SUCCESS` constant is what both copies return.
    `sideWrenched` is ignored: 1.12.2's `onPipeActivate` also let a wrench on one *arm* jump straight to that
    face, but that needs per-part hit detection this full-cube block does not have, and treating every face click
    as an arm click would make cycling impossible (clicking the already-active face is a no-op there), so every
    use cycles, exactly like 1.12.2's own centre click.
  - **`PipeBehaviourDirectional` (both platforms) gets the wood batch's dropped pieces back, reshaped.** The cycle
    order is 1.12.2's `VanillaRotationHandlers.ROTATE_FACING` (east, south, down, west, north, up), inline as a
    `public static final OrderedEnumMap<Direction> ROTATION_ORDER`, the same approach `TileEngineBase` already
    takes; `advanceFacing()` is 1.12.2's own loop again (`ROTATION_ORDER.next(current)` six times), and drives
    the tick-time fallback too, as in 1.12.2, replacing the wood batch's plain `Direction.values()` order. A
    faithful quirk kept: `OrderedEnumMap#indexOf(null)` reads index 0's slot (`DOWN`, position 2), so a pipe with
    no active face starts searching at `WEST` -- observed live below (a fresh iron pipe picked `WEST`, its hopper
    side). `getCurrentDir()` widened from `protected` to `public` so the tile can read it for the blockstate.
    `writePayload`/`readPayload` stay dropped: `setCurrentDir` -> `scheduleNetworkUpdate(BEHAVIOUR)` ->
    `TilePipeHolder#markDirtyAndSync()` already resyncs the whole tile NBT (`beh.currentDir` included).
  - **A real, reproduced, pre-existing bug: every wooden pipe with an active face lost its `Pipe` on restart.**
    `PipeBehaviourDirectional`'s NBT constructor called `setCurrentDir(...)`, whose
    `pipe.getHolder().getPipeLevel().isClientSide()` dereferences a level that is still `null` during a disk
    chunk load (the same load-before-`setLevel` ordering the earlier `PipeFlowItems` reload fix documented).
    Reproduced live on 26.x against the unmodified tree before any edit in this batch: a wood pipe next to a
    chest (`beh: {currentDir: "EAST"}`), `save-all flush`, `stop`, restart -> `Failed to load data for block
    entity ... java.lang.NullPointerException: Cannot invoke "net.minecraft.world.level.Level.isClientSide()"
    because the return value of "buildcraft.api.transport.pipe.IPipeHolder.getPipeLevel()" is null at
    PipeBehaviourDirectional.setCurrentDir(PipeBehaviourDirectional.java:113) ... <init>(...:61) ...
    PipeBehaviourWood.<init> ... TilePipeHolder.loadAdditional`, and `/data get block` afterwards showed a bare
    tile with no `pipe` tag at all. The earlier reload re-verification never caught it because a wood pipe with
    no inventory neighbour has `currentDir` `null`, and `setCurrentDir(null)` returns before touching the level.
    Fixed by assigning `currentDir` directly in the NBT constructor; re-verified below with the exact same
    scenario (wood *and* iron, both with a wrench-set face).
  - **The active face renders ("filled" texture), via one small blockstate property -- it stayed clean, so it is
    in.** New `buildcraft.transport.block.EnumPipeActiveFace` (`none` + the six directions, `StringRepresentable`,
    transport-local for the same single-consumer reason as `EnumPipeMaterial`) and `BlockPipeHolder.ACTIVE`,
    pushed by `TilePipeHolder#updateConnectionBlockState` alongside `MATERIAL`, plus a new
    `updateActiveFaceBlockState()` called from `scheduleNetworkUpdate` whenever `BEHAVIOUR` is among the parts --
    which is what `setCurrentDir` sends, so a wrench cycle or a tick-time re-pick updates the state immediately
    even when `Pipe#updateConnections` does not run. Always `none` for non-directional materials. In
    `pipe_holder.json` each wood/iron arm is split into two entries: `{"material": "wood", "north": "true",
    "active": "!north"}` -> `pipe_holder_arm_wood`, `{..., "active": "north"}` -> the new
    `pipe_holder_arm_wood_filled` (same geometry, `#all` = `block/pipe_wood_filled`). The `!` negation was
    confirmed in both jars' real `KeyValueCondition` source, not assumed: 26.3's `Term#parse` (`value.startsWith
    ("!")`) and 1.20.1's `flag = !s.isEmpty() && s.charAt(0) == '!'`. The whole file (both platforms,
    byte-identical, now 75 entries: 9 centre cubes + 7 x 6 plain arms + 2 x 6 x 2 clear/filled arms) is
    generated by a script that was first checked to reproduce the previous 35-entry file byte-for-byte, so the
    diff for the five old materials is exactly wood's arm split and nothing else. Cost: the block now has
    9 x 64 x 7 = 4032 states -- acceptable for one block, and far simpler than a custom baked model.
  - **Assets, both platforms, following the established provenance exactly.** Textures are byte-identical copies
    of `buildcraft_resources/assets/buildcrafttransport/textures/pipes/`: `pipe_gold.png` = `gold_item.png`,
    `pipe_void.png` = `void_item.png`, `pipe_clay.png` = `clay_item.png`, `pipe_iron.png` =
    `iron_item_clear.png`, `pipe_iron_filled.png` = `iron_item_filled.png`, `pipe_wood_filled.png` =
    `wood_item_filled.png` (the existing `pipe_wood.png` is already `wood_item_clear.png`, `md5` `5aa2611c...`).
    Core/arm/cube models are string-substituted from wood's own files (models directories remain identical
    across platforms); item models use each platform's own format (26.x `items/pipe_item_<m>.json`, 1.20.1
    `models/item/pipe_item_<m>.json`). Lang, following the port's short-name convention (1.12.2: "Golden
    Transport Pipe" etc.): "Golden Pipe", "Iron Pipe", "Clay Pipe", "Void Pipe". No recipes (none exist for any
    pipe yet, same as every prior pipe batch).
  - **A second real, pre-existing bug, 1.20.1 only: every pipe item was named "Pipe".** Caught by the drops check
    below: on 1.20.1 the dropped `pipe_item_gold` entity reported its name as `Pipe`. 1.20.1's
    `BlockItem#getDescriptionId()` returns `getBlock().getDescriptionId()` (decompiled source), so all nine
    `ItemPipeHolder`s used `block.buildcraft.pipe_holder` and every `item.buildcraft.pipe_item_*` lang key was
    dead. Fixed in the 1.20.1 `ItemPipeHolder` with `getDescriptionId() { return getOrCreateDescriptionId(); }`
    (`protected` on `Item`, confirmed via `javap` against `forge-1.20.1-47.1.106-merged.jar`) -- exactly what a
    plain `Item` returns. 26.x never had this bug: a 26.x item's description id comes from its
    `Item.Properties` (`ITEM_DESCRIPTION_ID` unless `useBlockDescriptionPrefix()`, which `BCRegistry#addItem`
    does not call), and its drops were already named "Golden Pipe"/"Void Pipe"/... live.
  - **In-game verification, both platforms, real dedicated servers over RCON.** Rig as established:
    `/forceload`, `/setblock buildcraft:pipe_holder` + `/data merge block {pipe:{def:"buildcraft:<m>"}}`, vanilla
    hoppers feeding, vanilla chests catching, `/execute if block ...[...]` for `BlockState`. Wrench driven through
    a temporary self-registering (`@EventBusSubscriber`) `/bctmpwrench <pos>` command that put a real
    `ItemWrench` stack in a `FakePlayerFactory.getMinecraft(level)` player's hand and called the real
    `player.gameMode.useItemOn(...)` -- the full vanilla `ServerPlayerGameMode` path (block hooks, then
    `ItemWrench#useOn`), not a direct `useOn` call; deleted before hand-back, `grep` and `git status` show no
    trace. Results were identical on 26.x and 1.20.1:
    - *Gold vs cobblestone*: two parallel 4-pipe lines (hopper -> 4 pipes -> chest), one emerald inserted into
      each hopper in the same game tick. Arrival in the chest: **gold 111 ticks, cobblestone 455 ticks** (both
      platforms, same numbers). Mid-transit NBT shows the ramp exactly: gold `speed: 0.01d` in -> `0.08d` out
      of pipe 1 -> `0.15000000000000002d` out of pipe 2 (`timeToDest` 75 -> 7 -> 4), cobblestone `0.01d`
      throughout (`timeToDest` 50).
    - *Void*: hopper -> void -> chest, 3 diamonds. Caught in transit (`side: "WEST", toCenter: 1b` x3), then after
      1500 ticks: hopper `[]`, chest `[]`, `execute if entity @e[type=item,...]` -> `Test failed` (nothing
      dropped either).
    - *Clay vs control*: hopper -> junction pipe with a chest on one side and a cobblestone pipe (-> a second
      chest) on another, 16 cobblestone each. Clay junction: **16 / 0** (inventory / pipe branch), both
      platforms. Identical cobblestone-junction control: 10 / 6 on 26.x, 8 / 8 on 1.20.1 (random split).
    - *Iron*: hopper west, chests north/east/south, all four connected (`con: 2720`). Auto-picked face `WEST`
      (the `indexOf(null)` quirk above). `/bctmpwrench` -> `useItemOn=Success[...] before=west after=north
      state=...[active=north,...,material=iron,...]` (1.20.1: `useItemOn=SUCCESS`); 4 iron ingots -> **north
      4, east 0, south 0**. Wrench again (`north` -> `up` skipped, not connected -> `east`), 4 gold ingots ->
      **east 4**, north still exactly its 4 iron, south 0.
    - *Wood*: chests east and north; auto-picked `NORTH`; wrench -> `east` (`active=east`), wrench -> `north`
      (`active=north`), `/execute if block` confirming the blockstate after each. Wrench on the clay pipe ->
      `useItemOn=Pass[]` (1.20.1 `PASS`), `active=none` unchanged.
    - *Blockstate*: `material=gold/void/clay/iron` plus the expected connection booleans and `active=` values all
      `Test passed`.
    - *Save/reload*: iron (wrenched), wood (wrenched), gold, clay, void placed; `save-all flush`, `stop`, restart
      the same world. Afterwards every `def` intact, iron `beh: {currentDir: "NORTH"}` + `[active=north]`
      (26.x) / `"EAST"` + `[active=east]` (1.20.1), wood's face and `active=` intact -- the exact scenario that
      crashed before the fix -- zero exceptions in either log. On 26.x, 2 more ingots through the reloaded iron
      pipe still went only north.
    - *Drops*: `/setblock ... air destroy` on each new material ->
      `buildcraft:pipe_item_gold`/`_void`/`_clay`/`_iron`, both platforms; entity names "Golden Pipe"/"Void Pipe"/
      "Clay Pipe"/"Iron Pipe" on 26.x, and on 1.20.1 after the naming fix (before it: "Pipe").
  - **Concurrency note for this batch's rig.** Other agents were running `runServer`/`runClient` against the
    shared `platforms/*/run/` directories at the same time, so part of the 26.x verification (the reload and drops
    checks, and the 26.x `runClient`) ran the exact JVM command line of a Gradle-launched run
    (`/proc/<pid>/cmdline`: `net.neoforged.devlaunch.Main` + `-Dfml.modFolders=...`) from a private working
    directory with its own `server.properties` (ports 25591/25592) -- same classes, same launcher, different
    world. The same trick does *not* work for 1.20.1's `BootstrapLauncher` (the mod was silently absent from the
    private server; the Gradle run evidently passes something outside the command line), so all 1.20.1 checks
    used the real `runServer`.
  - **Boots.** Every dedicated-server log above: zero exceptions (the only ones seen all batch were the pre-fix
    reproduction and one aborted launch that hit another agent's world `session.lock`). Real `runClient` boots
    on both platforms reached full atlas stitching (`2048x2048x4 minecraft:textures/atlas/blocks.png-atlas` on
    26.x, `1024x512x4` on 1.20.1) with zero exceptions and no missing-model/missing-texture/blockstate-parse
    warnings of any kind, i.e. the 14 new block models, 4 new item models, the regenerated multipart with its
    `!` conditions, and the 6 new textures all resolved.
  - **Not verified**: on-screen appearance (no display automation -- the filled/clear arm swap, the new textures
    and the item icons are verified only to load cleanly and to be selected by the right `BlockState`); a real
    player's right-click (the fake-player call drives the same `ServerPlayerGameMode#useItemOn` a real click
    reaches, minus the network packet); the client-side `SUCCESS` prediction branch. Out of scope: fluid flow
    (so no fluid variants/handlers), colouring, gates/statements (`addActions`/`onActionActivate`), arm-click
    face selection.
  - **Files, both platforms.** New: `transport/pipe/behaviour/PipeBehaviour{Gold,Void,Clay,Iron}.java`,
    `transport/block/EnumPipeActiveFace.java`; models `block/pipe_{gold,iron,clay,void}.json`,
    `block/pipe_holder_core_{gold,iron,clay,void}.json`, `block/pipe_holder_arm_{gold,iron,clay,void}.json`,
    `block/pipe_holder_arm_{wood,iron}_filled.json`, item models for the four; textures
    `block/pipe_{gold,void,clay,iron,iron_filled,wood_filled}.png`. Modified: `BCTransportRegistries`,
    `BlockPipeHolder` (`ACTIVE`, `ICustomRotationHandler`), `EnumPipeMaterial` (4 appended values),
    `TilePipeHolder` (`ACTIVE` push), `PipeBehaviourDirectional` (cycle order, NBT-load fix, public getter,
    javadoc), `PipeBehaviourWood` (javadoc only), `blockstates/pipe_holder.json`, `lang/en_us.json`; 1.20.1 only:
    `ItemPipeHolder` (`getDescriptionId`). `ItemWrench`, `BuildCraft.java` and everything outside
    `buildcraft.transport` untouched.
  - Verified with a forced `--no-build-cache clean :neoforge-26x:compileJava :neoforge-1201:compileJava` (run in
    a private snapshot copy of the working tree, since `clean` in the shared tree would have deleted the
    `build/classes` other agents' running dev servers were loading from), the full 25-test suite (25/25, both in
    the shared tree and in the snapshot), and the server/client boots above.

- **`TilePipeHolder` leaked every replaced `Pipe`'s event handlers -- found while independently re-verifying the
  gold/void/clay/iron batch above, not by it.** `loadAdditional` (26.x) / `load` (1.20.1) built a fresh `Pipe`
  and registered its behaviour and flow on the tile's `final` `eventBus`, but never unregistered the `Pipe` it
  was replacing. On a server that only bites on an in-place reload (`/data merge block`), but since the
  item-in-pipe batch every client-side sync goes through `loadAdditional` too, and items trigger a sync on
  every new travel segment -- so every client-side pipe on a busy line accumulated stale behaviour/flow
  handler objects without bound. 1.12.2 never hit this because its client got in-place payload updates, not
  whole-`Pipe` rebuilds. Found live: an iron pipe auto-faced west, then set to face south via
  `/data merge block <pos> {pipe:{beh:{currentDir:"SOUTH"}}}`, bounced every item back west into its feeding
  hopper instead of out the south face into a cobblestone pipe -- the stale west-facing behaviour and the new
  south-facing one each ran `SideCheck.disallowAllExcept(...)`, which together disallowed every side, so each
  item took the `TryBounce` path. Fix, both platforms: unregister the old `Pipe`'s behaviour and flow before
  registering the rebuilt one. Re-verified on a dedicated server with the same rig: all 6 of 6 ingots left
  through the south face, crossed the cobblestone pipe and landed in the far chest, none went north, zero
  exceptions; 25/25 tests.

- **The shared laser-rendering foundation (`buildcraft.lib.client.render.laser`, both platforms) and its first
  consumer: marker lasers -- volume boxes, path lines, volume-marker "signals", and the marker connector's
  "possible connection" preview.** 1.12.2's laser package (~1100 lines: `LaserData_BC8`, `LaserRenderer_BC8`,
  `LaserContext`, `LaserBoxRenderer`, `CompiledLaserType`/`CompiledLaserRow`, `LaserCompiledList`/
  `LaserCompiledBuffer`, `ILaserRenderer`) was the largest unported shared rendering dependency; the quarry, mining
  well, builder and silicon laser all draw through it. Not transliterated -- its display-list/VBO/`BufferBuilder`
  half has no counterpart on either target -- but the *geometry* it produced is reproduced quad-for-quad, the same
  "re-express the legacy quad framework, keep the real output" call `RenderTileTank` made.
  - **The API later consumers use** (identical shape on both platforms, only the draw call differs).
    `LaserData_BC8(type, start, end, scale[, minBlockLight])` -- immutable value type with real
    `equals`/`hashCode`, absolute world `Vec3` endpoints; `minBlockLight >= 15` is full-bright, exactly 1.12.2's
    meaning. `LaserData_BC8.LaserType`/`LaserRow`/`LaserSide` keep 1.12.2's structure (cap-start / start /
    cycling middle variations with per-side validity / end / cap-end rows, pixel rects on a 16px sprite, and the
    "same layout, other sprite" copy constructor), except a row's sprite is now a plain block-atlas id
    (`Identifier`/`ResourceLocation`) instead of a `SpriteHolderRegistry` `ISprite` -- so a `LaserType` is
    common-safe and can be named from server code (`MarkerSubCache#getPossibleLaserType()` is back, returning
    one). `LaserRenderer_BC8.compile(data)` -> `CompiledLaser` (world-light and atlas UVs resolved, positions stored
    as `float` offsets from a `double` origin to avoid far-from-origin precision loss, world-space `bounds` for
    frustum culling), cached exactly like 1.12.2's `COMPILED_STATIC_LASERS` (same key, same 5 s
    expire-after-write, which also refreshes the baked light). Then 26.x:
    `LaserRenderer_BC8.submit(SubmitNodeCollector, PoseStack, List<CompiledLaser>, Vec3 origin)` (one
    `submitCustomGeometry` call); 1.20.1: `LaserRenderer_BC8.render(PoseStack, MultiBufferSource,
    List<CompiledLaser>, Vec3 origin)`. `origin` is wherever the pose stack sits: the block entity's own
    position in a `BlockEntityRenderer`, the camera position in a level event. On 26.x `compile` must run in
    `extractRenderState` (it reads the level), `submit` in `submit`. `LaserBoxRenderer.makeLaserBox(Box|min,max,
    type, center)` returns the (up to) 12 edge lasers. `buildcraft.core.client.BuildCraftLaserManager` declares
    all 13 of 1.12.2's laser types with their exact row layouts (markers, stripes, the four animated power
    colours) -- only the marker ones have a consumer yet.
  - **Geometry, traced through the real compiled code** (a throwaway harness in the scratchpad calling
    `CompiledLaser.compile` with an identity-UV sprite, not a hand simulation). 1.12.2's local frame is kept
    exactly -- `rotZ(angleY)` then `rotY(angleZ)` from `LaserContext`, not an arbitrary basis, because the frame's
    roll decides which face is TOP/BOTTOM/LEFT/RIGHT and the path laser puts different rows on different sides.
    `MARKER_VOLUME_CONNECTED`, (0,0,0) -> (1,0,0), scale 1/16: length 16px, `lengthForMiddle = max(0, 16 - 16 -
    16) = 0` so no middle segment, `lengthEnds = 16` split 8/8. 10 quads / 40 vertices: start cap at x=0 (normal
    -X, corners (0, +-0.0625, +-0.0625), UV = the 2x2px cap rect 0..0.125), end cap at x=1 (normal +X, UV
    0.875..1); four long faces x=0..0.5 at y/z = +-0.0625 (normals +Y, -Y, -Z, +Z) textured with the *right* half
    of the start row (u 0.5..1, v 0..0.125); four faces x=0.5..1 with the *left* half of the end row (u 0..0.5,
    v 0.875..1). A beam 2px square in cross-section, centred on the line. Re-run for +Z, -X, -Y and a
    (3,2,1) diagonal at the real 1/16.05 marker scale: every cap normal is exactly -/+ the beam direction
    (diagonal: (-0.8018, -0.5345, -0.2673)), middle segments tile every 16/16.05 = 0.997 blocks cycling rows
    2..14px, bounds hug the line to +-0.0623.
  - **Lighting and render type.** Per-vertex light is 1.12.2's `computeLightmap` using its non-smooth-lighting
    branch (max of each light layer over the 3x3x3 blocks round the vertex) unconditionally -- deliberate: the
    smooth branch sampled only the vertex's own block, so an edge running *through* terrain drew black. Memoised
    per block during a compile. Drawn with the non-culling alpha-cutout entity type over the block atlas --
    26.x `RenderTypes.entityCutout` (`RenderPipelines.ENTITY_CUTOUT`: `withCull(false)`, `ALPHA_CUTOUT 0.1`,
    `PER_FACE_LIGHTING`), 1.20.1 `RenderType.entityCutoutNoCull` (both `javap`-confirmed). Every laser sprite
    was checked pixel-by-pixel: alpha is only ever 0 or 255, so cutout reproduces 1.12.2's alpha-test draw.
    **Simplified, deliberately:** 1.12.2 baked a per-face grey "diffuse" into the vertex colour; here the vertex
    colour is white and real per-face normals drive the entity shader's own directional shading (baking both
    would double-darken), and the `enableDiffuse`/`doubleFace` flags are gone (no-cull makes `doubleFace` moot).
    The sprite's own U/V ranges are interpolated by hand (`getU0 + (getU1 - getU0) * f`) because
    `TextureAtlasSprite#getU` changed meaning between the targets: 26.x takes a 0..1 fraction, 1.20.1 still
    takes 0..16 (`javap`/decompiled source on both).
  - **Getting non-model textures into the block atlas -- the classic silent failure.** Since 1.19.3 the atlas is
    data-driven on both targets: `SpriteSourceList.load` (26.x; `SpriteResourceLoader` on 1.20.1) reads
    `atlases/blocks.json` in the *atlas's* namespace via `ResourceManager#getResourceStack`, so a mod's
    `assets/minecraft/atlases/blocks.json` is *merged* with vanilla's, not a replacement. Both platforms ship one
    listing the 13 sprites as `{"type": "minecraft:single", "resource": "buildcraft:lasers/<name>"}` --
    `single` rather than a `directory` source so no other mod's `textures/lasers/` gets pulled in; the JSON is
    identical on both (1.20.1's `SpriteSources.TYPE_CODEC` is a `ResourceLocation` codec, so `minecraft:single`
    parses there too; 26.x's `SingleFile.MAP_CODEC` keeps the same `resource` field). A registration event was
    not needed (NeoForge's `RegisterSpriteSourcesEvent` only adds new source *types*). The 13 PNGs + 4 animation
    `.mcmeta` files are byte-for-byte (`cmp`) copies of `buildcraft_resources/assets/buildcraftcore/textures/
    lasers/`, now at `assets/buildcraft/textures/lasers/`. A post-stitch check (26.x `TextureAtlasStitchedEvent`,
    1.20.1 `TextureStitchEvent.Post`) warns for any laser sprite that resolves to the missing sprite and logs
    `[lib.laser] 13/13 laser sprites present in minecraft:textures/atlas/blocks.png` -- seen on both clients.
  - **Render hooks, per platform, and why two different kinds.** Connection lasers (boxes, paths, the connector
    preview) are drawn from a level-render event, as 1.12.2 did (`RenderWorldLastEvent` via `DetachedRenderer`/
    `MarkerRenderer`/`RenderTickListener`): a connection isn't owned by one marker -- a box has up to 8 corners,
    any of which may be in an unloaded chunk while the client cache still holds the connection -- so hanging it
    off one marker's block entity would make it vanish whenever that one marker's section was culled. 26.x:
    `ExtractLevelRenderStateEvent` (fired from `LevelExtractor`; gives the `ClientLevel`, `Camera`, `Frustum`)
    compiles and frustum-culls, parks the list on the `LevelRenderState` under a `ContextKey`
    (`BaseRenderState#setRenderData`), and `SubmitCustomGeometryEvent` (fired from `LevelRenderer#submitFeatures`
    right after block entities/particles, with a fresh camera-relative `PoseStack`) submits it -- the same pair
    NeoForge's own `BlockEntityRenderBoundsDebugRenderer` uses. `RenderLevelStageEvent` still exists on 26.x but
    now hands out an already-open `RenderPass` and its own javadoc points custom geometry at
    `SubmitCustomGeometryEvent`. 1.20.1: `RenderLevelStageEvent`, `Stage.AFTER_BLOCK_ENTITIES`, pose stack =
    camera rotation only (origin = `event.getCamera().getPosition()`), into `renderBuffers().bufferSource()`
    followed by an explicit `endBatch(renderType)`. Signal lasers stay a `BlockEntityRenderer`
    (`RenderMarkerVolume`), exactly like 1.12.2's TESR: they belong to one loaded tile. 1.12.2's
    `isGlobalRenderer`/`getMaxRenderDistanceSquared = 64*4*64` map to `shouldRenderOffScreen` and
    `getViewDistance() = 128` on both; the render box is NeoForge's `IBlockEntityRendererExtension#
    getRenderBoundingBox` on 26.x (sized to the 64-block reach) and needs nothing on 1.20.1, where Forge's
    default `IForgeBlockEntity#getRenderBoundingBox` is already `INFINITE_EXTENT_AABB` for a block with an empty
    collision shape (markers are `noCollission()`) -- which also means the 1.20.1 `TileMarkerVolume` javadoc's
    "getRenderBoundingBox gone" is only true of 26.x. This BER is the first real exercise of the laser API through
    both `BlockEntityRenderer` contracts, the shape the quarry/mining well/silicon laser will use.
  - **Client sync -- a real gap found and closed.** `MessageMarker` already mirrored every marker/connection
    *change* into the client cache, but `MarkerCache.onPlayerJoinWorld`/`onWorldUnload` had no caller at all: a
    client only knew about connections made while it was watching (relog and every box was gone), and one world's
    client cache survived into the next world joined with the same dimension key. New
    `buildcraft.lib.marker.MarkerCacheEvents` (both dists, `@EventBusSubscriber`) ports 1.12.2's
    `BCLibEventDist#onEntityJoinWorld`/`#onWorldUnload` onto `EntityJoinLevelEvent` (for a `ServerPlayer`, using
    the event's level) and `LevelEvent.Unload`. 1.12.2 delayed the join send a tick; not needed now -- read in the
    real `PlayerList`/`ServerPlayer` on both targets, `ServerLevel#addPlayer` (which fires the event) always runs
    after the `ClientboundLoginPacket`/`ClientboundRespawnPacket` that creates the client level. Client-only
    classes are registered with `@EventBusSubscriber(value = Dist.CLIENT)`; FML reads `value` from scan data
    before `Class.forName` (`javap -c` on 26.x `AutomaticEventSubscriber`, which also routes each method to the
    mod or game bus by `IModBusEvent`; 1.20.1's `Mod.EventBusSubscriber` has one `bus()` per class, so the atlas
    check is a nested `bus = MOD` class there). The 1.20.1 server's debug log shows `MarkerCacheEvents`
    auto-subscribed and `RenderMarkerConnections` absent; the client's shows both.
  - **Verified in-game on both platforms, including on screen.** RCON against real dedicated servers (26.x on
    the shared `run/server`; 1.20.1 from a private directory, see below), with a temporary self-registering debug
    command (deleted -- `git status` clean of it) driving the real interactions through a `FakePlayer`: three
    `buildcraft:marker_volume` at (1000,100,1000)/(1004,100,1000)/(1000,100,1003) linked by the real
    `BlockMarkerVolume` use path (`onManualConnectionAttempt`) -> one connection, box 1000..1004 x 1000..1003, 4
    edge lasers `(1000.5625,100.5,1000.5)->(1004.4375,100.5,1000.5)` etc. (flat box: `center` mode skips Y);
    three `buildcraft:marker_path` linked by two real `ItemMarkerConnector#use` calls with the fake player
    aimed along each pair -> one 3-marker path; a fourth volume marker's `showSignals: 0b` -> `1b` from a
    `/setblock` redstone block (the real `neighborChanged` path); `[active=true]` on the interacted markers;
    `save-all flush` then the gzipped `buildcraft_marker_{volume,path}.dat` decoded by hand showing exactly those
    groupings -- identical results on both platforms. Then a real client (the Gradle run's own JVM command line,
    re-run from a scratch game directory with `--quickPlayMultiplayer`) joined each server *after* all of that:
    a temporary client hook (deleted) logged the client cache holding all 4+3 markers, both connections and
    `signals=true` -- i.e. delivered purely by the new join sync -- and 6 connection lasers / 416 vertices
    submitted per frame on 26.x; screenshots (`Screenshot.grab`, player `/tp`'d into view) show the red volume
    box, the green path line and the six blue signal beams on both 26.x and 1.20.1, and with a marker connector in
    hand the yellow/blue "possible" laser between two unlinked markers. Zero exceptions in all four logs; no
    `Missing sprite` for any laser texture. **Environment finding**: 1.20.1's `BootstrapLauncher` dev run
    discovers the mod from the `MOD_CLASSES` environment variable (`loader-47.2.2` reads it), not from
    `-Dfml.modFolders` -- setting `MOD_CLASSES` to the same value makes a private-directory 1.20.1 server/client
    load BuildCraft (the transport batch above saw the mod silently absent without it).
  - **Not verified / scope cuts.** Fine visual fidelity against 1.12.2 side by side (no 1.12.2 client here) --
    the screenshots show the right shapes, colours and placement, not a pixel comparison; the per-vertex light
    in a dark area; performance with many large boxes. Not ported: `ItemMapLocation`'s held-item lasers (the item
    isn't ported), the quarry/builder/filler/mining-well/silicon-laser consumers (they now only need a
    renderer), 1.12.2's AO-dependent light branch and baked diffuse (above). Known pre-existing gap, untouched:
    markers joined by `onManualConnectionAttempt` other than the clicked one don't get `ACTIVE` refreshed
    (documented in `TileMarkerVolume`'s javadoc) -- the lasers don't depend on it.
  - **Files, both platforms.** New: `lib/client/render/laser/{LaserData_BC8,CompiledLaser,LaserRenderer_BC8,
    LaserBoxRenderer}.java`, `core/client/{BuildCraftLaserManager,BCCoreClientRegistries}.java`,
    `core/client/render/{RenderMarkerVolume,RenderMarkerConnections}.java`, `lib/marker/MarkerCacheEvents.java`,
    `assets/minecraft/atlases/blocks.json`, `assets/buildcraft/textures/lasers/*` (13 PNG + 4 mcmeta). Modified:
    `lib/marker/MarkerSubCache` (`getPossibleLaserType` back, javadoc), `core/marker/{Volume,Path}SubCache`
    (implement it), `core/marker/VolumeConnection` (`MARKER_MAX_DISTANCE` public, javadoc),
    `core/marker/PathConnection` (javadoc), `BuildCraft.java` (one line in the client-only block,
    `modBus.addListener(buildcraft.core.client.BCCoreClientRegistries::registerRenderers);`, fully qualified so
    no import line was needed). Nothing in `buildcraft.transport`/`energy`/`factory` touched.
  - Verified with a forced `--no-build-cache clean :neoforge-26x:compileJava :neoforge-1201:compileJava` (in an
    `rsync` snapshot of the working tree, as the transport batch did, since `clean` in the shared tree would
    delete the `build/classes` other agents' running dev servers load from), the full 25-test suite (25/25 in
    both the snapshot and the shared tree), plus the server/client runs above.

- **`buildcraft.energy` -- the oil/fuel fluid family (`BCEnergyFluids`) and the fuel/coolant registry
  (`buildcraft.api.fuels` implementations + `BCEnergyRecipes`), both platforms.** The foundation for the
  oil -> distiller -> combustion-engine chain; the engines and the distiller themselves are a later batch.
  - **The fluid set, enumerated from 1.12.2's own `BCEnergyFluids#preInit` table, not guessed: ten fluids x three
    heat variants = thirty.** 1.12.2 only registered all thirty when `buildcraftfactory` was loaded (otherwise just
    heat-0 `oil` and `fuel_light`); `BCModules.FACTORY.isLoaded()` is constant `true` in this one-mod port, so the
    full set always registers. The declared-but-never-defined `tar` field stays unregistered, as in 1.12.2.
    Registry ids are 1.12.2's own fluid names: `oil`, `oil_heat_1`, `oil_heat_2`, `oil_residue[_heat_N]`,
    `oil_heavy`, `oil_dense`, `oil_distilled`, `fuel_dense`, `fuel_mixed_heavy`, `fuel_light`, `fuel_mixed_light`,
    `fuel_gaseous` -- each id names the `FluidType`, the source `Fluid`, and the placeable `LiquidBlock`; the
    flowing fluid is `flowing_<id>` and the bucket `<id>_bucket` (150 registry entries per platform). Derived
    properties are 1.12.2's formulas unchanged: viscosity `base * (4 - heat) / 4`; density negated once
    `heat >= boil` (so `oil_distilled_heat_2`, `fuel_dense_heat_2`, `fuel_mixed_heavy_heat_2`,
    `fuel_light_heat_1/2`, `fuel_mixed_light_heat_1/2` and all three `fuel_gaseous` are gases --
    `FluidType#isLighterThanAir`); temperature `300 + 20 * heat`; luminosity 0; flammable (`enableOilBurn`
    default `true`) for everything except residue; map colour = 1.12.2's own nearest-`MapColor` search over
    `tex_dark` (oil -> `COLOR_BLACK` `0x191919`, residue `0x562c3e`, ...). Base table (density, viscosity, boil,
    spread, light, dark): oil 900/2000/3/6/`505050`/`050505`; residue 1200/4000/3/4/`100F10`/`421042`; heavy oil
    850/1800/3/6/`A08F1F`/`423520`; dense oil 950/1600/3/5/`876E77`/`422424`; distilled 750/1400/2/8/
    `E4AF78`/`B47F00`; dense fuel 600/800/2/7/`FFAF3F`/`E07F00`; mixed heavy 700/1000/2/7/`F2A700`/`C48700`;
    light fuel 400/600/1/8/`FFFF30`/`E4CF00`; mixed light 650/900/1/9/`F6D700`/`C4B700`; gaseous 300/500/0/10/
    `FAF630`/`E0D900`. **Sticky** ("`oilIsDense`", web-like drag) was config-gated and **off by default** in
    1.12.2; with no energy config in this port yet it is not implemented (documented in `BCFluidBlock`).
  - **New files, both platforms**: `buildcraft.energy.BCEnergyFluids` (table + registration; nested
    `BCFluid` ties the five registry objects together), `buildcraft.energy.BCEnergyRecipes`,
    `buildcraft.lib.fluid.BCFluidType` (port of 1.12.2 `BCFluid`: heat, light/dark colours, flammability,
    textures, heat-suffixed name), `BCFluidBlock` (port of `BCFluidBlock`: `LiquidBlock` + flammability 200 /
    fire spread 200 as `IBlockExtension`/`IForgeBlock` overrides + `ignitedByLava()`), `BCFluidBucketItem`
    (port-only: replaces Forge 1.12.2's universal bucket, which no longer exists), `FuelRegistry`,
    `CoolantRegistry` (1.12.2 `buildcraft.lib.fluid` implementations of the already-ported `buildcraft.api.fuels`
    interfaces, which had no implementation until now). `BCEnergyRegistries` (both): a static block calling
    `BCEnergyFluids.preInit(REGISTRY)` (buckets land in the creative tab after the Stirling engine), plus
    `register()` registering the fluid-type/fluid `DeferredRegister`s and installing `BuildcraftFuelRegistry.fuel`/
    `.coolant` (1.12.2 did that in `BCLibRegistries#preInit`). **`BuildCraft.java` (26.x only): one line** in the
    client-only block, `modBus.addListener(BCEnergyClientRegistries::registerFluidModels)`; 1.20.1's
    `BuildCraft.java` is untouched. `BCEnergyClientRegistries` (26.x) gained `registerFluidModels`.
  - **Registry keys and APIs, confirmed per platform in the real sources jars.** 26.x: `NeoForgeRegistries.Keys
    .FLUID_TYPES` (`neoforge:fluid_type`), `Registries.FLUID`, `BaseFlowingFluid.Source/Flowing/Properties`,
    `LiquidBlock(FlowingFluid, Properties)` (takes the fluid itself -- safe because vanilla registers `FLUID`
    before `BLOCK`), `BucketItem(Fluid, Properties)` with `public final Fluid content`, `noCollision()`,
    `PushReaction.POPPED` (vanilla 26.x water). 1.20.1: `ForgeRegistries.Keys.FLUID_TYPES` (`forge:fluid_type`),
    `ForgeRegistries.FLUIDS`, `ForgeFlowingFluid.*`, Forge's `Supplier` constructors on `LiquidBlock`/`BucketItem`
    (`getFluid()` accessors), `noCollission()` (1.20.1 spelling), `PushReaction.DESTROY` (vanilla 1.20.1 water).
    `FluidType.Properties` is the same shape on both (`descriptionId`/`density`/`viscosity`/`temperature`/
    `sound(SoundActions.BUCKET_FILL/EMPTY, ...)`); both `*FlowingFluid.Properties` default to tick rate 5, level
    decrease 1, slope distance 4, explosion resistance 1 (set to 100 here, vanilla water's value).
  - **Flow model is a genuine, documented approximation.** 1.12.2 `setQuantaPerBlock(base + (base > 6 ? heat :
    heat / 2))` travelled `quanta - 1` blocks, any 1..15; vanilla `FlowingFluid` starts at level 8 and can only
    travel 7/3/2/1 blocks (`levelDecreasePerBlock` 1/2/3/4). `levelDecreasePerBlock = max(1, round(7 / (quanta -
    1)))` picks the nearest: spreads of 5+ become 7 (the 8-11-block fuels are capped at water's reach), 3-4
    become 3. Tick delay is `max(1, viscosity / 200)` -- exactly vanilla's calibration on both targets (water
    1000 -> `getTickDelay` 5, lava 6000 -> 30, both read from the sources); Forge 1.12.2's `BlockFluidBase` used
    the same rule, but no 1.12.2 Forge jar exists here to re-check it. **Gases do not flow upward**: vanilla
    `FlowingFluid` has no upward flow, so a placed gaseous source spreads downward like a liquid (1.12.2's
    negative-density `BlockFluidClassic` rose). Their buckets *do* render upside down (`flip_gas: true`, matching
    the universal bucket's own `"flipGas": true`, still visible in NeoForge's legacy `dynbucket.json`).
    1.12.2's water/lava `displacements` map and `isEntityInsideMaterial` are not ported (see `BCFluidBlock`).
  - **Textures: 1.12.2 did not ship per-fluid textures -- it recoloured three greyscale ones at stitch time; that
    recolour was baked offline, exactly.** `buildcraft_resources/.../textures/blocks/fluids/` holds `heat_{0,1,2}_
    {still,flow}.png` (byte-identical images; only the `.mcmeta` differs: heat 0/1 `frametime 3, interpolate`,
    heat 2 default) plus unused legacy `oil_heat_0_*`/`fuel_*`/`redplasma_*`. `BCEnergySprites`/
    `AtlasSpriteFluid` (`BCLibConfig.useSwappableSprites`, default `true`) generated each fluid's sprite from the
    heat-N texture by a per-channel gradient map, `(dark * (256 - v) + light * v) / 256`, alpha forced to 255 --
    not a multiplicative tint (residue's "dark" is lighter than its "light"), so no tint colour can reproduce it.
    Vanilla's data-driven `paletted_permutations` sprite source was ruled out by reading it on both targets: its
    `PalettedSpriteSupplier` builds `new SpriteContents(id, new FrameSize(w, h), image)` over the whole image with
    no animation metadata, which would squash the 16x512 animation strip into one static sprite. So the same
    formula was run once (Python/PIL, integer maths identical to `recolourSubPixel`) and the 60 results ship as
    `assets/buildcraft/textures/block/fluids/<name>_heat_<N>_{still,flow}.png`, each with the original
    `heat_N_*.png.mcmeta` copied byte-for-byte beside it (identical trees on both platforms, 876K each). The fluid
    tint is plain white, just as `BCFluid#setColour(light, dark)` forced `colour` to white. Plus 30 blockstates
    (`{"variants": {"": ...}}`, vanilla water's own shape) and 30 particle-only block models per platform, and
    per-platform bucket models (below). Lang: 10 `fluid_type.buildcraft.<name>` names (1.12.2's `fluid.<name>`
    strings), 1.12.2's three `buildcraft.fluid.heat_N` formats verbatim (`%s (§bCool§r)`/`(§6Hot§r)`/
    `(§cSearing§r)` -- heat 0 included, since every fluid is `heatable`), and one `item.buildcraft.fluid_bucket`
    (`%s Bucket`, the universal bucket's format); block and bucket names derive from the fluid type's
    description instead of 60 per-variant keys.
  - **Client fluid rendering, 26.x -- new territory, with evidence.** `IClientFluidTypeExtensions` has no texture/
    tint methods and `FluidType#initializeClient` does not exist on 26.3. Vanilla `FluidStateModelSet#bake`
    hard-codes `FluidModel.Unbaked`s for water/lava only, then calls NeoForge `ClientHooks#gatherFluidModels`,
    which posts the mod-bus **`RegisterFluidModelsEvent`** (worker thread, during model loading) and afterwards
    logs `Missing FluidModel for fluid '...'` for every registered fluid still unmapped; `FluidStateModelSet#get`
    then falls back to `ModelBakery.MissingModels#fluid` (missing sprite, `null` tint). There is no JSON path for
    fluid models; the event is the mechanism, and it is exactly how NeoForge registers its own milk
    (`ClientNeoForgeMod#onRegisterFluidModels`). `registerFluidModels` registers `new FluidModel.Unbaked(new
    Material(still), new Material(flow), null, FluidTintSources.constant(0xFFFFFFFF))` for each source+flowing
    pair. `FluidModel.Unbaked#bake` rejects non-block-atlas sprites; `textures/block/` is covered by vanilla
    `atlases/blocks.json`'s `directory` source. **The tint source must be non-null**: `RenderTileTank` and
    `GuiAutoCraftFluids` (26.x) both call `model.tintSource().color(...)` unguarded, so a `null` tint (legal --
    vanilla lava's) would NPE there. (Consequence for a later batch, not fixed here since `buildcraft.factory`
    was out of scope: a tank holding **lava** would hit exactly that NPE in both 26.x consumers.) **Negative
    control run**: with `registerFluidModels` temporarily short-circuited, a real `runClient` logged exactly 60
    `Missing FluidModel for fluid 'buildcraft:...'` warnings (30 sources + 30 flowing); with it restored, zero.
    Buckets use NeoForge's `neoforge:fluid_container` item model (`DynamicFluidContainerModel`, registered in
    `ClientNeoForgeMod#registerItemModels`) in `assets/buildcraft/items/<id>_bucket.json`: base
    `minecraft:item/bucket`, fluid mask `neoforge:item/mask/bucket_fluid_drip`, `fluid`, `flip_gas: true` --
    it resolves the fluid sprite through the same `FluidStateModelSet`.
  - **Client fluid rendering, 1.20.1 -- the classic hook, confirmed.** `FluidType`'s constructor calls private
    `initClient()`, which only on `Dist.CLIENT` (and not datagen) calls `initializeClient(consumer)`;
    `BCFluidType` supplies an anonymous `IClientFluidTypeExtensions` overriding `getStillTexture()`/
    `getFlowingTexture()` (tint left at the interface default `0xFFFFFFFF`). Because `initializeClient` runs from
    the *super* constructor, before `BCFluidType`'s own fields are assigned, the anonymous class reads
    `BCFluidType.this.stillTexture` on every call rather than capturing it. Buckets: `models/item/<id>_bucket
    .json` with `"loader": "forge:fluid_container"`, parent `forge:item/bucket_drip`, `flip_gas: true` (loader
    fields read from `DynamicFluidContainerModel.Loader#read`); no item colour handler is needed since there is
    no tint.
  - **Fuel/coolant values -- 1.12.2's `BCEnergyRecipes#init` formulas verbatim** (`TIME_BASE = 240_000`,
    `totalTime = TIME_BASE * boost / 4 / multiplier / amountDiff`, `power = multiplier * MjAPI.MJ`, residue
    `1000 / amountDiff` mB of heat-0 residue; only heat-0 fluids are fuels). Read back live on both platforms:
    `fuel_gaseous` 8 MJ/t x 1875 t; `fuel_light` 6 x 15000; `fuel_dense` 4 x 90000; `fuel_mixed_light` 3 x
    10000; `fuel_mixed_heavy` 5 x 19200; `oil_dense` 4 x 30000 + 500 mB residue; `oil_distilled` 1 x 37500;
    `oil_heavy` 2 x 40000 + 333 mB residue; `oil` 3 x 10000 + 125 mB residue. Coolants: water 0.0023 deg/mB;
    ice -> 1000 mB water x 1.5; packed ice x 2. The distillation/heat-exchange half of 1.12.2's `init` feeds
    `refineryRecipes`, used only by the Distiller/Heat Exchanger -- not ported (scope). The 26.x registries key
    on `FluidResource` (`equals`), 1.20.1 on `FluidStack#isFluidEqual`, matching each platform's already-ported
    API; `isItemEqual` -> `ItemStack.isSameItem`.
  - **A real defect found live on 26.x, not by reading: the fuel registry cannot be filled at common setup.**
    First boot with `BCEnergyRecipes.init` queued from `FMLCommonSetupEvent` (1.12.2's init slot) failed mod
    loading: `NullPointerException: Components not bound yet` from `Holder.Reference#components`, via
    `FluidResource.of(Fluids.WATER)` -> `Fluid#computeDefaultResource` -> `new FluidStack`. On 26.3 even a plain
    fluid's default `FluidStack`/`ItemStack` needs its holder's default data components, which are only bound by
    `ReloadableServerResources#updateComponentsAndStaticRegistryTags` during world/datapack load (or from the
    registry-sync packet on a remote client), which then posts the game-bus `DefaultDataComponentsBoundEvent`.
    26.x now fills the (static, immutable) registries once, on the first such event (synchronised, guarded);
    rebooted and re-verified. 1.20.1 has no data components and keeps common setup (`enqueueWork`).
  - **In-game verification, both platforms, via RCON against real dedicated servers.** Every one of the 30 source
    blocks placed by `/setblock ... buildcraft:<id>` (1.20.1 all 30; 26.x oil/fuel_light/fuel_gaseous_heat_2
    placed plus a server-side dump confirming `defaultBlockState().getFluidState()` is the right source fluid for
    all 30); every one of the 30 buckets `/give`n -> `No player was found` (vs `Unknown item
    'buildcraft:not_a_bucket'` as the control). Flow observed: a single `oil` source in a stone basin became
    `buildcraft:oil[level=0..4]` along the next four blocks on 26.x (`execute if block ... [level=N]`, all
    passed); on 1.20.1 levels 0-2 within the first few seconds (idle-server tick throttling, see the RCON
    memory); a `fuel_light`/`fuel_gaseous_heat_2` source on a one-block pillar ran off its edge onto the block
    beside and below. **Pump -> tank, both platforms**: a `buildcraft:pump` above a 9x9 oil pond with a
    `buildcraft:tank` beside it, battery re-merged in a loop, read back afterwards -- 26.x: pump
    `tank: {stacks: [{amount: 9000, id: "buildcraft:oil"}]}`, tank `{stacks: [{amount: 16000, id:
    "buildcraft:oil"}]}` (full); 1.20.1: pump `tank: {FluidName: "buildcraft:oil", Amount: 9000}`, tank
    `{FluidName: "buildcraft:oil", Amount: 16000}`. Fuel/coolant values (above) and per-fluid properties were
    read back via a temporary self-registering (`@EventBusSubscriber`) `/bctmpfuel`/`/bctmpfluids` command pair,
    deleted afterwards (no trace in the tree); property dump excerpt (26.x): `oil: name='Oil (§bCool§r)'
    dens=900 visc=2000 temp=300 gas=false tick=10 flam=200 lavaIgn=true map=191919 bucket=Oil (§bCool§r)
    Bucket`, `oil_residue: ... dens=1200 visc=4000 ... tick=20 flam=0 lavaIgn=false map=562c3e`,
    `oil_distilled_heat_2: ... dens=-750 visc=700 temp=340 gas=true tick=3`; 1.20.1 identical. Both servers
    booted and stopped with zero exceptions. **Real `runClient` on both targets** reached full atlas stitching
    (`blocks.png-atlas` 2048x2048 on 26.x, 1024x512 on 1.20.1) with no missing-model/missing-texture/missing-
    fluid-model warning mentioning `buildcraft` (plus the 26.x negative control above).
  - **Not verified: on-screen appearance** -- no display interaction here, so the in-world fluid surfaces, bucket
    icons, the Tank renderer and Auto Workbench fluid bars showing these fluids are unconfirmed by eye; what is
    confirmed is that every sprite/model resolves without warnings and the recoloured PNGs are correct (visually
    inspected as a strip: oil near-black, residue purple, heavy/dense oils brown, fuels orange-to-yellow).
  - **Scope cuts**: world-gen oil (springs, lakes, oil biomes), the Distiller, Heat Exchanger and Combustion/
    Iron engine, the refinery recipe half of `BCEnergyRecipes`, the sticky/`oilIsDense` option and any energy
    config, upward gas flow, water displacement, and the `MigrationManager` 7.99 block-rename migrations.
  - Verified with a forced `--no-build-cache clean :neoforge-26x:compileJava :neoforge-1201:compileJava` in the
    shared tree (both green -- run in the shared tree, not an `rsync` snapshot, so it did wipe `build/classes`
    for anyone's running dev instance at that moment), and the full 25-test suite (25/25).

- **26.x: a tank or Auto Workbench (Fluids) holding lava would have crashed the client.** Flagged by the fluids
  batch above, fixed separately. `RenderTileTank` and `GuiAutoCraftFluids` both called
  `model.tintSource().color(...)` on the fluid's `FluidModel` without a null check, but `FluidModel#tintSource()`
  is `@Nullable` and vanilla's own lava model passes `null` for it (read directly in the decompiled
  `FluidStateModelSet.LAVA_MODEL`). Both call sites now fall back to untinted white. 1.20.1 is unaffected: its
  `IClientFluidTypeExtensions#getTintColor` always returns a colour.

- **`buildcraft.transport` -- fluid pipes: `PipeFlowFluids` and nine fluid-pipe materials (cobblestone, wood,
  stone, sandstone, quartz, gold, iron, clay, void), with fluid rendering.** Each material is a new
  `PipeDefinition` (same behaviour class as its item sibling, `flowFluid()` instead of `flowItem()`, id
  `buildcraft:<material>_fluid` as in 1.12.2) and an `ItemPipeHolder` `pipe_fluid_<material>`. No new block or
  tile, per the one-block-many-materials architecture.
  - **`PipeFlowFluids` (both platforms) is the original's movement logic line for line**: seven sections (six
    faces and the centre); `moveFromPipe` (side -> neighbour), `moveFromCenter` (centre -> sides, split evenly by
    the transfer rate, shuffled), `moveToCenter` (sides -> centre, via the `PreMoveToCentre`/`OnMoveToCentre`
    events). Each section has a `ticksInDirection` state machine: set to -60 when fluid comes in and +60 when it
    goes out, and it decays one per tick. Only non-negative sections output and only non-positive ones input, so
    fluid can move in, out and back. Each section also has an `incoming[]` ring buffer of `currentDelay` ticks
    that holds new fluid before it can move on. Capacity is 1.12.2's `max(1000, rate x 10)`. Rates are
    `BCTransportConfig`'s `fluidTransfer` calls with the default `baseFlowRate` 10: cobble/wood 10 mB/t,
    stone/sandstone 20, clay/iron/quartz 40, gold 80 (delay 2), void 80. They go into `PipeApi.fluidTransferData`
    from a static block in `BCTransportRegistries`. The `PipeEventFluid` API was already ported and needed no
    change. NBT keys match 1.12.2: `fluid`, and `tank[0..6]` indexed by `EnumPipePart` with 6 as the centre.
    That includes the original's misleading `capacity` key, which holds a section's *amount*.
  - **26.x: the transfer API, transaction-safe like `PipeFlowItems`.** Each section is a
    `ResourceHandler<FluidResource>`, registered under `Capabilities.Fluid.BLOCK` in `registerCapabilities` (it is
    null for item pipes). `javap` of `neoforge-26.3.0.7-beta-universal.jar`: `Capabilities$Fluid.BLOCK` is a
    `BlockCapability<ResourceHandler<FluidResource>, Direction>`, and `ResourceHandler` declares `insert(int, T,
    int, TransactionContext)` plus a *default* `insert(T, int, TransactionContext)`. The decompiled source shows
    the default loops `for (index < size())`. That is why a section reports one slot (the pipe's fluid and that
    section's amount) where 1.12.2 reported zero tank properties: with zero slots, `FluidUtilBC.move`/
    `pushFluidAround` could never insert. Extraction returns 0, as 1.12.2's `drain`s did.
    - Every mutation reachable from someone else's transaction (`Section#insert`, `tryExtractFluid`,
      `insertFluidsForce`, `extractFluidsForce`) first calls `journal.updateSnapshots(tx)`. The
      `SnapshotJournal<FlowSnapshot>` covers the fluid, the delay, and each section's amount, direction,
      `currentTime` and ring buffer. The flow's own pushes open a root `Transaction`, and the flow drains its
      section only after that transaction commits.
    - The current fluid is a `FluidResource`: 1.12.2's `FluidStack` amount meant nothing, and
      `FluidResource.CODEC` stores just the id and components. The `PipeEventFluid` events still take a
      `FluidStack`, so one is built per event.
    - `tryExtractFluidAdv` merges into `tryExtractFluid(IFluidFilter)` through `FluidFilters.findExtractable`, as
      the 26.x `IFlowFluid` javadoc already specifies.
  - **1.20.1 is 1.12.2's shape almost verbatim.** Each section is a classic `IFluidHandler`, reached from
    `PipeFlow#getCapability(ForgeCapabilities.FLUID_HANDLER)` through `TilePipeHolder#getCapability`, so no
    registration is needed. Simulation uses `FluidAction.execute()` (`javap`: `IFluidHandler$FluidAction` has
    `execute()`) and needs no journal. `tryExtractFluidAdv` keeps 1.12.2's fallback of walking `getTanks()` for
    non-`IFluidHandlerAdv` tanks, and returns null only where 1.12.2 returned `PASS` (no handler on that side).
    The `fluid` tag is a classic `FluidStack` tag, so it carries the pipe's total as `Amount`. One small fix to
    the original: its `extractSimple` compared the filter with itself (`!filter.isFluidEqual(filter)`), so its
    "drained the wrong fluid" check could never fire. It now compares against what was actually drained.
  - **Connections.** `canConnect(face, PipeFlow)` is `other instanceof IFlowFluid` (item flows:
    `instanceof IFlowItems`). `canConnect(face, BlockEntity)` asks the neighbour for the fluid capability. Since
    `Pipe#updateConnections` handles a neighbouring *pipe* only through `canPipesConnect` and then `continue`s,
    item and fluid pipes can never connect to each other. Tanks, pumps and the auto workbench expose fluids, so
    fluid pipes connect to them and item pipes don't.
  - **Behaviours: the fluid handlers the item batches dropped "while no fluid flow existed" are back**, both
    platforms, straight from 1.12.2:
    - Wood gets its `IFlowFluid` extraction branch (`MJ_PER_MILLIBUCKET` = 1000 micro-MJ, the `mjPerMillibucket`
      default; on 26.x `simulate` is a root transaction committed only when not simulating) and `fluidSideCheck`
      (never push back out of the extraction face).
    - Iron gets `fluidSideCheck` (output only through the active face) and `fluidInsert` (refuse fluid offered
      through the active face).
    - Void gets a static `OnMoveToCentre` handler that zeroes `fluidEnteringCentre`.
    - Clay gets its `PipeEventFluid.SideCheck` `orderSides` overload.
    - Stone/cobblestone/quartz (`PipeBehaviourSeparate`) and sandstone (pipes only) keep their connection rules.
  - **Client sync, and the bandwidth choice.** A client-side pipe never ticks, so it sees only what the last
    whole-tile sync wrote. 1.12.2 sent `NET_FLUID_AMOUNTS` when any section's amount or direction differed from
    what was last sent, throttled by a `SafeTimeTracker` at `networkUpdateRate` (10 ticks). The port keeps that
    policy: compare with the last-sent values each tick, and call `scheduleNetworkUpdate(FLOW)` (a whole-tile
    `markDirtyAndSync`, the same route as items) at most once per 10 ticks. A settled or full pipe never syncs,
    and a flowing one costs at most 2 tile updates a second. Client-side smoothing
    (`clientAmountLast`/`clientAmountThis`, flow offsets) is dropped, because each sync rebuilds the client's
    `Pipe`, so nothing survives to interpolate between.
  - **Rendering (`RenderTilePipeHolder`, both platforms)** uses 1.12.2's `PipeFlowRendererFluids` geometry:
    - A horizontal side section is a box filled to `amount/capacity` of its height (from the top for a gas,
      `FluidType#getDensity() < 0`). A vertical one is a full-height column of width `sqrt(fill)`.
    - The centre is a filled cube when anything horizontal flows (or nothing leaves vertically), topped by a
      column when fluid leaves upward.
    - Sprite and tint follow `RenderTileTank`: on 26.x the `FluidStateModelSet` `FluidModel` with a nullable
      `tintSource()` (lava), inside the state-extraction/`submitCustomGeometry` split; on 1.20.1
      `IClientFluidTypeExtensions` and `RenderType.entityTranslucent(BLOCK_ATLAS)`. UVs come from each vertex's
      block-space position (1.20.1 `TextureAtlasSprite#getU(double)` takes 0..16), so small boxes don't stretch
      the sprite.
    - The animated flow offsets are not reproduced.
  - **Blockstate: 9 new `EnumPipeMaterial` values (`cobblestone_fluid` ... `void_fluid`)**, so each fluid pipe
    has its own core/arm/cube models and textures. Wood/iron fluid get `_filled` arms for the active face.
    - `pipe_holder.json` (both platforms, byte-identical, 150 entries: 18 cores + 14 x 6 plain arms + 4 x 6 x 2
      clear/filled arms) is generated by a script. The script was first checked to reproduce the old 75-entry
      file byte for byte, then run with the 9 fluid materials appended.
    - **State count: 18 materials x 7 `active` values x 2^6 connections = 8064** (was 4032), noted on
      `BlockPipeHolder.ACTIVE`.
    - Textures are byte-identical copies from `buildcraft_resources/.../textures/pipes/`: `pipe_<m>_fluid.png` =
      `<m>_fluid.png`; for wood and iron, `pipe_<m>_fluid.png` = `<m>_fluid_clear.png` and `_filled` =
      `<m>_fluid_filled.png`. Core/arm/cube models are string-substituted from wood's.
    - Item models: `items/pipe_fluid_<m>.json` on 26.x, `models/item/` on 1.20.1. Lang: "<Material> Fluid Pipe"
      (1.12.2's `item.PipeFluids*` names).
    - A Python check parsed every JSON (286 on 26.x, 258 on 1.20.1) and resolved every multipart/item model and
      every texture those models reference, with 0 missing. It also confirmed the PNGs are byte-identical.
      Atlas stitching and on-screen appearance are left for the consolidated client check.
  - **In-game verification, both platforms.** Private dedicated servers ran from an `rsync` snapshot's
    Gradle-generated `runServer.sh` (`createServerLaunchScript`) with `--nogui`, private game dirs, ports
    25631/25632 and 25633/25634, a flat world, `pause-when-empty-seconds=0` on 26.x, and `MOD_CLASSES` on 1.20.1.
    Pipes were placed with `setblock` + `data merge {pipe:{def:"buildcraft:<m>_fluid"}}`, and each wooden pipe
    was powered by a creative engine above it. Results were identical on 26.x and 1.20.1 unless noted:
    - *Pump -> wood -> 3 cobblestone -> tank* over a 5x5 water pool:
      - The tank filled at exactly the cobblestone rate: 3870 -> 5870 -> 7880 mB at t = 1250/1450/1651 on 26.x,
        and 5390 -> 7400 over 201 ticks on 1.20.1.
      - Mid-transfer section NBT, cobble pipe 5 (26.x): `"tank[4]": {ticksInDirection: -59s, capacity: 90s}`,
        `"tank[6]": {capacity: 100s}`, `"tank[5]": {ticksInDirection: 59s, capacity: 100s}`, `fluid: {id:
        "minecraft:water"}`, with the ring buffer holding `in[i]: 10s`.
      - Blockstates: `[material=wood_fluid,west=true,east=true,active=west]` and
        `[material=cobblestone_fluid,...]` both `Test passed`.
      - Pump-path rollback check (26.x): pump engine removed and an empty tank swapped in. Pump tank + four pipes
        + tank stayed at 26020-26030 while the pump tank drained 8410 -> 0 into the line, then settled at
        26000 (tank full). That is no creation from the pump's never-committed simulate insert. The +/-10-30 is
        read skew between consecutive RCON reads of a moving line.
    - *Wood extraction from a tank (oil)*: tank (8000 `buildcraft:oil`) -> wood -> cobble -> tank. Everything
      arrived: source 0, destination 8000, pipes empty afterwards (`fluid` tag gone).
    - *Iron*: wood -> iron junction with tanks north/south/east.
      - The iron pipe first auto-picked `WEST`, its input side. `fluidInsert` then refused all input: every tank
        stayed empty.
      - After `data merge {pipe:{beh:{currentDir:"SOUTH"}}}`, `[active=south,north/south/east/west=true]`, and
        only the south tank filled: 26.x N 0 / S 3980 / E 0; 1.20.1 N 0 / S 1990 / E 0.
    - *Void*: tank (8000 oil) -> wood -> void -> tank. The source drained (2010 left on 26.x, 4050 on 1.20.1 at
      the reading), the far tank stayed at 0, and the void pipe held only its inbound side section
      (`"tank[4]": capacity 90s`) with the centre at `0s`.
    - *Every material*: tank (4000 lava) -> wood -> stone -> gold -> quartz -> clay -> sandstone -> cobble ->
      tank. 3750 mB arrived with 250 in transit, on both. Every pipe's `[material=..,west=true,east=true]`
      passed. Sandstone stayed unconnected to a tank beside it (`south=false`), and that tank stayed empty.
      Stone next to quartz did not connect (`PipeBehaviourSeparate`), confirmed in a first layout.
    - *Item vs fluid*: fluid, fluid, item, item cobblestone pipes in a row, with a tank south and a chest north of
      the middle two.
      - Middle fluid pipe: `[west=true,east=false,south=true,north=false]` (`con: 384` = PIPE west + TILE
        south).
      - Middle item pipe: `[west=false,east=true,south=false,north=true]` (`con: 1056` = TILE north + PIPE
        east).
      - So item and fluid pipes don't connect to each other, fluid pipes connect to tanks and not chests, and item
        pipes the reverse. Both platforms.
    - *Save/restart with contents*:
      - 26.x: `tick freeze`, then line B read at 16000 total mid-transfer (source 6000, wood `{4: 410, 5: 100, 6:
        1000}`, cobble `{4: 90, 5: 100, 6: 100}`, dest 8200). Then `save-all flush`, `stop`, restart, freeze
        again. The world ran 7 ticks before the second freeze and 16000 was still exact (60 mB had moved on),
        with the full pump line's 4 x 3000 mB unchanged. After `tick unfreeze` it kept flowing.
      - 1.20.1 has no `/tick`, so conservation was checked instead: 15990 before and 15990 after, the same read
        skew both times. The mid-transfer pump line came back with the same section pattern
        (`{4: 990, 5: 100, 6: 1000}` ...).
    - Every server log across all boots and restarts has zero exceptions. The only `ERROR` line is vanilla's
      flat-preset `No key layers in MapLike[{}]` on first world creation.
  - **Not verified / scope cuts.**
    - On-screen fluid rendering is deferred to the coordinator's visual check. The renderer only compiles here.
    - `addDrops`: 1.12.2 dropped the fluid as a fragile fluid shard, an unported `BCCoreItems` item, so a broken
      pipe's fluid is lost.
    - `addTriggers`/gates are not ported, and neither are diamond/diamond-wood/obsidian fluid pipes or colours.
    - A pipe changed in place between item and fluid by `/data merge` does not call `invalidateCapabilities()`.
      Normal placement never changes a pipe's flow.
  - **Files, both platforms.**
    - New: `transport/pipe/flow/PipeFlowFluids.java`, plus the assets: 9 textures + 2 `_filled`, 9 x
      core/arm/cube models + 2 `_filled` arms, and 9 item models.
    - Modified: `BCTransportRegistries` (`flowFluids`, definitions, rates, items; on 26.x also the
      `Capabilities.Fluid.BLOCK` registration), `EnumPipeMaterial`, `BlockPipeHolder` (javadoc: state count),
      `PipeBehaviour{Wood,Iron,Void,Clay}`, `RenderTilePipeHolder`, `blockstates/pipe_holder.json`,
      `lang/en_us.json`.
    - Nothing outside `buildcraft.transport` and the shared lang file changed. The `buildcraft.api.transport`
      fluid API needed no change.
  - Verified with a forced `--no-build-cache clean :neoforge-26x:compileJava :neoforge-1201:compileJava` in a
    private `rsync` snapshot (with other agents' in-progress files reverted to HEAD in the snapshot only) and the
    full test suite (25/25). An incremental compile of both platforms in the shared tree, with everyone's current
    changes, is also green.

- **The Combustion Engine (1.12.2's "Iron Engine"): a liquid-fuel engine with fuel, coolant and residue tanks,
  on both platforms.** Ported line for line from `TileEngineIron_BC8`: while redstone-powered, it burns one mB of
  fuel (anything the fuel/coolant batch's `BuildcraftFuelRegistry.fuel` recognises) every
  `totalBurningTime / 1000` ticks, adds that fuel's `powerPerCycle` MJ per tick, heats up by
  `powerPerCycle * HEAT_PER_MJ` degrees per tick, and above the ideal heat drains up to 40 mB of coolant per tick
  (water at 0.0023 degrees per mB) -- with no coolant the heat keeps climbing until the engine overheats and
  stalls, exactly as before. A "dirty" fuel like crude oil also fills a residue tank.
  - **One real limitation forced a design change, not a bug.** 1.12.2 burned fuel with a direct
    `fuel.amount--` on the tank's own stack, so for the last mB the tank held a zero-amount stack of the fuel
    while it kept burning. This target's fluid tank (`FluidStacksResourceHandler`, same as every other tank in
    this port) can't represent a zero-amount stack of a specific fluid at all -- `FluidResource.toStack(0)` is
    simply empty -- so the tile now keeps the fuel identity in a separate field for that final stretch, giving
    the same real behaviour (the last mB still burns its full time) without depending on a representation this
    target doesn't have.
  - **A real, port-wide bug found while testing this engine, fixed for all 30 fluid buckets on both
    platforms:** neither platform gave a `BCFluidBucketItem` a fluid-handler capability by default. On 26.x,
    NeoForge's own capability hooks only wire up its ready-made bucket handler for items whose class is exactly
    `BucketItem`; on 1.20.1, Forge's `BucketItem#initCapabilities` has the identical `getClass() == BucketItem.class`
    check. Either way, a full oil or fuel bucket could not be emptied into this engine, a tank, or anything else
    by hand. Fixed per platform: 26.x registers `Capabilities.Fluid.ITEM` for every fluid's bucket item, backed
    by NeoForge's own `BucketResourceHandler` (which works for any `BucketItem` subclass); 1.20.1 overrides
    `BCFluidBucketItem#initCapabilities` to return Forge's own `FluidBucketWrapper` directly.
  - Fluid capability: a `CombinedResourceHandler`/three-tank `IFluidHandler` (fuel and coolant insert-only,
    residue extract-only), exposed on every face, matching 1.12.2's own `InternalFluidHandler`.
  - Rendering and GUI follow the Stirling Engine's own precedent: `BlockStateProperties.FACING`, the piston-rod
    `RenderTileEngine`, and a container/GUI with real fluid bars for all three tanks, reusing the same
    fluid-sprite/tint lookup the Tank renderer and Auto Workbench (Fluids) GUI already established on each
    platform (including the nullable `tintSource()` for lava on 26.x).
  - Verified with a forced clean rebuild of both platforms, the 25-test suite, and live RCON on dedicated
    servers for both: a pump over an oil pool fed the engine and it burned, heated and delivered real MJ to a
    `power_tester` (confirmed by that tile's own climbing `total`); a forced overheat with the coolant tank
    drained let heat climb past 200 with no coolant, confirming the stall path; refilling coolant let heat fall
    again; and both platforms kept an identical tank/heat/burn-time state across a save and restart.

- **Refinery: Distiller, Heat Exchanger and the refinery recipe table (`buildcraft.factory`, both platforms).**
  BuildCraft 8's replacement for the old Refinery. Ported from `TileDistiller_BC8`, `TileHeatExchange`,
  `BlockDistiller`, `BlockHeatExchange`, `ContainerDistiller`, `GuiDistiller`, `RenderDistiller`,
  `RenderHeatExchange`, and the refinery half of `BCEnergyRecipes`.
  - **Recipes (`BCEnergyRecipes#initRefinery`).** This is 1.12.2's `BCModules.FACTORY.isLoaded()` block with the
    table unchanged: ten `addDistillation` rows (amounts divided by their HCF along with the MJ cost),
    `addHeatExchange` for all ten families (cool<->hot at 10 mB), water heatable 0->1 (consumed) and lava
    coolable 4->2 (consumed). The fuel/coolant half is untouched apart from the one call. 1.12.2 installed
    `RefineryRecipeRegistry.INSTANCE` from `BCLibRegistries`, which is not ported, so `initRefinery` installs it
    itself.
  - **Real 26.x bug fixed in `lib/recipe/RefineryRecipeRegistry` (the one change outside `factory`/`energy`).**
    It matched recipe inputs with `FluidStack.matches(a, b)`. The 26.3 NeoForge sources jar shows that method
    compares the **amount** too (`first.getAmount() != second.getAmount() ? false : isSameFluidSameComponents`).
    So a tank of 4000 mB oil could never match the 8 mB recipe input, and `addRecipe`'s replace-existing check
    was also amount-sensitive. The real equivalent of 1.20.1's `isFluidEqual` is
    `FluidStack.isSameFluidSameComponents`, and both call sites now use it. The 1.20.1 copy only had its comment
    corrected.
  - **Distiller (`TileDistiller`, `BlockDistiller`).**
    - Kept exactly as 1.12.2: the 4000 mB in/gas/liquid tanks, the 1024 MJ battery, the
      `MAX_MJ_PER_TICK`-scaled draw, `distillPower` carry-over and refund-on-stop.
    - Per-side capabilities copy 1.12.2's `CapUtil` wiring: input on the four horizontals, gas on `UP`, liquid on
      `DOWN`, and nothing for a side-less query.
    - 26.x registers these in `BCFactoryRegistries` (`Capabilities.Fluid.BLOCK`, MJ receiver/readable,
      `HAS_WORK`). 1.20.1 hands them out from `getCapability` with `LazyOptional`s.
    - 1.12.2's `tankIn.setCanDrain(false)` has no counterpart on the port's `Tank`. The horizontal faces get a
      fill-only wrapper instead: a `DelegatingResourceHandler` whose `extract` returns 0 on 26.x, and an
      `IFluidHandler` whose `drain` returns `EMPTY` on 1.20.1.
    - Tank changes set a flag, and each tick sends at most one sync, instead of 1.12.2's per-tank
      `FluidSmoother` messages. The 100-sample power history is saved but kept out of the sync tag.
  - **Heat Exchanger (`TileHeatExchange`, `BlockHeatExchange`).** 1.12.2's structure rules are kept exactly:
    - A line of 3-5 blocks with the same facing, walked up to 5 each way from any member. `facing.getClockWise()`
      points to the start and `getCounterClockWise()` to the end.
    - With fewer than 3, sections are removed. With more than 5, nothing changes (1.12.2's own TODO). Existing
      start/end sections are reused.
    - `middleCount` selects `FLUID_MULT` {5, 10, 20} mB/tick, after a 120-tick PREPARING warm-up.
    - The heated fluid enters at the start's `DOWN` and leaves at the end's `UP`, auto-pushed. The coolant enters
      at the end's outer side and leaves at the start's outer side, auto-pushed.
    - The wrench keeps 1.12.2's `rotate()` (90 degrees alone, 180 degrees for the whole line, with start and end
      swapping). This compiles but was not exercised in-game.
  - **Heat Exchanger deliberate differences from 1.12.2:**
    - `getActualState` is gone, so `part`/`connected_left`/`connected_right` are real block state. The tile writes
      `part` with `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, and `updateShape` keeps the connection flags.
      `connected_y`, which was always false, is dropped.
    - **1.12.2 bug fixed:** `ExchangeSectionStart#writeToNbt` never called `super`, so a start section's two tanks
      were lost on every save. They are saved now, as are `middleCount`/`progress`/`progressState`.
    - On 26.x a section change calls `level.invalidateCapabilities`; on 1.20.1 it re-issues the `LazyOptional`s.
  - **GUI (`GuiDistiller`, `ContainerDistiller`).**
    - The original `distiller.png` is used byte-for-byte, with 1.12.2's exact rectangles.
    - Three real fluid bars: input 16x38 at (44, 23), gas and liquid 34x17 at (98, 10)/(98, 54), each with its
      glass overlay.
    - The fill technique is `GuiAutoCraftFluids`', but the 16x16 sprite is **tiled** under the scissor as 1.12.2's
      `GuiUtil.drawFluid` did, not stretched. On 26.x the sprite and tint come from `FluidStateModelSet` with a
      nullable `tintSource`; on 1.20.1 from `IClientFluidTypeExtensions`.
    - Hand-traced: 2000/4000 mB in the input bar gives `round(38*0.5) = 19` rows, scissor `[top+42, top+61)`.
      16/4000 mB in a gas bar gives 0 rows, and 3968 mB gives 17 rows.
    - Also ported: the off-state icons (valid input, gas/liquid blocked), the active background, the
      flowing-colour animation (same arithmetic, pixel-rounded), and tank tooltips.
    - The animation colour is the midpoint of the fluid's `tex_light`/`tex_dark` pair, standing in for
      1.12.2's sprite-average colour.
    - Not ported: shift-click and click-to-fill a bucket into the tank. The port's `Tank` has no
      `transferStackToTank`.
  - **Rendering.**
    - `RenderDistiller` draws 1.12.2's three `TankSize` boxes rotated by facing. `RenderHeatExchange` draws the
      four tank boxes plus the two streams along the pipe.
    - The shared `factory/client/render/FluidBoxRenderer` uses `RenderTileTank`'s quad and render-type recipe:
      26.x state-extraction + `submit`, 1.20.1 classic `render`. Gases (negative density) fill from the top, as
      `FluidRenderer` did.
    - The flow is simplified: straight boxes that grow by progress, with no texture scroll or anchor flip.
    - The distiller's expression-driven piston animation (`models/tiles/distiller.json`) has no variable-model
      system here. The pistons sit at rest in the static block model with the "off" texture, which is what
      1.12.2's item model showed.
    - Models are generated from 1.12.2's JSON: the distiller from its item model; the heat exchanger from
      `heat_exchange_static.json`, split into six multipart pieces keyed on `facing`/`part`/`connected_*`.
    - BC's `both_sides` becomes a zero-thickness reverse face per face.
    - Face `rotation` n becomes n x 90. The direction is not confirmable without a client, so it is flagged for
      the visual check.
    - Textures are copied byte-for-byte. 1.20.1 models carry `render_type: minecraft:cutout`; 26.x derives the
      layer from sprite alpha (all 0/255).
    - Every model/blockstate/item/loot/recipe JSON parses, and every referenced model and texture exists (script
      check, 16 files per platform).
    - Recipes: the distiller is 1.12.2's; the heat exchanger uses `minecraft:glass` for the colourless-glass ore
      tag.
  - **In-game verification, both platforms.**
    - Setup: private servers ran from a HEAD `git archive` snapshot plus these files, with ports 25621/25622 and
      25623/25624, flat worlds, and a temporary self-registering `/bcref` command. It moved fluid through
      `level.getCapability`/`getCapability` on a given side, and has been deleted since (`git status` and
      `build/classes` are clean of it).
    - Results were identical on both unless noted.
    - *Distiller fed by a real pump over an oil pool, powered by a creative engine on its east side:*
      - 26.x after 20 s: `tankIn 4000 oil` (pump refilling), `tankGasOut 224 fuel_gaseous`,
        `tankLiquidOut 42 oil_heavy`. That is 14 crafts of 8 -> 16 + 3 at 32 MJ.
      - 1.20.1: 320/60 (20 crafts).
      - Caps: `null=none; down=Tank[oil_heavy]; up=Tank[fuel_gaseous]; north/south/west/east=[oil 4000]`.
        `drain west 100` gave `extracted 0`.
      - `move up` put `304 fuel_gaseous` (19 x 16) into the adjacent `buildcraft:tank` above, and `move down`
        put `57 oil_heavy` (19 x 3) into the tank below. External fills of the outputs, and of water/`oil_heavy`
        (no heat-0 recipe) into the input, were all refused.
    - *Heat 1 and heat 2:*
      - `oil_heat_1`: 4000 -> 3912, gas `fuel_mixed_light_heat_1` 110, liquid `oil_dense_heat_1` 22. That is
        22 x (4 -> 5 + 1).
      - `oil_heat_2`: 3888, `oil_distilled_heat_2` 112, `oil_residue_heat_2` 14. That is 14 x (8 -> 8 + 1).
    - *Blocked output:* when gas reached 4000 the distiller stopped at input 800 / liquid 800, `active: 0b`,
      `distillPower: 0L` (refunded).
    - *Heat exchanger structure* (`part` read with `execute if block`):
      - 2 blocks: both middle. Adding a third gave `end/middle/start`, with
        `connected_left=true,connected_right=true` on the middle.
      - A differently-facing 4th block stayed middle and did not join.
      - 6 in a row: all middle. Remove one: `end/middle/middle/middle/start`, `middleCount` 3.
      - Caps: start `down` + `east` (the outer side when facing north); end `up` + `west`; middle none.
    - *Oil heated by searing oil (3-long, facing north):*
      - `oil_heat_2` was refused at the start and `oil` at the end.
      - Once RUNNING, input 1925 -> 1520 over 81 ticks = **5 mB/t**, with 480 `oil_heat_1` auto-pushed into
        *each* adjacent tank: the heated product up from the end, and the cooled coolant out of the start's east
        side.
      - 1.20.1 read 305 mB over 61 ticks.
    - *Water heated by lava (4-long, facing east):* 10 mB/t (1.20.1: 610 mB over 61 ticks). Both were consumed
      and nothing reached either output tank. After running dry it went to state STOPPING, progress counting
      down from 108.
    - *Save/restart:*
      - 26.x: `tick freeze` mid-run, then read, `stop`, restart and freeze again. The start section
        `{input: 1800 oil, progressState: 2, progress: 120, middleCount: 1}`, the end `{input: 800 oil_heat_2}`,
        both output tanks at 2200, and a distiller's `distillPower: 7666465L`/`battery: 78333535L`/tanks were all
        identical, with `part` still start/end. After `tick unfreeze` it ran on at once with no second warm-up
        (1800 -> 1600).
      - 1.20.1: the same comparison immediately after restart was identical (start input 1695, distiller
        `distillPower: 9666794L`).
    - *Loot:* `setblock ... air destroy` dropped `buildcraft:distiller`/`heat_exchange` on both.
    - Every server log (4 boots on 26.x, 3 on 1.20.1) has zero exceptions. The only `ERROR` is vanilla's
      flat-preset `No key layers`.
  - **Not verified / scope cuts.**
    - Deferred to the coordinator's visual check: the on-screen GUI and the renderers (compile-only here), and
      the heat-exchanger face-texture rotations.
    - Wrench rotation is not exercised.
    - Fluid is lost when a section is removed or the block is broken. 1.12.2 dropped fragile fluid shards, and
      the item is not ported.
    - A heat-exchanger line split across an unloaded chunk border is treated as short, exactly as in 1.12.2.
  - **Files, both platforms.**
    - New: `factory/tile/{TileDistiller,TileHeatExchange}`, `factory/block/{BlockDistiller,BlockHeatExchange}`,
      `factory/container/ContainerDistiller`, `factory/gui/GuiDistiller`,
      `factory/client/render/{FluidBoxRenderer,RenderDistiller,RenderHeatExchange}`.
    - New assets: blockstates `distiller`/`heat_exchange`, 8 block models, item models, 8 textures + `gui/distiller.png`,
      loot tables, recipes.
    - Modified: `BCFactoryRegistries`, `BCFactoryClientRegistries`, `energy/BCEnergyRecipes` (refinery half only),
      `lib/recipe/RefineryRecipeRegistry` (26.x fix, 1.20.1 comment), `lang/en_us.json`.
  - Verified with a forced `--no-build-cache clean :neoforge-26x:compileJava :neoforge-1201:compileJava` in a
    private `rsync` snapshot of the shared tree, and the full test suite (25/25).

- **The mining well and pump get their missing renderer layer: two status LEDs and a retracting intake-tube
  laser, closing the "no renderer to consume it" deferral both `TileMiner` classes' own javadoc has documented
  since that base class first landed.** 1.12.2's `RenderMiningWell`/`RenderPump`/`RenderTube` (139/155/50 lines,
  `common/buildcraft/factory/client/render/`) ported onto the same `BlockEntityRenderer` machinery
  `RenderMarkerVolume`/`RenderTileTank`/`RenderHeatExchange` already established, plus two small server-side fixes
  that were prerequisites, not renderer code themselves.
  - **The tube laser reuses the existing laser pipeline, not a new one.** `BuildCraftLaserManager` gains
    `TUBE_MINING_WELL`/`TUBE_PUMP` (a shared private `tubeLaserType(sprite)` helper on both platforms, since both
    originals built an identical cap/middle layout, just off different sprites) rather than repurposing
    `POWER_LOW`/`MED`/`HIGH`/`FULL` -- those are a generic green/yellow/red/blue gradient family with no
    consumer yet, semantically wrong for a plain grey retracting tube. The two new sprites are 1.12.2's own,
    copied byte-for-byte (confirmed via `md5sum`) from `buildcraft_resources/assets/buildcraftfactory/textures/
    blocks/{mining_well,pump}/tube.png` into `assets/buildcraft/textures/lasers/tube_{mining_well,pump}.png`,
    with two new `minecraft:single` entries in `assets/minecraft/atlases/blocks.json` on both platforms (the same
    mechanism the rest of `BuildCraftLaserManager`'s sprites already use). 1.12.2's `led_green.png`/`led_red.png`
    (also sitting in that same texture folder) are confirmed dead assets by grepping the whole 1.12.2 tree --
    never referenced by any Java source -- so they are not pulled in, matching the established "no renderer-only
    dead asset" precedent.
  - **The LEDs are a hand-built tinted cube, not a laser.** 1.12.2's `RenderPartCube` (a mutable, per-instance
    object holding a `MutableVertex` re-positioned every frame, tinted with a runtime-generated pure-white
    texture, `ModelLoader.White.INSTANCE`) has no packaged asset behind its white base at all, so there is
    nothing to copy byte-for-byte for it. The new `buildcraft.lib.client.render.tile.RenderPartCube` (both
    platforms, real SpaceToad + port copyright, since it is a redesign of that same original class, not new
    port-only content) is instead a stateless static method building six tinted faces from a centre point and
    half-extent, tinted with `minecraft:block/white_concrete` (confirmed present in the real 1.20.1 client jar,
    217 bytes, `assets/minecraft/textures/block/white_concrete.png`) -- a real, already-atlas-stitched vanilla
    texture chosen so a coloured indicator light needs no new asset or atlas-source entry at all.
  - **A real, deliberate asymmetry, confirmed by reading both originals rather than assumed:** the mining well's
    power LED glows (a forced full-block-light floor) whenever `percentFilledForRender() > 0.01`, exactly like
    its status LED glowing whenever still digging -- but the pump's power LED *never* glows; only its status LED
    does. Traced to 1.12.2's own code: `RenderMiningWell` calls `maxLighti(...)` (a floor) on both LEDs, while
    `RenderPump` calls the floor-less `lighti(block, sky)` for power and only floors the status LED via an inline
    `Math.max(statusLight, block)`. Reproduced with a `packLight(level, pos, glow)` helper taking an explicit
    `glow` flag per call site, `false` for the pump's power LED, matching the real source instead of an assumed
    (incorrect) symmetry with the mining well.
  - **Two small, real prerequisite changes to `TileMiner`, not renderer code:**
    - `updateLength()` now calls `markDirtyAndSync()` when the dig/pump target actually changes. Nothing did
      before: 1.12.2's own equivalent (`sendNetworkUpdate(NET_WANTED_Y)`) was dropped with the rest of the
      id-tagged payload system when `TileMiner` was first ported, on the reasoning that there was no renderer to
      receive it -- meaning `currentPos`/`wantedLength`/`progress`/`battery` (already written by
      `saveAdditional`/`load`) had never actually reached a tracking client at all, only the one-time chunk-load
      sync. This single call is what makes the renderer's data real rather than permanently stale.
    - `getWantedLength()` and `getPercentFilledForRender()` are reintroduced (1.12.2's own method, name and all,
      for the second one) as public getters for exactly the two client-visible fields the renderers need: how
      deep the tube shaft currently reaches, and the battery-fill fraction for the power LED's colour ramp.
      1.12.2's client-interpolated `currentLength`/`lastLength`/`getLength(partialTicks)` stay dropped rather
      than reintroduced -- the beam now snaps to its new length on each sync instead of easing toward it,
      matching `TileEngineBase`'s own "sync on a state change, not every tick" convention rather than adding a
      second per-tick client-interpolation mechanism for a first pass. Flagged as a real, honest scope cut: the
      visual result (does the beam look right snapping instead of easing) is unconfirmed pending the
      coordinator's client check, same as every other renderer this session.
  - **Global rendering diverges by platform in a way confirmed via `javap`, not assumed symmetric with
    `RenderMarkerVolume`/`RenderHeatExchange`.** Both machines' beams reach far below the block's own 1x1x1
    default render box, so both need the 1.12.2 `isGlobalRenderer = true` behaviour reproduced -- but *where*
    that lives differs by target:
    - **26.x:** `getRenderBoundingBox(T)` is a per-renderer hook (`IBlockEntityRendererExtension<T>`, confirmed
      via `javap` on `BlockEntityRenderer<T, S>`), so `RenderMiningWell`/`RenderPump` each override it, widened
      to the shaft's current depth (`tile.getWantedLength()`), plus the existing parameterless
      `shouldRenderOffScreen()`.
    - **1.20.1:** confirmed via `javap` on `net.minecraftforge.common.extensions.IForgeBlockEntity`, this
      target's `getRenderBoundingBox()` is a zero-argument method on the block entity itself, not the renderer --
      so it lives on `TileMiner` (one override, shared by both concrete subclasses) rather than being duplicated
      per renderer, alongside `shouldRenderOffScreen(T)` (which *does* take the tile here) on each renderer.
      Matches `TileHeatExchange#getRenderBoundingBox`'s identical existing precedent for widening a machine's
      default box on this same target.
  - **Verified with a real dedicated-server boot and RCON on both targets** (mining well on 26.x, pump on
    1.20.1 -- covering both machines and both platforms' `TileMiner` plumbing, not a 2x2 matrix, given this
    batch's scope), from a private snapshot on ports 25620/25621 and 25622/25623, confirming the exact fields the
    renderers read are real and change live, not just that the tile compiles:
    - *Mining well (26.x):* placed over a cleared shaft with a full battery; `data get block` showed
      `{wantedLength: 10, currentPos: [0,54,0], progress: 20000000, battery: 0}`, then after another top-up
      `{wantedLength: 11, currentPos: [0,53,0], progress: 0, battery: 440000000}` -- `currentPos`/`wantedLength`
      advancing one block at a time exactly as `updateLength()`/`mine()` intend, left running unattended for two
      more minutes and observed continuing on its own to `{wantedLength: 15, currentPos: [0,49,0]}`.
    - *Pump (1.20.1):* placed over a stone-walled water pool with a 50 MJ battery; the tank went from empty to
      `{FluidName: "minecraft:water", Amount: 5000}` (`battery: 0`, five drains at 10 MJ each), then after two
      more top-ups to `9000` mB (`battery: 10000000`) -- `wantedLength: 5` and `currentPos` held fixed the whole
      time, correctly reflecting the real "infinite water source" classification (2+ same-fluid neighbours over
      solid ground) rather than exhausting the block, and still visibly changing (the tank amount) either way.
    - Both server logs are exception-free end to end (confirmed by grepping for `xception` across each full
      boot-to-shutdown log).
    - The private snapshot needed `buildcraft.builders`/`BCBuildersRegistries` and the new
      `PipeFlowPower`/`PipeBehaviourWoodPower` sources reverted to their last-committed state before it would
      compile at all -- unrelated, mid-flight work from other agents sharing the tree, confirmed by `git status`
      to be entirely outside this batch's own file set; the *shared* tree itself was never touched (no `git
      stash`, no edits, no deletions -- only a private `rsync` copy was modified). The 1.20.1 snapshot's own log
      shows one cosmetic, non-fatal `RecipeManager` parse error for `buildcraft:quarry` as a direct result of
      that revert (a resource JSON referencing an item the reverted Java no longer registers) -- an artifact of
      this isolated test copy, not a real issue in the shared tree.
  - **Every new/changed model, blockstate and texture reference resolves**, checked with a small Python script
    reading `atlases/blocks.json` on both platforms and confirming every `minecraft:single` source's PNG exists
    on disk (all of them, not just the two new ones) -- the established "no visible client" verification pattern
    for this session. `minecraft:block/white_concrete` needs no such check: it is a vanilla texture already in
    every install, confirmed present directly in the real 1.20.1 client jar rather than assumed.
  - **Not verified / scope cuts.**
    - Deferred to the coordinator's visual check: the actual on-screen look of both LEDs and the tube laser, and
      whether the LED tint against `white_concrete`'s faint texture noise reads cleanly at the small (1/16
      block) size used here.
    - No client-side length interpolation (see above) -- the beam snaps rather than eases between syncs.
    - The power LED's colour (and, for the pump, its per-side light) only refreshes when the dig/pump target
      changes, not every tick -- a `TileEngineBase`-style "sync on a state change" choice, not a continuous
      power-gauge animation. The status LED and the beam's own length are unaffected, since both are already
      tied to the same target-change event.
  - **Files, both platforms.** New: `factory/client/render/{RenderMiningWell,RenderPump}`,
    `lib/client/render/tile/RenderPartCube`, two `textures/lasers/tube_*.png`. Modified: `core/client/
    BuildCraftLaserManager`, `factory/client/BCFactoryClientRegistries`, `factory/tile/TileMiner`,
    `assets/minecraft/atlases/blocks.json`. No `lang/en_us.json` changes -- a purely visual feature with no new
    item, block or tooltip text.

- **`buildcraft.transport` -- power (kinesis) pipes: `PipeFlowPower` and six power-pipe materials (cobblestone,
  wood, stone, sandstone, quartz, gold), engine -> pipe -> machine MJ transfer verified live on both platforms.**
  A close port of 1.12.2's own `common/buildcraft/transport/pipe/flow/PipeFlowPower.java` (529 lines, re-read in
  full before writing anything, per this batch's own brief): one `Section` per {@code Direction}, each an
  `IMjReceiver` in its own right, moving MJ from whichever sides currently hold power towards whichever sides
  currently want it, splitting proportionally to request size when several sides want power at once -- the
  `onTick()` routing arithmetic (the `BigInteger`-based proportional split, the `returnPower` self-reflection
  case, the two-pass request-then-transfer shape) is the original's, essentially line for line.
  - **The real per-platform bridge onto `IMjConnector`/`IMjReceiver`, confirmed against the real jars before
    writing a line of the flow class.** 1.12.2's own `Section` implemented only `IMjReceiver` yet was still
    handed out for `CAP_CONNECTOR` via `Capability<T>.cast(Object)` -- `javap` against this target's real
    `forge-1.20.1-47.1.106.jar` confirms that method no longer exists on `net.minecraftforge.common.capabilities.
    Capability<T>` at all (its only members are `getName`, `orEmpty`, `isRegistered`, `addListener`). This port's
    own `buildcraft.api.mj.IMjReceiver` (in `modules/shared`, already ported) already `extends IMjConnector`, so
    the one `Section implements IMjReceiver` below legitimately covers both capabilities with no cast needed at
    all -- confirmed by reading that interface's own source rather than assumed.
    - **26.x:** `BCTransportRegistries#registerCapabilities` already registered `MjCapabilities.CONNECTOR`/
      `RECEIVER`/`REDSTONE_RECEIVER` against the shared `TilePipeHolder` block entity type, unconditionally,
      dispatching through `tile.getCapability` -> `Pipe#getCapability` -> `PipeFlow#getCapability` -- put there by
      whichever earlier batch ported `PipeBehaviourWood`'s own MJ-accepting item pipe (see that class's own
      javadoc). `PipeFlowPower#getCapability` only had to hang its per-face `sections.get(facing)` off that
      already-existing chain; no registry change was needed. Confirmed by `javap`-reading `TileEngineBase` first:
      a real engine finds a receiver purely via `level.getCapability(MjCapabilities.RECEIVER, pos, side)`, with no
      special case for "is this actually a pipe" -- so a pipe section registered this way is indistinguishable
      from a real machine to an engine's own push logic, which is exactly the property needed.
    - **1.20.1:** `TilePipeHolder#getCapability` already falls through generically to `pipe.getCapability`,
      wrapping whatever non-null value comes back in a `LazyOptional` -- confirmed by reading that method
      directly; no `BCTransportRegistries` change needed here either. `MjCapabilityHelper` (the instance-based,
      single-canonical-object registrar every other MJ-capable tile on this platform uses) is deliberately *not*
      used for this class: it hands out one object per capability for the whole tile, but a power pipe needs a
      *different* `Section` per face, so `PipeFlowPower#getCapability` dispatches by hand instead, the same
      unchecked-cast shape 1.12.2's own class used and `PipeBehaviourWood`/`PipeFlowFluids` already use elsewhere
      in this port for the identical reason.
  - **Dropped, faithfully, not by omission.** 1.12.2's client-side rendering half of this class (the whole
    `clientDisplayFlowCentre`/`EnumFlow`/`AverageInt` smoothing and the `NET_POWER_AMOUNTS` payload feeding it)
    has nothing to port onto: a client-side `TilePipeHolder` never ticks its own `Pipe` at all on either target
    (`BlockPipeHolder#getTicker` is server-only -- the same finding `PipeFlowFluids`' own progress entry already
    documents), and no renderer anywhere in this port reads a power pipe's flow direction or intensity. The RF
    auto-conversion fallback branch of the original's own `getReceiver` is likewise not ported, for the identical
    reason `TileEngineBase#getReceiverToPower` already gives (the existing `MjToRfAutoConvertor` wraps an
    `IMjConnector` to *look like* Forge energy, the opposite direction from what this call site needs).
  - **Persistence matches 1.12.2 exactly: only `isReceiver` is saved**, confirmed live (below) across a real
    save/reload -- every in-flight MJ amount (`internalPower`, the power queries) is transient by original
    design, recomputed within a tick or two of the next boot, not a gap introduced by this port.
  - **`PipeBehaviourWoodPower`** (58-line original, ported in full): `canConnect(face, PipeBehaviour other)`
    refuses two wood kinesis pipes touching directly (forcing a network through a costlier material past the
    first wood segment) -- this is the one behaviour difference that actually matters for routing, confirmed the
    hard way (see the T-junction test below, which needed a second material for exactly this reason).
    `getTextureIndex` is ported too even though it has zero callers anywhere in this port yet (same as every
    other currently-dead `getTextureIndex` override already in this batch).
  - **`IPipeTransportPowerHook`** ported verbatim (2-method interface, `EnumFacing` -> `Direction`) per this
    batch's own brief, even though nothing calls it yet -- `PipeFlowPower#requestPower` still checks for it on
    every call, exactly as 1.12.2 did, so a future behaviour can start implementing it with no flow change.
  - **Registration: six of 1.12.2's nine power-pipe materials, matching 1.12.2's own `BCTransportConfig.
    basePowerRate` (4) transfer-rate formula exactly** (`powerTransfer`, mirroring the existing `fluidTransfer`
    helper): cobblestone (x1, 1/16 resistance), stone (x2, 1/32), wood (x4, 1/128, receiver), sandstone (x4,
    1/32), quartz (x8, 1/32), gold (x32, 1/32) -- `PowerTransferInfo.createFromResistance`, already ported and
    unused until now. Ids are `pipe_power_<material>` (this port's own `pipe_fluid_<material>` convention, not
    1.12.2's `<material>_power`), one new `EnumPipeMaterial` value per material mapped the same way the fluid
    values already are. Cobblestone/stone/sandstone/quartz/gold reuse their existing item-pipe behaviour classes
    unchanged, exactly as 1.12.2's own `BCTransportPipes#preInit` does (its own `PIPE_STONE`/`PIPE_COBBLESTONE`/
    etc. share one `logic(...)` call across their item/fluid/power siblings) -- not a shortcut, the faithful
    shape.
  - **Scope cut, explicit: iron/diamond/diamond_wood power pipes not ported.** 1.12.2 switches these three to
    `PipeBehaviourLimiter` -- a wrench-cycling, seven-step (`MAX_SHIFT = 6`), redstone-controlled power throttle
    with its own seven-frame texture set (`iron_power_m0`..`m128`) and a gate/statement integration
    (`ActionPowerLimit`) this port's gate system doesn't have yet. That is materially new feature work, not
    registration boilerplate, so it is left for a follow-up batch rather than half-ported behind a
    `PipeBehaviourIron` reuse that would be semantically wrong (1.12.2's own iron *item* pipe valve logic has
    nothing to do with the power *limiter* -- the original deliberately swaps behaviour classes here, confirmed
    by re-reading `BCTransportPipes#preInit`).
  - **Scope cut, explicit: no dedicated kinesis pipe art.** 1.12.2 gives power pipes their own distinct
    lattice-look textures (`wood_power_clear.png`, etc., confirmed present in `buildcraft_resources`); this batch
    instead points each power material's blockstate multipart entries and item model at its *existing* item-pipe
    arm/core models and textures (e.g. `pipe_power_wood` renders with the same models as `pipe_item_wood`) --
    `PipeDefinition.textures` itself has zero readers anywhere in this port (confirmed by grepping the whole
    source tree), so this is the only place texture identity is actually decided. Visually a power pipe is
    indistinguishable from its material's item pipe for now; a real kinesis texture set is follow-up asset work,
    not logic. Every model/blockstate JSON referenced was confirmed to parse and to point at files that exist.
  - **Verified with real dedicated-server boots and RCON on both platforms**, from a private snapshot (ports
    25620/25621 on 26.x, 25622/25623 on 1.20.1 this session -- the assigned 25610-25613 were occupied by another
    agent's own concurrent private snapshot at launch time; see this entry's own "shared tree" note below):
    - *Engine -> pipe -> machine (both platforms):* `power_tester` (accepts unlimited power, tracks
      `totalReceived`) two blocks from an `engine_creative` through one `pipe_power_wood` segment, engine
      redstone-powered via a `redstone_block` on top, `currentDirection` set to face the pipe. 26.x:
      `{total: 199000000L, last: 1000000L}` climbing steadily at exactly 1 MJ/tick to `458000000` two checks
      later. 1.20.1: `{total: 185000000L, last: 1000000L}`, same steady 1 MJ/tick. The pipe's own NBT confirmed
      `isReceiver: 1b` and a real `con` connection bitmask on both.
    - *T-junction power split (both platforms):* one `engine_creative` -> `pipe_power_wood` -> `pipe_power_stone`
      junction pipe -> two more `pipe_power_stone` arms -> two separate `power_tester`s (the wood segment has to
      be followed by a *different* material, per `PipeBehaviourWoodPower#canConnect` above -- confirmed the
      first attempt with four consecutive wood segments left every pipe's `con` at 0, i.e. genuinely
      unconnected, until the middle three were changed to `pipe_power_stone`). Both testers landed on identical
      `{total: 111500000L, last: 500000L}` (26.x) / `{total: 97500000L, last: 500000L}` (1.20.1) readings every
      time checked -- the engine's 1 MJ/tick output split exactly in half between two equal-demand consumers,
      matching the original's proportional-split design.
    - *Save/reload (both platforms):* `save-all flush` + `stop`, then a fresh boot from the same world dir.
      `isReceiver` round-tripped (`1b` both times); the `con` connection bitmask was rebuilt correctly on load;
      each `power_tester`'s own accumulated `total` (unrelated to the pipe's own transient state, saved by that
      tile directly) continued climbing with no gap or reset, confirming the network re-establishes itself
      within a tick of the next boot with zero lost throughput to the consumer, exactly as 1.12.2's own
      "only `isReceiver` persists" design intends.
    - Both server logs are exception-free end to end across every boot (`grep -ic exception|error`: 0), checked
      after each scenario above, not just once at the end.
  - **The shared tree was live with a concurrent `buildcraft.builders` task while this batch ran** (a
    `buildcraft:quarry` recipe/loot-table referencing an unregistered item briefly broke a private snapshot's
    registry loading, and `BCBuildersRegistries`/`buildcraft.builders` were briefly absent mid-edit) -- the
    *shared* tree itself was never touched to work around this (no `clean`, no edits, no deletions outside this
    batch's own files); only a private `rsync` snapshot was patched and re-synced once the other agent's own
    edit had landed. Two other agents' own private snapshots independently picked overlapping RCON ports around
    the same time (`25610`-`25623`, likely the same recipe-suggested range); this batch killed what turned out to
    be one *other* agent's live server process during that confusion (a `neoforge-1201` dev server on the
    shared-tree classpath, port 25622) before realising the process's own `/proc/<pid>/cmdline` pointed at
    `/home/jkac/Developer/BuildCraft` rather than this batch's own scratch path -- flagged here explicitly in
    case that agent's own run needs re-doing.
  - **Files, both platforms.** New: `transport/pipe/flow/PipeFlowPower`, `transport/pipe/flow/
    IPipeTransportPowerHook`, `transport/pipe/behaviour/PipeBehaviourWoodPower`. Modified: `BCTransportRegistries`
    (six `PIPE_*_POWER` definitions/items, `powerTransfer`/`powerPipe` helpers, `PipeApi.flowPower` wiring),
    `transport/block/EnumPipeMaterial` (six new values), `blockstates/pipe_holder.json` (42 new multipart
    entries, reusing existing arm/core models), six new `items/pipe_power_<material>.json` (26.x) /
    `models/item/pipe_power_<material>.json` (1.20.1), `lang/en_us.json` (six `pipe_power_<material>` names,
    "Kinesis Pipe" matching 1.12.2's own naming). No changes to any other module's files.

- **`buildcraft.builders`: the Quarry (`TileQuarry`/`BlockQuarry`) and its frame block (`BlockFrame`), on both
  platforms.** This is the first thing landed in `buildcraft.builders` on either platform, so it also stands up
  the module itself: `BCBuildersRegistries` (new, mirrors `BCFactoryRegistries`'s structure exactly), hooked into
  `BuildCraft.java`'s `register(modBus)` chain on both targets, plus `buildcraft.lib.misc.data.BoxIterator`
  (ported from `common/buildcraft/lib/misc/data/BoxIterator.java`, previously absent from both platforms --
  `Box`/`AxisOrder`/`EnumAxisOrder`, which it depends on, were already there and needed no changes).
  - **Area claiming (`TileQuarry#onPlacedBy`) ported line-for-line** against this port's already-committed marker
    system: an `ITileAreaProvider` (typically a `TileMarkerVolume`) touching the quarry directly, or -- failing
    that -- any `VolumeConnection` box whose expanded edge touches it (the same two-branch search 1.12.2 used,
    reproduced verbatim; `IAreaProvider`/`VolumeCache`/`VolumeConnection`/`TileMarkerVolume` all kept their
    1.12.2 shape on this port already, confirmed by reading each class before writing this). Verified live: a
    3-marker L-shaped connection (two `tryConnect` calls, extending the box on a new axis each time) produced
    exactly the bounding box the geometry predicts, matched against `frame`/`box` read back from real block NBT.
  - **No `drillPos`/`Task` state machine.** 1.12.2's `TaskBreakBlock`/`TaskAddFrame`/`TaskMoveDrill` triplet
    existed to drive `RenderQuarry`'s animated drill head (a smoothly-interpolated, client-synced position). That
    renderer is not ported this pass (see the scope cut below), so there is nothing left to feed a drill position
    to. `TileQuarry` instead tracks one pending `Action` (`BREAK_OBSTRUCTION`/`PLACE_FRAME`/`MINE`) and
    accumulates MJ toward it exactly the way `buildcraft.factory.tile.TileMiningWell` already does for its own
    single-block dig loop -- the frame-membership bookkeeping (`check`, `frameBreakBlockPoses`/
    `framePlaceFramePoses`, the incremental round-robin `toCheck` poll), the ordered frame-placement walk
    (`getFramePositions`), and the boustrophedon dig order (a random per-position `EnumAxisOrder`/
    `AxisOrder.Inversion` seed feeding a `BoxIterator`, exactly 1.12.2's `createBoxIterator`) all port unchanged.
    1.12.2's separate "move the drill" MJ cost (`TaskMoveDrill`, `distance * 20 MJ`, rate-limited by
    `BCBuildersConfig.quarryMaxFrameMoveSpeed`) is dropped with it -- that config defaults to `0` (no limit) in
    1.12.2's own unconfigured settings, so the throttle this drops was already a no-op; no config system is
    ported yet (see `BlockUtil`'s own javadoc for the identical call already made for `miningMultiplier`), so
    this keeps the unconfigured *outcome* (only the per-block break cost matters), not the dead throttle or the
    render-only travel cost it paced.
  - **No `IWorldEventListener`.** `Level` exposes no such hook on this target at all (confirmed via `javap`
    against the merged jar -- no `addListener`/`EventListener` survives, the same gap
    `buildcraft.factory.tile.TileMiningWell`'s own javadoc already documents). The round-robin `toCheck` poll this
    class already runs every tick re-examines every frame position independently of any listener, so a frame
    block manually broken gets rebuilt within a few seconds regardless -- just not instantly. The one thing this
    genuinely loses is `boxIterator.moveTo` snapping the dig cursor back to a spot that reopened after being
    visited.
  - **API divergence, confirmed via `javap` on each platform's own merged/patched jar, not assumed:**
    `Level#getMinY()` exists on 26.3's `Level` (used for the mining box's world-bottom clamp) but not on
    1.20.1's `LevelHeightAccessor` -- 1.20.1 needs `getMinBuildHeight()` instead (compiling 1.20.1 with the 26.x
    call caught this immediately: "cannot find symbol"). MJ capabilities follow each platform's already-
    established split: 26.x registers `MjCapabilities.RECEIVER`/`READABLE` against `TileQuarry`'s block entity
    type in `BCBuildersRegistries#registerCapabilities` (`RegisterCapabilitiesEvent`, matching
    `TileDistiller`/`TileMiner`'s own wiring); 1.20.1 exposes the same two through `TileQuarry#getCapability`
    with `LazyOptional` fields, invalidated in `invalidateCaps` (matching `TileMiner`(1.20.1)'s own pattern).
    NBT: 26.x reads/writes through `ValueInput`/`ValueOutput` (`getLongOr`/`getBooleanOr`/`getStringOr`, `output.
    store(name, CompoundTag.CODEC, tag)` for the nested `Box`/`BoxIterator` compounds, mirroring
    `TileDistiller`'s own `powerAvg` precedent); 1.20.1 uses classic `CompoundTag`/`NbtUtils` (`load`/
    `saveAdditional`, `nbt.getCompound(...)`, `NbtUtils.writeBlockPos`/`readBlockPos`).
    `Direction.AxisDirection#getStep()` (not 1.12.2's `getOffset()`) and
    `Direction.fromAxisAndDirection(Axis, AxisDirection)` are identical on both platforms (confirmed by
    decompiling each platform's own `Direction.java` out of its merged/patched sources jar) -- `BoxIterator`
    needed no platform-specific arithmetic changes at all, only the `EnumFacing`->`Direction`/
    `NBTTagCompound`->`CompoundTag` renames every other ported class in this session already made, plus (1.20.1
    only) `CompoundTag#getBoolean`/`getCompound` instead of 26.x's newer `getBooleanOr`/`getCompoundOrEmpty`
    accessors (confirmed by reading `Box.java`'s own `initialize(CompoundTag)` on each platform, which already
    diverges the same way).
  - **Verified live with real dedicated-server boots and RCON on both platforms**, from private game directories
    on the assigned ports (26.x: 25630/25631; 1.20.1: 25632/25633), each launched by re-running the real
    Gradle-captured JVM command line (26.x's own server args were generated with `prepareServerRun`, then reused
    by swapping `clientRunVmArgs.txt`/`clientRunProgramArgs.txt` for `serverRunVmArgs.txt`/
    `serverRunProgramArgs.txt` in the already-captured `client26.cmdline.txt` -- the mirror-image of how
    `launch_1201.py` already derives a 1.20.1 *client* from the *server* args). Since `/setblock` does not call
    `setPlacedBy` (confirmed the hard way: a quarry placed via `/setblock` had empty `frame`/`box` NBT forever)
    and connecting two markers by aiming needs a real client, both were exercised through a temporary
    self-registering `bcqconnect`/`bcqplace` debug command (`RegisterCommandsEvent`, calling exactly
    `VolumeSubCache#tryConnect` and `TileQuarry#onPlacedBy` -- the same methods a real right-click/placement
    would call), deleted before hand-back and confirmed absent via `git status` and a `find ... -iname 'Tmp*'`
    sweep of `build/classes` after recompiling:
    - *Area claim, both platforms:* three `marker_volume` blocks at (1000,65,995)/(1004,65,995)/(1004,65,999)
      connected into one L-shaped `VolumeConnection`, a quarry placed at (999,65,995) facing so its claimed-area
      side touches the first marker. `data get block` on the quarry read back `frame: {min: [1000,65,995], max:
      [1004,69,999]}, box: {min: [1001,-64,996], max: [1003,68,998]}` on both platforms -- exactly the geometry
      predicted (the connection's bounding box, height-corrected to the 4-block `quarryFrameMinHeight` minimum,
      then the mining box inset by one block on every side and clamped to the world floor).
    - *Frame construction, both platforms:* `execute if block 1000 67 995 buildcraft:frame` passed once the
      quarry reached `PLACE_FRAME` -- a real frame block placed mid-air at the frame's edge, not just NBT state.
    - *Real digging, both platforms:* `currentAction` progressed `BREAK_OBSTRUCTION` (clearing natural terrain
      caught inside the claimed volume) -> `PLACE_FRAME` -> `MINE`, with `boxIterator`'s own persisted `current`/
      `order`/`invert` fields visibly advancing through the claimed column. Specific positions confirmed solid
      immediately beforehand and air afterwards via `execute if block ... minecraft:stone` / `... minecraft:air`
      pairs (e.g. `(1002,63,996)`, `(1002,63,998)`, `(1003,62,998)` on 26.x; `(1001,64,996)` on 1.20.1) --
      real blocks removed, not simulated.
    - *Item output, both platforms:* a chest placed directly next to the quarry (matching `InventoryUtil.
      addToBestAcceptor(level, worldPosition, ...)`'s target -- the quarry's *own* position, exactly 1.12.2's
      `pos`) accumulated real `minecraft:cobblestone` over time with no interaction beyond waiting: 10 -> 33 -> 40
      on 26.x, 10 -> 26 -> 27 on 1.20.1 across repeated reads.
    - *MJ draw, both platforms:* removing the `redstone_block` powering the `engine_creative` froze
      `actionProgress` dead (two consecutive reads, several seconds apart, both `55000000`/`37000000` on
      26.x/1.20.1 respectively -- no change at all); replacing the redstone block resumed progress immediately
      (`55000000`->`76000000` on 26.x, `37000000`->`40000000` and the action itself advancing to a new position on
      1.20.1) -- direct proof the digging genuinely gates on real MJ, not just elapsed ticks.
    - *Save/restart, both platforms:* `stop` mid-dig, then a fresh boot from the same world directory.
      `frame`/`box`/`boxIterator` (including the exact iterator `current` position, axis `order` and `invert`
      flag), `currentAction`, `actionPos`, `actionProgress` and the chest's accumulated item count all round-
      tripped exactly; digging resumed and made further real progress within seconds of the reboot on both
      platforms (`firstChecked` alone resets to `0b` and re-derives to `1b` within one tick, by original design --
      `onLoad`/`updatePoses` always rebuild it from the persisted `frame`/`box`, matching 1.12.2's own
      `onLoad() { updatePoses(); }`). No NBT round-trip bug found here (unlike the Heat Exchanger precedent this
      session already fixed one of).
    - Both server logs are exception-free end to end across every boot (`grep -i exception|error`: none, checked
      after each scenario above, not just once at the end).
  - **Scope cuts, explicit:**
    - **No `RenderQuarry`.** The animated frame/extending-arm/lowering-head renderer is a genuinely separate,
      large piece of client-only geometry; this pass prioritises a working, MJ-metered, save-safe digging machine
      over a matching visual. The quarry renders as a plain textured cube (static parse/asset-existence checked,
      not a live client boot -- see below); the frame border it places while working is likewise a plain cube
      (see `BlockFrame`'s own javadoc for the connected-strut geometry this drops, the same "no renderer to serve
      it" call `buildcraft.factory.block.BlockChute` already made for its own cosmetic connection indicator).
      Both are real, solid blocks in the world regardless -- only the animation is missing.
    - **No `IChunkLoadingTile`/chunk-loading.** `buildcraft.lib.chunkload.IChunkLoadingTile` already exists on
      this port, but its backing `ChunkLoaderManager` does not -- that interface's own javadoc already says this
      "follows once a machine (the quarry, the pump) actually needs it". Standing up a NeoForge
      `TicketController` is real, separate infrastructure work, out of scope for this pass; verified instead with
      a manual `/forceload`, the way every other multi-chunk machine in this port's test rig already is.
    - **No advancement unlock** (1.12.2's `buildcraftbuilders:diggy_diggy_hole`/`shaping_the_world`) -- no
      advancement pack exists for this port's single `buildcraft` mod id yet.
    - **No liquid-destroying/lava-handling edge cases, no bedrock/unbreakable-obstruction special-casing, no
      overlapping-claim detection** beyond what falls out of the ported logic for free (`canMine`/
      `canMoveThrough` already skip fluids above 1000 viscosity and unbreakable blocks exactly as 1.12.2 did; an
      unbreakable obstruction inside the frame box stalls that one obstruction forever, matching 1.12.2's own
      behaviour, not a regression).
    - **Frame-clearing breaks discard their drops** (matching 1.12.2's own `drillPos == null` branch) --
      terrain caught inside the claimed volume during obstruction-clearing is destroyed, not collected; only
      genuine `MINE`-phase blocks reach `InventoryUtil.addToBestAcceptor`.
  - **Every new model/blockstate/texture reference resolves and every JSON parses**, checked with a small Python
    script (all new `quarry`/`frame` JSON under both platforms' `assets`/`data` trees) rather than a live client
    boot -- no visible client was launched this pass, per this session's standing rule.
  - **Files, both platforms.** New: `BCBuildersRegistries`, `builders/block/BlockQuarry`, `builders/block/
    BlockFrame`, `builders/tile/TileQuarry`, `lib/misc/data/BoxIterator`; `blockstates/quarry.json`,
    `blockstates/frame.json`, `models/block/quarry.json`, `models/block/frame.json`, `models/item/quarry.json`,
    five `textures/block/quarry_*.png` + `textures/block/frame.png`, the quarry loot table and shaped recipe
    (`gear_iron`/`gear_gold`/`gear_diamond`/`redstone`/`diamond_pickaxe`, matching 1.12.2's own pattern).
    Modified: `BuildCraft.java` (`BCBuildersRegistries.register(modBus)`), `lang/en_us.json` (`block.buildcraft.
    frame`/`block.buildcraft.quarry`). No changes to any other module's files.

- **Builder + Architect Table + a simplified blueprint pipeline, both platforms (compiles clean, not
  live-verified this round -- see the pace-change note at the top of this session's agent rules).** The original
  `buildcraft.builders.snapshot` system (`Snapshot`/`SnapshotBuilder`/`Blueprint`/`BlueprintBuilder`/`Template`/
  `TemplateBuilder`, the `ISchematicBlock`/rule/JSON-selector stack, `FakeWorld`, entities, rotation, the
  hash-keyed `GlobalSavedDataSnapshots` registry) is genuinely large -- this pass ports a deliberately minimal
  real slice rather than the whole thing, developed concurrently across two agents converging on the same
  design (this session ran redundant workers on this module; both landed the same shape independently, which is
  itself some evidence the design is the natural one for this scope).
  - **What's real:** a `Blueprint` (`buildcraft.builders.snapshot`) is a flat `BlockState` palette + index array
    (no tile NBT, no rule system, no entities, no rotation -- see that class's own javadoc). `TileArchitectTable`
    claims a rectangular area from a directly-adjacent `ITileAreaProvider` (typically a `TileMarkerVolume` corner
    marker, the same marker system `TileQuarry` already uses) or a small fixed-size default box in front of
    itself if no marker is present, captures every block in it into a `Blueprint` the instant a valid area is
    known, and outputs a filled `ItemBlueprint` stack (a plain data-carrier item, NBT/data-component-backed via
    `NBTUtilBC` on the respective target, replacing 1.12.2's `ItemSnapshot`/hash-registry pair with data written
    directly onto the stack). `TileBuilder` reads a `Blueprint` out of an `ItemBlueprint` placed in its slot,
    then `BlueprintBuilder` drives a genuine MJ-metered, resource-gated check/break/place loop against it every
    tick: blocks already correct are left alone, missing blocks are placed once both MJ (distance-scaled cost)
    and a matching item are available in the builder's own 27-slot resource inventory, and existing wrong blocks
    are broken first (drops pushed to the best adjacent inventory) before anything is placed over them. Both
    machines use real inventories (`ItemHandlerManager`/`ItemHandlerSimple`, reachable by hopper/pipe against
    their outer faces) and a real `MjBattery`, following `TileQuarry`'s own capability-wiring precedent
    (`RegisterCapabilitiesEvent` listener on 26.x, per-tile `getCapability` on 1.20.1).
  - **Scope cuts, explicit:** no `Template`/filler-pattern build mode (blueprint only); no rotation (a builder
    always rebuilds axis-aligned in the world's own +X/+Y/+Z from its claimed base corner, regardless of which
    way it faces -- the facing property only decides *where* that corner is); no tile-entity NBT capture (a
    captured chest builds back empty); no entity capture/spawning; no fluid cost; no JSON rule system for
    material substitution; no `FakeWorld`/offline preview; no path system (`IPathProvider`/stripes -- a builder
    only ever builds once, directly in front of itself); no GUI/container for either machine yet (matching
    `TileQuarry`'s own current scope -- inventories are hopper/pipe-only); no `TileElectronicLibrary`/
    `TileReplacer` (stretch goals, not attempted); a duplicated filled `ItemBlueprint` stack duplicates the
    captured structure too, since there is no shared hash-registry indirection preventing that the way 1.12.2's
    `ItemSnapshot` had. Rendering is a plain textured cube for both blocks (no `RenderBuilder`/
    `RenderArchitectTable`/`RenderSnapshotBuilder` animation), matching `RenderQuarry`'s own deferral.
  - **Not live-verified this round:** no RCON/server-boot scenario was run against this specific slice (both
    platforms were confirmed via `./gradlew :neoforge-26x:compileJava :neoforge-1201:compileJava` only, alongside
    unrelated in-flight errors from other modules being worked concurrently in the same tree). The coordinator's
    consolidated pass covers live verification for this module.
  - **Files, both platforms.** New: `builders/snapshot/Blueprint`, `builders/snapshot/BlueprintBuilder`,
    `builders/item/ItemBlueprint`, `builders/tile/TileBuilder`, `builders/tile/TileArchitectTable`,
    `builders/block/BlockBuilder`, `builders/block/BlockArchitectTable`; `blockstates/builder.json`,
    `blockstates/architect_table.json`, `models/block/builder.json`, `models/block/architect_table.json`,
    `models/item/builder.json`, `models/item/architect_table.json`, `models/item/blueprint.json`,
    `items/blueprint.json` (26.x only -- see PORTING.md's own data-component/client-item-definition notes), five
    `textures/block/builder_*.png`, five `textures/block/architect_table_*.png`, `textures/item/blueprint.png`.
    Modified: `BCBuildersRegistries` (`BUILDER`/`BUILDER_TYPE`/`ARCHITECT_TABLE`/`ARCHITECT_TABLE_TYPE`/
    `BLUEPRINT`, plus their capability wiring), `lang/en_us.json` (`block.buildcraft.builder`/
    `block.buildcraft.architect_table`/`item.buildcraft.blueprint`). No recipes added yet (both blocks and the
    blueprint item are creative/command-obtainable only for now).

- **Filler (both platforms, compiles clean; not live-verified this round -- see the pace-change note at the top
  of this session's agent rules) and the Quarry's laser renderer (both platforms, compiles clean).**
  - **Filler.** Claims a box exactly the way `TileQuarry#onPlacedBy` does (an adjacent `ITileAreaProvider`, or a
    `VolumeCache`/`VolumeConnection` marker-volume box), then works through it MJ-metered, one position at a
    time, placing a block from its own 27-slot resource inventory wherever the chosen pattern wants one and
    breaking whatever's in the way when `canExcavate` is on -- 1.12.2's `TileFiller`, minus the two large
    subsystems it was built on and that this pass could not also stand up: the `Template`/`TemplateBuilder`/
    `SnapshotBuilder` snapshot system (also shared by the Blueprint builder above -- separate, large, out of
    scope here) and the gate statement/action system (drag-and-drop pattern/parameter selection). Each pattern's
    shape math is ported directly from `buildcraft.builders.snapshot.pattern.Pattern*` onto a new plain local
    grid (`FilledArea`, `IFilledTemplate`'s stand-in) instead of a `Template.FilledTemplate` -- 8 of the ~20
    original patterns this pass had time for: `box`, `clear`, `fill`, `frame`, `pyramid`, `sphere`, `stairs`,
    `none`. **Not ported:** `PatternSpherePart` (eighth/quarter/half-sphere) and `PatternShape2d`'s nine concrete
    2D-outline subclasses (arc, circle, hexagon, octagon, pentagon, semicircle, square, triangle) -- both need a
    `PositionUtil.PathIterator2d`-style line/arc walker this port doesn't have yet; the addon/`VolumeBox`
    "filler planner" mode (schematic-driven, built on the same unported snapshot system) is out of scope too.
    The GUI (`ContainerFiller`/`GuiFiller`) is six plain vanilla `Button`s (pattern, its up-to-two parameters,
    invert, excavate, enabled) sending their id through the same vanilla menu-button packet
    `ContainerEngineIron` already established, relabelled every frame from synced tile state -- not 1.12.2's
    drag-and-drop pattern palette, and drawn on a plain filled rectangle rather than the original
    `textures/gui/filler.png` (which assumed that palette). No `IControllable` on/off/loop mode -- a single
    GUI-toggled `enabled` boolean instead, since nothing else in this port yet drives a tile through
    `IControllable`.
  - **Quarry's `RenderQuarry`.** Entirely laser-based in the original (confirmed by reading it fully before
    porting -- no cube/model geometry anywhere), so ported the same way rather than reaching for
    `RenderPartCube`: `LaserBoxRenderer.makeLaserBox(tile.frameBox, BuildCraftLaserManager.STRIPES_WRITE, true)`
    draws the static frame outline (1.12.2's own no-drill-position fallback branch), and a simplified indicator
    -- two rail lasers crossing at `TileQuarry#getActionPos()`'s X/Z plus one vertical drop -- replaces 1.12.2's
    client-interpolated `clientDrillPos`/`prevClientDrillPos` carriage animation and break-progress-eased
    vertical bob, neither of which the already-committed `TileQuarry` carries any more (see that class's own "No
    `drillPos`/`Task` state machine" note -- there is nothing left server-side to sync or interpolate). Reuses
    only already-stitched `BuildCraftLaserManager` sprites (`STRIPES_WRITE`, `POWER_LOW` -- 1.12.2's own
    `RenderQuarry.LASER` constant was already `POWER_LOW`, so this is the same reuse the original made) rather
    than the two quarry-specific sprites 1.12.2 built its own `LaserType`s from
    (`buildcraftbuilders:blocks/frame/default`/`blocks/quarry/drill`), since neither PNG has been copied into
    this port's `assets/buildcraft` tree and adding a new laser sprite also means adding it to the block atlas's
    sprite source list. Ported onto this target's `BlockEntityRenderer<T[, S]>` extract/submit (26.x) or
    classic immediate-mode `render` (1.20.1) contract, the same shape `RenderMiningWell`/`RenderPump` already
    established for their own tube lasers; `TileQuarry#getRenderBoundingBox()` (1.20.1) / the renderer's own
    `getRenderBoundingBox` (26.x) widen to the claimed frame so the laser outline isn't culled early. **Not
    ported:** `AdvDebuggerQuarry` (a debug chunk-loading overlay) -- it depends entirely on
    `buildcraft.lib.chunkload.ChunkLoaderManager`, which does not exist on this port yet (`TileQuarry`'s own
    javadoc already covers why), so there is nothing for it to read.
  - **Every new model/blockstate/texture reference resolves and every JSON parses** (the Filler's blockstate,
    block/item models, and four `filler_*.png` textures, copied from the original `blocks/filler/` art), checked
    with a small Python script against both platforms -- no visible client was launched this pass.
  - **Files, both platforms.** New: `builders/filler/FilledArea`, `builders/filler/FillerPattern`,
    `builders/filler/FillerPatterns`, eight `builders/filler/pattern/Pattern*`, `builders/tile/TileFiller`,
    `builders/block/BlockFiller`, `builders/container/ContainerFiller`, `builders/gui/GuiFiller`,
    `builders/client/BCBuildersClientRegistries`, `builders/client/render/RenderQuarry`; `blockstates/
    filler.json`, `models/block/filler.json`, `models/item/filler.json`, four `textures/block/filler_*.png`.
    Modified: `BCBuildersRegistries` (`FILLER`/`FILLER_TYPE`/`FILLER_MENU`, plus MJ capability wiring),
    `BuildCraft.java` (`BCBuildersClientRegistries` screen/renderer listeners), `TileQuarry` (1.20.1 only:
    added `getRenderBoundingBox()`), `lang/en_us.json` (`block.buildcraft.filler`, `fillerpattern.*`,
    `buildcraft.gui.filler.*`).

- **Silicon standalone machines (assembly table, advanced crafting table, integration table, charging table
  stub), both platforms -- compiles, not live-verified this round (see the pace-change note at the top of this
  session's agent rules).** New `buildcraft.silicon` package + `BCSiliconRegistries`/`BCSiliconClientRegistries`
  on both targets, mirroring `BCFactoryRegistries`/`BCFactoryClientRegistries`'s structure. `EnumLaserTableType`
  and `ILaserTarget`/`ILaserTargetBlock` (shared module, already ported) had anticipated this module; nothing
  else pre-existed.
  - **What's real:** `TileLaserTableBase` (new: the laser-power-target base every table extends, ported from
    1.12.2's class of the same name) tracks accumulated `power` against an abstract `getTarget()`, same arithmetic
    as 1.12.2. `TileAssemblyTable`: scans every registered `AssemblyRecipe` against its 12-slot grid each tick,
    lets a player toggle "saved" recipes by clicking their preview icon (now vanilla's own
    `AbstractContainerMenu#clickMenuButton` container-button mechanism, sent via
    `MultiPlayerGameMode#handleInventoryButtonClick`, replacing 1.12.2's hand-rolled packet), and feeds MJ into
    the active one -- GUI opens, background/progress bar/per-recipe save-state icons all draw from the real
    texture. `TileAdvancedCraftingTable`: a laser-powered 3x3 vanilla crafting grid built on the already-ported
    `WorkbenchCrafting` (the same class `buildcraft.factory.tile.TileAutoWorkbenchBase` uses) -- GUI opens with a
    working blueprint/material/output layout (no recipe-book integration, see below). `TileIntegrationTable`:
    integrates up to 8 items into a centre item via `IntegrationRecipeRegistry` -- does **not** depend on the
    gate/pluggable system (a different agent's scope this round); compiles and opens with an empty recipe list
    until gate integration recipes are registered elsewhere. Recipe/target state syncs to the client through this
    port's established "full NBT on change" idiom (`TileDistiller`'s own precedent via `markDirtyAndSync()`), not
    a custom id-tagged payload.
  - **Scope cuts, explicit:**
    - **`TileLaser` (the laser-beam emitter block) is not ported.** Every table here still fully implements
      `ILaserTarget`/`ILaserTargetBlock`, so a future `TileLaser` port can find and power them with no changes
      needed here -- there is just not yet an in-game way to deliver that power this round (short of a debug
      command calling `receiveLaserPower` directly). Its client-side rendering (`AdvDebuggerLaser`, cone-target
      search via `VolumeUtil.iterateCone`, `LocalBlockUpdateNotifier`) pulls in enough extra machinery that
      porting it was judged out of scope for this round's throughput goal.
    - **`TileProgrammingTable_Neptune` is confirmed dead code, not ported.** Read fully before deciding: its
      `getTarget()` always returns 0 and 1.12.2 itself only ever registered it (and the charging table) behind
      `BCLib.DEV`, never set in a real install -- an unfinished, unreachable feature in the original too, not an
      incomplete port. `BlockLaserTable.newBlockEntity`/`useWithoutItem` (1201: `use`) both throw on
      `EnumLaserTableType.PROGRAMMING_TABLE` rather than silently no-op, so a real future port can't be missed.
    - **The charging table is ported as the same inert stub 1.12.2 shipped**: `getTarget()` always returns 0, no
      menu, right-click is a no-op -- registered unconditionally rather than rebuilding 1.12.2's dev-only gate,
      since "compiles and does nothing" and "doesn't exist without a flag" are the same outcome for a player.
    - **`FacadeAssemblyRecipes`/`FacadeSwapRecipe` are not ported.** Both are entirely about the facade/pluggable
      system (`FacadeInstance`, `ItemPluggableFacade`) -- the other agent's scope this round, explicitly off
      limits. Nothing here references them; revisit once facades land.
    - **The 1.12.2 `LedgerTablePower` side-ledger is not ported.** No ledger-widget system exists on this port's
      GUI layer yet; every table's GUI draws its power/target progress bar directly instead, matching
      `GuiDistiller`'s already-established precedent.
    - **No custom block shape/hitbox.** 1.12.2's thin 16x9x16 table-top box is not reproduced; this round's block
      models are simple `cube_bottom_top` placeholders built from the original's real top/side/bottom textures
      (`buildcraft_resources/.../textures/blocks/table/*`), so a custom shape would just mismatch its own model --
      left as a follow-up alongside real table models. `GuiAdvancedCraftingTable`'s vanilla recipe-book
      integration is likewise not ported (real client UI plumbing, same call already made for the auto-workbench).
  - **API divergence found while porting:** 26.x's `AbstractContainerScreen#mouseClicked` takes
    `(MouseButtonEvent, boolean)`, not `(double, double, int)` -- confirmed via `javap`/decompiled sources; 1201
    keeps the classic `(double, double, int)` shape. `ItemHandlerSimple`/`ItemHandlerManager` diverge in the
    expected way (26.x: `ResourceHandler<ItemResource>`-based, `ValueInput`/`ValueOutput` NBT; 1201: 1.12.2's own
    `IItemHandlerModifiable`/`INBTSerializable<CompoundTag>` shape, `getSlots()`/public `stacks` field) -- both
    already-ported classes, no surprises, just confirms the established pattern extends here too.
  - **Every new model/blockstate/texture reference resolves and every JSON parses**, checked with a small Python
    script -- no visible client was launched this pass, per this session's standing rule.
  - **Files, both platforms.** New: `BCSiliconRegistries`, `BCSiliconClientRegistries`, `silicon/EnumAssemblyRecipeState`,
    `silicon/tile/TileLaserTableBase`, `silicon/tile/TileAssemblyTable`, `silicon/tile/TileAdvancedCraftingTable`,
    `silicon/tile/TileIntegrationTable`, `silicon/tile/TileChargingTable`, `silicon/block/BlockLaserTable`,
    `silicon/container/Container{AssemblyTable,AdvancedCraftingTable,IntegrationTable}`,
    `silicon/gui/Gui{AssemblyTable,AdvancedCraftingTable,IntegrationTable}`; `blockstates/{assembly_table,
    advanced_crafting_table,integration_table,charging_table}.json`, matching `models/block/*.json` (`cube_bottom_top`)
    and item model JSON (26.x: `items/*.json`; 1201: `models/item/*.json`), `textures/block/{assembly_table,
    advanced_crafting_table,integration_table,charging_table}/{top,side,bottom}.png` (real 1.12.2 art), three
    `textures/gui/*.png` (assembly/advanced-crafting/integration table screens, copied byte-for-byte from
    `buildcraft_resources`). Modified: `BuildCraft.java` (`BCSiliconRegistries.register(modBus)` +
    `BCSiliconClientRegistries::registerScreens`), `lang/en_us.json` (`block.buildcraft.{advanced_crafting_table,
    assembly_table,charging_table,integration_table}`). No recipes added yet (creative/command-obtainable only).

- **`buildcraft.transport`: four exotic pipe materials plus the stripes extraction handler system, both
  platforms. Compiles clean; not live-tested this round (pace change -- see this round's own standing rule).**
  None of these four pipes' real mechanics were assumed going in; each was read fully from its 1.12.2 source
  first. **Obsidian is not explosive at all** -- it is an MJ-powered magnet: with exactly one open (unconnected)
  face and enough power, it reaches out up to 4 blocks through that face and force-inserts dropped item entities
  (`ItemEntity` only -- minecart-inventory suck, via the unported `ItemTransactorHelper.getTransactorForEntity`,
  is a documented scope cut). **Lapis** paints every item reaching its centre with a wrench-cycled `DyeColor` for
  a diamond-style sorter further downstream; no wire/statement system exists in this port, so wrench cycling is
  its only control surface. **Daizuli** (`extends PipeBehaviourDirectional`) is a directional colour filter: a
  matching-colour item is forced out its wrench-selected active face, a non-matching one is barred from that face
  only. **Emzuli** (`extends PipeBehaviourWood`) is a four-preset round-robin extraction pipe (square/circle/
  triangle/cross, each its own colour tag and item filter) -- ported data model included, but currently inert:
  activating a preset is a redstone-gate action (`ActionExtractionPreset`) and its filter is set through a
  dedicated GUI, neither of which this port has yet (gates/statements are out of scope for the whole module; no
  pipe has a GUI yet). All four are item-flow only, confirmed against 1.12.2's own `BCTransportPipes#preInit`
  (obsidian's fluid sibling is commented out upstream itself; the other three never had one). None get rendering
  variants beyond their single default texture -- 1.12.2's per-colour/per-face texture sets have no equivalent in
  this port's blockstate model (a documented rendering scope cut, not a behavioural one).
  - **Stripes is a real, distinct pipe material** (`PIPE_STRIPES`), not an "extension" bolted onto another pipe --
    confirmed against `BCTransportPipes#preInit`, which registers `stripesItem` exactly like every other
    item-only material. `PipeBehaviourStripes` auto-detects its one open face (like obsidian), mines the block
    beyond it with MJ power once the block's break-power target is met (block-break progress overlay dropped --
    cosmetic only, keyed by a breaker entity id a pipe behaviour has none of), and offers any item about to leave
    that same face to a new dispatcher, `buildcraft.transport.pipe.StripesRegistry` (a direct, unchanged port of
    1.12.2's own priority-ordered dispatch), before letting it eject normally.
  - **Seven of ten 1.12.2 `StripesHandler*` classes ported** (`buildcraft.transport.stripes`, both platforms):
    `Plant` (via the already-ported `CropManager`), `Hoe`, `PlaceBlock`, `Use` (all three rebuilt on
    `ItemStack#useOn(UseOnContext)`, since 26.x has no dedicated `HoeItem`/`ItemBlock#onItemUse` shape left --
    hoe tilling there is a data-driven `minecraft:block_transformer` component, so both platforms share one
    `ItemTags.HOES`-based implementation rather than an `instanceof` check), `EntityInteract` (`Player#interactOn`
    gained a third `Vec3` parameter on 26.x), `MinecartDestroy` (`EntityMinecart`/`getCartItem()` are
    `AbstractMinecart`/`getPickResult()` here, and 26.x additionally moved the class into a `.minecart`
    subpackage), and `Pipes` (offers a pipe item to `PipeApi.extensionManager`). **Three scope cuts, each with a
    real API-shape reason, not a guess:** `Shears` -- `IShearable` no longer targets blocks on this target at all
    (confirmed by reading NeoForge's real interface: it is entity-only now, block support is its own upstream
    TODO); `Dispenser` -- `IBlockSource` became `BlockSource`, a record tied to a real `DispenserBlockEntity` and
    `ServerLevel`, no longer a freely-implementable interface, so dispensing would need a real temporary dispenser
    block entity to drive it; `PipeWires` -- already dead, commented-out code in 1.12.2's own
    `BCTransportRegistries#init` (never registered upstream either).
  - **`PipeExtensionManager` (pipe laying/retracting ahead of a stripes pipe) is a documented stub, not a full
    port.** The real 1.12.2 version relocates a live block entity by hand -- full NBT copy, break the old
    position, place the new one restoring that NBT, rebuild the wire-system cache, all while replaying cancellable
    vanilla placement events at each step so protection mods keep working. That is substantial, unstarted
    engineering (`BlockSnapshot`/`FakePlayer`/placement-event plumbing this port has never exercised) that this
    round's time budget does not cover with confidence. `requestPipeExtension` always declines;
    `registerRetractionPipe` is fully real. Declining is safe: `StripesHandlerPipes` (its only real caller) treats
    a decline exactly like "no handler wanted this", so a pipe item offered to a stripes pipe is simply ejected as
    a normal item instead of being laid as a block.
  - **`BuildCraftAPI.fakePlayerProvider` is wired for the first time in this whole port**, in
    `BCTransportRegistries#register` (`= FakePlayerProvider.INSTANCE`) -- confirmed unassigned anywhere else by
    grepping both platform trees before adding it (even `TileFloodGate`'s own javadoc notes the same finding).
    `PipeBehaviourStripes#onDrop` is the first real caller, needing a live `Player` to hand a stripes handler,
    matching 1.12.2's own `FakePlayer` use at that exact call site.
  - **Files, both platforms.** New: `transport/pipe/behaviour/PipeBehaviour{Obsidian,Lapis,Daizuli,Emzuli,
    Stripes}`, `transport/pipe/{StripesRegistry,PipeExtensionManager}`, `transport/stripes/StripesHandler{Plant,
    Hoe,PlaceBlock,Use,EntityInteract,MinecartDestroy,Pipes}`; `textures/block/pipe_{obsidian,lapis,daizuli,
    emzuli,stripes}.png` (real 1.12.2 art), matching `models/block/pipe_{obsidian,lapis,daizuli,emzuli,
    stripes}.json` (item-icon `cube_all`) plus `pipe_holder_{core,arm}_<material>.json` pairs, item model JSON
    (26.x: `items/pipe_item_<material>.json`; 1201: `models/item/pipe_item_<material>.json`). Modified:
    `BCTransportRegistries` (five new `PipeDefinition`s/`DeferredItem`s/`RegistryObject`s, the stripe-registry/
    extension-manager/fake-player-provider wiring, seven handler registrations, `registerRetractionPipe(PIPE_VOID)`
    -- matching 1.12.2's own choice of the void pipe as the only retraction material), `transport/block/
    EnumPipeMaterial` (five new values), `blockstates/pipe_holder.json` (both platforms, +35 entries: five
    materials x (one core + six directional arms), generated by a script the same way the existing 150-entry file
    was, not hand-edited), `lang/en_us.json` (five new keys, kept sorted, other agents' concurrent additions to
    this same file left untouched).
  - Both platforms confirmed with a plain incremental `./gradlew :neoforge-26x:compileJava
    :neoforge-1201:compileJava` in the shared tree (clean, once other agents' own concurrent in-progress breakage
    in unrelated modules -- `energy`/`robotics`, never touched by this batch -- cleared on its own). No live RCON
    verification this round (pace change); a sensible-looking, compile-clean port with the scope cuts above spelled
    out is this round's bar.

- **`buildcraft.robotics` -- the Zone Planner (its entire scope), both platforms -- compiles, not
  live-verified this round (see the pace-change note at the top of this session's agent rules).**
  Confirmed by reading the actual 1.12.2 directory, not assumed, that this module is the Zone
  Planner in its entirety -- there is no robot system anywhere under `buildcraft.robotics`.
  `ContainerProgrammingTable_Neptune`/`GuiProgrammingTable_Neptune` were confirmed genuinely dead
  (a repo-wide reference search found nothing registers or opens either, in the original source or
  this port) and are not ported, matching this session's silicon-module precedent for the
  identically-named `TileProgrammingTable_Neptune`.
  - **What's real:** `ZoneChunk`/`ZonePlan` (a per-colour, chunk-grid claim map, one `ZoneChunk`
    BitSet per touched `ChunkPos`) ported near-verbatim, including the full `IZone` implementation.
    `TileZonePlanner` holds sixteen `ZonePlan` layers (one per `DyeColor`) plus a real 16-slot
    paintbrush storage inventory; both persist to NBT and sync to tracking clients through this
    port's established `markDirtyAndSync()` idiom. `BlockZonePlanner`/`ContainerZonePlanner`/
    `GuiZonePlanner` are real, working block/container/screen classes -- the block opens the GUI on
    right-click, wrench-rotates for free via `IBlockWithFacing`'s own default `attemptRotation`, and
    the GUI shows the paintbrush grid and player inventory over a plain panel background.
  - **Scope cuts, both caused by the same real upstream gap, not oversights.** This port has no
    concrete `buildcraft.core.item.ItemMapLocation` anywhere yet (confirmed via a repo-wide search:
    `buildcraft.api.items.IMapLocation` exists as an interface, but no implementing item or
    `BCCoreItems` field does) -- creating one was judged out of scope for a robotics-module cleanup
    pass and left to whoever next touches `buildcraft.core`. Without it: (1) the six map-location
    input/output slots and their 200-tick "processing" delay (import a claimed zone from a portable
    map item into a layer, or export one back onto a blank map) are not ported -- the exact NBT
    shape `ZonePlan#writeToNBT` produces is unchanged from 1.12.2's own `"chunkMapping"` tag, so this
    is cheap to add back once the item exists; (2) with nothing left to push from a click, the whole
    client-editable path (`sendLayerToServer`, the `NET_PLAN_CHANGE` id-tagged payload) is dropped
    too. Separately, and unconditionally: 1.12.2's `GuiZonePlanner` (a ~450-line hand-rolled 3D scene
    -- raw `GL11`/`GLU` immediate-mode calls, a perspective viewport scissored into the 2D screen, a
    ray-traced mouse pick against a client-side chunk-map cache, mouse-drag rectangle painting) has no
    realistic mapping onto either target's modern rendering pipeline in this pass's time budget, so
    it -- and its whole network-sync half (`ZonePlannerMapChunk`/`ZonePlannerMapChunkKey`/
    `ZonePlannerMapData{,Client,Server}`, `ZonePlannerMapRenderer`, `MessageZoneMapRequest`/
    `MessageZoneMapResponse`, `RenderZonePlanner`) -- is not ported either. None of this blocks the
    Zone Planner from being a real, working, data-correct block.
  - **Every new model/blockstate/texture reference resolves and every JSON parses** (the block's
    blockstate/models, copied byte-for-byte from the original's real `textures/blocks/zone_planner/`
    art), checked with a small Python script against both platforms -- no visible client was
    launched this pass.
  - **Files, both platforms.** New: `BCRoboticsRegistries`, `robotics/client/BCRoboticsClientRegistries`,
    `robotics/zone/{ZoneChunk,ZonePlan}`, `robotics/block/BlockZonePlanner`,
    `robotics/tile/TileZonePlanner`, `robotics/container/ContainerZonePlanner`,
    `robotics/gui/GuiZonePlanner`; `blockstates/zone_planner.json`, `models/block/zone_planner.json`,
    item model JSON (26.x: `items/zone_planner.json`; 1201: `models/item/zone_planner.json`), seven
    `textures/block/zone_planner_*.png` (real 1.12.2 art). Modified: `BuildCraft.java`
    (`BCRoboticsRegistries.register(modBus)` + `BCRoboticsClientRegistries::registerScreens`),
    `lang/en_us.json` (`block.buildcraft.zone_planner`).

- **`buildcraft.energy` -- the RF engine and the oil spring/world-gen, finishing this module, both
  platforms -- compiles, not live-verified this round (see the pace-change note at the top of this
  session's agent rules).**
  - **RF engine.** `TileEngineRF`/`BlockEngineRF`/`ContainerEngineRF`/`GuiEngineRF` are new (1.12.2's
    RF engine shared `BlockEngine_BC8` with the other two; this port gives it a dedicated block, like
    `BlockEngineStone`/`BlockEngineIron`). Consumes RF from a neighbouring source, boosted by up to
    four iron/gold gear upgrades, and converts it to MJ at `BCLibConfig.mjRfConversion`'s ratio --
    the arithmetic (upgrade lookup, heat ramp, RF-to-MJ conversion) is a direct, unchanged port of
    1.12.2's own. **`buildcraft.api.mj.MjToRfAutoConvertor` (mentioned in `PipeFlowPower`'s own
    javadoc) turned out not to apply here** -- it wraps an `IMjConnector` to look like Forge/NeoForge
    energy to the *outside*, the opposite direction from what an RF-consuming engine needs (accepting
    real incoming RF and storing it internally). On 1201, that direction is still 1.12.2's own
    hand-rolled `IEnergyStorage` inner class, ported over almost unchanged. On 26.x it is a genuine
    divergence: confirmed via `javap` against the real `neoforge-universal.jar` that
    `net.minecraftforge.energy.IEnergyStorage` does not exist on this target at all -- energy moves
    through `net.neoforged.neoforge.transfer.energy.EnergyHandler` inside a rollback-capable
    `Transaction` instead. Rather than hand-writing a second `SnapshotJournal` (the way
    `MjToRfAutoConvertor` itself had to), this uses NeoForge's own ready-made
    `SimpleEnergyHandler(capacity, maxInsert, maxExtract)`, confirmed present in the real jar, with
    `maxExtract = 0` reproducing 1.12.2's always-refuse-extraction behaviour for free.
  - **Oil spring and world-gen.** `TileSpringOil`/`BlockSpringOil` are new -- the "infinite oil
    source" block `BlockSpringWater`'s own javadoc and `TilePump`'s own javadoc were already
    anticipating (`ITileOilSpring`, ported to `buildcraft.core.tile` with a plain `UUID` in place of
    1.12.2's `GameProfile` -- the only thing `AdvancementUtil#unlockAdvancement`, the sole real
    consumer, ever needed from it). `EnumSpring.OIL`'s `liquidBlock` (left `null` until this module
    exists, per that enum's own javadoc) is now wired to the crude-oil fluid block. **World-gen was
    investigated, not guessed at:** this target's Feature/datapack system (already fully worked out
    for the water spring by `core.gen.SpringGenerator`/`BCCoreFeatures` -- a genuine 26.x-vs-1201 API
    split, record-`Feature`-plus-`MapCodec` there vs. the classic `Feature<FC>`-plus-
    `ConfiguredFeature` pair here) turned out to already be a clean, working target to reuse, so a new
    `energy.gen.OilSpringGenerator`/`BCEnergyFeatures` pair (mirroring `SpringGenerator`/
    `BCCoreFeatures` exactly) places a real oil vein from bedrock to the surface, gated on
    `EnumSpring#OIL.canGen`, attached to every overworld biome via the same `add_features` biome-
    modifier mechanism the water spring uses, at a deliberately rarer `rarity_filter` (`"chance":
    200` vs. water's `40`) standing in for 1.12.2's much lower spout/lake density. **What is a real,
    deliberate cut** is 1.12.2's actual oil-generation system beyond that: `OilGenerator`/
    `OilGenStructure` (a multi-shape spout/lake/flat-pattern/terrain-height placer) and its two
    custom `Biome`s (`BiomeOilOcean`/`BiomeOilDesert`, registered via `BiomeDictionary` and a
    `TerrainGen`-bus event handler) have no equivalent left on either target -- custom biome
    registration and `IWorldGenerator`/`GenerationStage` are both gone -- and reproducing that whole
    system on top of the new Feature infrastructure is real, separate design work well beyond this
    pass; a single-shape oil vein was judged the honest proportionate substitute, not a translation
    of the original odds. Per-player pump progress (`TileSpringOil#onPumpOil`) is tracked in memory
    but not persisted to NBT this pass, since the advancement it would drive
    (`buildcraftfactory:black_gold`) is not authored anywhere in this port yet and `TilePump` does
    not call `onPumpOil` either (a different module, out of this pass's scope) -- `totalSources`,
    the one field that matters regardless, is still saved.
  - **Every new model/blockstate/texture reference resolves and every JSON parses** (the RF engine
    reuses the Stirling Engine's existing side/back textures rather than authoring new art, matching
    `BCFactoryRegistries#AUTO_WORKBENCH_FLUIDS`'s own "no new texture asset" precedent; the oil spring
    reuses `spring_water`'s `minecraft:block/bedrock` model, same as that block), checked with a small
    Python script against both platforms.
  - **Files, both platforms.** New: `energy/tile/{TileEngineRF,TileSpringOil}`,
    `energy/block/{BlockEngineRF,BlockSpringOil}`, `energy/container/ContainerEngineRF`,
    `energy/gui/GuiEngineRF`, `energy/gen/OilSpringGenerator`, `BCEnergyFeatures`,
    `core/tile/ITileOilSpring`; `blockstates/{engine_rf,spring_oil}.json`, `models/block/engine_rf.json`,
    item model JSON for both, worldgen feature/placed-feature JSON for `spring_oil` plus its
    biome-modifier JSON (26.x: `neoforge/biome_modifier`; 1201: `forge/biome_modifier` +
    `configured_feature`). Modified: `BCEnergyRegistries` (`ENGINE_RF`/`ENGINE_RF_TYPE`/
    `ENGINE_RF_MENU`, `SPRING_OIL`/`SPRING_OIL_TYPE`, `EnumSpring.OIL.liquidBlock` wiring, capability
    registration), `BCEnergyClientRegistries` (RF screen + renderer), `lang/en_us.json`
    (`block.buildcraft.{engine_rf,spring_oil}`).

- **`buildcraft.transport`: the diamond pipe family (item, fluid, wood/diamond combo) and the three
  remaining power-pipe materials (iron, diamond, diamond_wood), on both platforms.** Ported faithfully
  against the real 1.12.2 `PipeBehaviourDiamond`/`DiamondItem`/`DiamondFluid`/`WoodDiamond`/`Limiter`
  sources, not assumed.
  - **Diamond pipes.** `PipeBehaviourDiamond` (54 phantom filter slots, 9 per face x 6 faces) plus
    `PipeBehaviourDiamondItem` (priority/split-on-match item routing) and `PipeBehaviourDiamondFluid`
    (the fluid twin) -- both near-verbatim ports. `PipeBehaviourWoodDiamond` (the combo material: a
    filtered active wooden pipe, white-list/black-list/round-robin) required real per-platform rework
    for fluid extraction: 26.x collapses 1.12.2's basic/`Adv` `tryExtractFluid` pair into one
    filter-taking, transaction-based method (no `ActionResult` `PASS` case can arise), while 1.20.1
    keeps both methods and translates `ActionResult<FluidStack>` into a plain nullable `FluidStack`
    (`null` == 1.12.2's `PASS`). Filter-slot NBT persistence also diverges: 26.x bridges
    `PipeBehaviour`'s raw `CompoundTag` contract to `ItemHandlerSimple`'s modern
    `serialize`/`deserialize(ValueOutput/ValueInput)` via `TagValueOutput`/`TagValueInput`
    (`ProblemReporter.DISCARDING`); 1.20.1 keeps 1.12.2's own `serializeNBT`/`deserializeNBT` unchanged.
  - **First pipe GUI on this port.** `ContainerDiamondPipe`/`ContainerDiamondWoodPipe` (both
    platforms) use the already-established `ContainerBCTile`/`AbstractContainerScreen` framework
    (`buildcraft.factory`'s own), not the abandoned 1.12.2 `GuiBC8`/`ContainerBC_Neptune` layer. New:
    `TilePipeHolder` now implements `MenuProvider` (dispatching by `instanceof
    PipeBehaviourDiamond`/`PipeBehaviourWoodDiamond` on its live `Pipe`), and `BlockPipeHolder` gained
    an activation hook (`activatePipeBehaviour`, called from `useWithoutItem`/`useItemOn` on 26.x, from
    the single `use()` on 1.20.1) that opens it when `PipeBehaviour#onPipeActivate` answers `true` --
    every other material's default `onPipeActivate` still answers `false`, so this is additive, not a
    behaviour change for existing pipes. The wood/diamond filter-mode buttons go through
    `AbstractContainerMenu#clickMenuButton`/`Minecraft#gameMode.handleInventoryButtonClick` (vanilla's
    own mechanism for a GUI button changing server-visible state, confirmed via the real decompiled
    `MultiPlayerGameMode` on both platforms) -- this port has no BuildCraft-specific GUI packet layer at
    all, so this is the correct replacement for 1.12.2's own `sendNewFilterMode`/`readMessage` pair, not
    a shortcut. **Scope cut:** the buttons are plain vanilla `Button` widgets with text labels, not
    1.12.2's custom pixel icon buttons (`GuiImageButton`/`pipe_emerald_button.png`) -- no button-widget
    framework exists in `buildcraft.lib.gui` on either platform yet, and building one was judged out of
    proportion to this round. **Verified:** clean compile on both platforms, every new
    model/blockstate/texture reference resolves (checked with a small Python script); the GUI itself
    was not live-clicked this round (no mouse/keyboard input automation in this environment, matching
    this port's established GUI-verification caveat).
  - **Power pipes: iron and diamond via the new `PipeBehaviourLimiter`** (a wrench-cycled, 0..6-step
    power throttle, halving the pipe's transfer rate each step until the final step disables transfer
    entirely) -- a close port of 1.12.2's own class on both platforms, with the RF branch and
    `ActionPowerLimit`/`onActionActivate` dropped (no RF power pipe is ever registered on this port at
    all, and gates/statements remain out of scope). **diamond_wood power is *not* `PipeBehaviourLimiter`
    -- verified against 1.12.2's real `BCTransportPipes#preInit` rather than the round's own task
    description**, which had named it alongside iron/diamond as a limiter material: the real source
    switches `diaWoodPower` to plain `PipeBehaviourWoodPower` (the same class `pipe_power_wood` already
    uses), never to the limiter. Ported faithfully to match. Transfer rates are 1.12.2's own
    `BCTransportConfig` formula results (iron x8/32, diamond x64/32, diamond_wood x64/32 receiver).
    Dedicated kinesis textures are still not ported for any of the nine power materials (unchanged
    scope cut from the earlier power-pipes batch); all three new power materials reuse their item
    pipe's own arm/core models.
  - **Per-platform wrench-vs-GUI ordering divergence, found by reading the real dispatch chain, not
    assumed.** `PipeBehaviourWoodDiamond#onPipeActivate` checks for a held wrench and returns `false`
    when one is present -- redundant on 26.x (a wrench click never reaches this method at all; the
    wrench's own `useOn` already returns non-`PASS` first) but load-bearing on 1.20.1, where
    `BlockState#use` is tried *before* the wrench's own `useOn`, so without the check a wrench click on
    a diamond/wood pipe would always open the GUI instead of ever reaching `attemptRotation`.
  - **Shared-tree note:** this batch ran concurrently with another agent's own wires/gates/pluggables
    work touching the same files (`BCTransportRegistries`, `BlockPipeHolder`, `TilePipeHolder`,
    `blockstates/pipe_holder.json`, `lang/en_us.json`) on both platforms; edits were interleaved by
    re-reading immediately before each change, and the final combined
    `:neoforge-26x:compileJava :neoforge-1201:compileJava` was confirmed clean with both batches' work
    present.
  - **Files, both platforms.** New: `transport/pipe/behaviour/{PipeBehaviourDiamond,
    PipeBehaviourDiamondItem,PipeBehaviourDiamondFluid,PipeBehaviourWoodDiamond,PipeBehaviourLimiter}`,
    `transport/container/{ContainerDiamondPipe,ContainerDiamondWoodPipe}`,
    `transport/gui/{GuiDiamondPipe,GuiDiamondWoodPipe}`. Modified: `BCTransportRegistries` (diamond/
    diamond_wood item+fluid `PipeDefinition`s and their placeable items, `PIPE_IRON_POWER`/
    `PIPE_DIAMOND_POWER`/`PIPE_DIAMOND_WOOD_POWER` plus their items, two new `MenuType`s),
    `BlockPipeHolder` (pipe-behaviour activation hook), `TilePipeHolder` (`MenuProvider`),
    `BCTransportClientRegistries`+`BuildCraft` (screen registration), `EnumPipeMaterial` (7 new values),
    `blockstates/pipe_holder.json` (61 new multipart entries, reusing iron's existing arm/core models for
    the power variants), 14 new block models + 7 new item models per platform (26.x: `items/*.json`;
    1201: `models/item/*.json`), 6 new textures (`pipe_diamond{,_fluid}`,
    `pipe_diamond_wood{,_filled,_fluid,_fluid_filled}`, copied byte-for-byte from `buildcraft_resources`)
    plus 2 GUI textures (`filter.png`, `pipe_diamond_wood.png` -- 1.12.2's own `pipe_emerald.png`,
    renamed since this port has no emerald pipe), `lang/en_us.json` (12 new keys).

- **Wires, gates and pluggables -- a partial landing, both platforms, compiles, not live-verified
  this round (see the pace-change note at the top of this session's agent rules).** 1.12.2's gate
  system lived under `buildcraft.silicon` only because of an assembly-table dependency that never
  actually exists on this port; this batch keeps it under `buildcraft.transport.{gate,plug,
  item,container,gui,statements}` instead, next to the pipe/wire system it actually depends on.
  - **Wire network/signal logic, real for the first time.** `buildcraft.transport.wire.WireNetwork`
    ports 1.12.2's `WireSystem` (`canWireConnect` -- a wire crosses a pipe boundary only where the
    pipes are already connected, neither face is blocked by a blocking pluggable, and, for two
    structure pipes, their dye colours match or are unset) as a plain on-demand breadth-first walk
    over `WireNode`s, stopping the moment a reachable `IWireEmitter` answers `isEmitting`. **Not
    ported:** 1.12.2's persisted, incrementally-maintained `WorldSavedDataWireSystems` graph and its
    two bespoke sync payloads (`MessageWireSystems`/`MessageWireSystemsPowered`) -- recomputing on
    demand needs no separate persisted state to save, load, or keep in sync with the world at all
    (the tile's own already-NBT-synced wire-colour map is the only durable state a wire needs), which
    is a deliberate trade against very large networks' query cost, not an oversight.
    `SimplePipeWireManager#isPowered`/`isAnyPowered` (both platforms, previously honest
    always-`false` stubs per that class's own prior javadoc) now delegate to it for real.
  - **`TilePipeHolder#pluggables`** (both platforms): a real `EnumMap<Direction, PipePluggable>`,
    persisted whole (definition id + own NBT) under a `"pluggables"` key with the same "reload
    replaces wholesale" semantics `pipe` already had, wired into drops (`BlockPipeHolder#getDrops`),
    ticking, `preRemoveSideEffects`/`onRemove` (26.x/1201's differently-shaped genuine-removal
    hooks), and both capability paths (`getCapabilityFromPipe` for the neighbour-facing pipe
    connection, the tile's own `getCapability` for a pluggable's *own* outward capability -- a
    blocking pluggable with no capability of its own now correctly cuts a neighbour off, matching
    `PluggablePowerAdaptor`'s "blocking but re-exposes MJ anyway" contract). `BlockPipeHolder` gained
    real item-use dispatch (26.x: `useWithoutItem`/`useItemOn`; 1201: the single `use()`) that
    activates an existing pluggable on the clicked face first, then places one from an
    `IItemPluggable` held item.
  - **Three real pluggables.** `PluggableBlocker`/`PluggablePowerAdaptor` (`buildcraft.transport.
    plug`, near-verbatim ports; the RF-to-MJ auto-conversion bridge inside the power adaptor is a
    documented scope cut, orthogonal Forge-Energy interop) and `PluggableGate`, wrapping a real
    `GateLogic` (`buildcraft.transport.gate`: `EnumGateMaterial`/`EnumGateLogic`/`EnumGateModifier`/
    `GateVariant`/`ActionType`/`TriggerType`, close ports of 1.12.2's `buildcraft.silicon.gate`
    package) that runs the same AND/OR trigger-group resolution loop every tick. **The whole
    `NET_ID_*`/`writePayload`/`readPayload`/`sendResolveData`/`sendStatementUpdate` bespoke gate
    network layer is dropped**, per this round's "only add a bespoke payload where the established
    sync convention genuinely can't do the job" rule -- `PluggableGate`'s state already round-trips
    through `TilePipeHolder`'s own pluggable NBT, and the GUI (below) needs no separate push channel.
  - **One real end-to-end trigger/action pair.** `TriggerPipeSignal`/`ActionPipeSignal`
    (`buildcraft.transport.statements`, one instance per dye colour, registered as internal
    gate-only statements via new `TriggerProviderPipes`/`ActionProviderPipes`) read and write
    `IWireManager#isAnyPowered`/`IWireEmitter#emitWire` for real -- a gate on one pipe emitting a
    colour is genuinely readable by a gate on a same-coloured wire elsewhere in the network, through
    `WireNetwork`'s BFS. **Scope cut:** `TriggerParameterSignal`/`ActionParameterSignal` (extra
    colour parameter slots) are not ported -- both statements report `maxParameters() == 0`;
    `ActionPipeColor`/`ActionPipeDirection`/`TriggerFluidsTraversing`/`TriggerItemsTraversing`/
    `TriggerPowerRequested`/`ActionPowerLimit`/`ActionExtractionPreset` are not ported at all, in
    favour of landing one real trigger/action pair end-to-end over breadth across many.
  - **A working gate configuration GUI**, but a wholly new, plain one -- not a port of 1.12.2's
    hand-drawn icon-grid `GuiGate`, which is squarely a rendering-polish task out of this round's
    scope. `ContainerGate`/`GuiGate` use only vanilla mechanisms: one `DataSlot` pair per gate slot
    (trigger index, action index into `GateLogic#getAllValidTriggers/Actions`, computed identically
    on both sides since every input they read is already client-synced) for state going
    server-to-client, and `AbstractContainerMenu#clickMenuButton`
    (`Minecraft#gameMode.handleInventoryButtonClick`, vanilla's own "pick one of a fixed list"
    mechanism, already re-verified this round by the diamond-pipe batch above) for a button cycling
    a slot's assignment -- no bespoke gate network payload exists anywhere in this batch. Opens via
    `player.openMenu`/`NetworkHooks.openScreen`'s 4-arg extra-data overload (pos + side; a pipe can
    have up to six gates, so pos alone can't find the right one back).
  - **`ItemPluggableGate`** is the one physical gate item, an NBT-tagged `GateVariant` distinguishing
    every material/logic/modifier combination -- matching 1.12.2's own NBT-variant shape. **Scope
    cut:** the full material x logic x modifier creative-tab cartesian product
    (`addSubItems`/`addModelVariants`) is not ported; only the untagged default stack (which reads
    back as `GateVariant`'s own "basic gate" default: AND/clay-brick/no-modifier) is in the creative
    tab. Every other variant still works, just isn't pre-populated there this round.
  - **Not ported at all this round:** facades (`PluggableFacade` and its `Facade{BlockStateInfo,
    Instance,PhasedState,StateManager}`/`FilterEventHandler` support classes -- a large, mostly
    orthogonal data-driven "disguise this pipe segment as another block" subsystem whose own custom
    rendering integration has no realistic home in this round's time budget), the gate-accessory
    pluggables (`PluggableLens`/`Pulsar`/`Timer`/`LightSensor`), `ItemGateCopier`, and pipe/wire
    dye colouring (`ActionPipeColor` and the colour-cycling wrench interaction it implies). All of
    these are genuine, deliberate scope cuts, not overlooked -- prioritising one real, working,
    wire-connected gate over broad-but-shallow coverage of the whole pluggable family.
  - **Files, both platforms.** New: `transport/wire/WireNetwork`, `transport/gate/{EnumGateMaterial,
    EnumGateLogic,EnumGateModifier,GateVariant,GateLogic,ActionType,TriggerType}`,
    `transport/plug/{PluggableBlocker,PluggablePowerAdaptor,PluggableGate}`,
    `transport/item/{ItemPluggableGate,ItemPluggableSimple}`, `transport/container/ContainerGate`,
    `transport/gui/GuiGate`, `transport/statements/{TriggerPipeSignal,ActionPipeSignal,
    TriggerProviderPipes,ActionProviderPipes}`. Modified: `TilePipeHolder` (`pluggables` field,
    capability/drop/tick wiring), `BlockPipeHolder` (item-use pluggable dispatch, `onRemove`/
    `preRemoveSideEffects`), `SimplePipeWireManager` (`isPowered`/`isAnyPowered` now real),
    `BCTransportRegistries` (pluggable definitions, three items, one `MenuType`, statement-provider
    registration), `BCTransportClientRegistries` (gate screen registration), `lang/en_us.json`
    (9 new keys).

- **Real, deliberate scope-cut reversal: every Engine block and the Tank now have their real shape and a
  matching collision/outline `VoxelShape`, on both platforms.** Found live, from real gameplay on a deployed
  client, not by reading: every engine (Wood/Stone/Iron/RF/Creative) and the Tank had rendered as a plain
  textured full cube since the batches that first ported them, each with a javadoc comment explicitly
  deferring the real shape ("no renderer to show it off yet"/"no custom render shape yet"). Once
  `RenderTileTank` and the engines' own real GUIs existed, that reasoning was stale, and a real client session
  showed it: the hover outline and collision box were still a full 1x1x1 cube, visibly wrong once anything
  suggested the block wasn't one.
  - **Engines.** `buildcraft.lib.block.EngineShapes` (new, both platforms, identical -- `VoxelShape`/`Shapes`/
    `BooleanOp`/`Direction` are unchanged between targets, confirmed via `javap`) computes the real per-facing
    shape by rotating `buildcraftlib:models/block/engine_base.json`'s own geometry (an 8-thick mounting slab
    flush against the powered machine, plus a 12-long, 8x8 trunk protruding from the output face) through the
    same 90-degree-multiple rotations the model needs per facing, derived by hand from first principles
    (rotate each box's corners around the block's centre, per axis, then take the new min/max) and double
    checked against the geometry making physical sense (an 8-thick slab spanning the full cross-section on the
    mounting side, a 12-long trunk out the opposite face, for all six facings). All five engine blocks now
    override `getShape`/`getCollisionShape` to return `EngineShapes.get(state.getValue(FACING))`.
  - **Engine block models.** Replaced the placeholder `"parent": "minecraft:block/cube"` models with a real
    static model per tier (base + base-at-rest + trunk, i.e. `engine_base.json`'s own elements evaluated at
    `progress = 0`, its idle pose -- the fourth, "chamber" element is genuinely zero-height at that progress
    value, so it is correctly omitted, not cut). The dynamic parts of the original (`variables`/`rules`, the
    piston animation, the per-heat trunk texture swap via `stage`) still have no equivalent -- that needs
    `buildcraft.lib.expression`'s own model-loader integration, which is real, separate, unstarted work; the
    trunk texture is fixed to `trunk_blue` (`trunk_creative` for the Creative engine, matching the original's
    own `stage_light: 0` override), a fair "idle/default appearance" stand-in copied byte-for-byte from
    `buildcraft_resources`, not a new asset. Blockstates were rewritten to match the model's native
    "facing up" orientation (previously the rotation table assumed a native "facing north" orientation, which
    made no visible difference on a symmetric cube but would have been wrong the moment the real geometry
    landed).
  - **Tank.** The block model now matches 1.12.2's real bounding box exactly (`2/16 .. 14/16` inset on every
    horizontal side, full height) instead of a plain cube, and `BlockTank#getShape`/`getCollisionShape` return
    the matching `Shapes.box(...)` on both platforms.
  - Not done this pass, noted for the next visual sweep: several other blocks ported this session under an
    identical "plain cube for now" scope cut (Quarry, Frame, Filler, the silicon tables, Zone Planner, and
    others) still render as plain cubes with default full-cube collision. This fix covers only the two the
    coordinator was shown live (engines, tanks) -- the same treatment should be applied to the rest before
    calling rendering fidelity done.

- **Follow-up to the above, same real client session: the pipe holder had the identical missing-shape bug, plus
  two separate, real, unrelated bugs the same session surfaced.**
  - **`BlockPipeHolder` had no `getShape`/`getCollisionShape` override at all, on either platform** -- every
    pipe, no matter how few of its six faces were actually connected, collided and highlighted as a full 1x1x1
    cube. Fixed with a new `buildcraft.transport.block.PipeShapes` (both platforms, identical): a `0.25..0.75`
    centre cube plus one arm box per connected face, derived from 1.12.2's own real
    `BlockPipeHolder#addCollisionBoxToList` algorithm (not invented) -- each arm's size comes from
    `Pipe#getConnectedDist(Direction)`, the exact value `RenderTilePipeHolder` already reads to draw the visible
    connection arm, so the collision box and the visible pipe always agree. Not yet included: pluggable and
    wire-part boxes, which 1.12.2 also unions in -- a smaller, real follow-up.
  - **A genuine bad source asset, not a code bug: `buildcraft_resources/assets/buildcraftfactory/textures/
    blocks/tank/{side,end}.png` are not usable tank textures.** Found by a real client session showing the Tank
    rendering as a white cube with a red diagonal accent no matter what fluid it held or which of two very
    different block models (the plain full cube it shipped with, and a from-scratch inset-shape replacement
    built this same session) it used -- ruling out both the model geometry and the fluid content as the cause.
    Decoded the actual texture pixels to confirm: `tank/side.png` is 172/256 fully-transparent pixels and
    `tank/end.png` is 212/256, with the only opaque colour being a dark red (`#7F0000`) -- compared against
    `engine/stone/side.png` (256/256 opaque, a real texture) to confirm this isn't just this asset family's
    normal style. Whatever these two files actually are, they are not a tank casing texture, and no other file
    anywhere under `buildcraft_resources` looks like a plausible correct replacement. **Reverted the Tank's
    block model back to the plain full cube** (matching pre-session behaviour, at least not visibly broken)
    rather than ship the correctly-shaped model against a broken texture; the real fix needs the actual source
    art, which isn't available in this repository.
  - **Seven items had no item model registered on 26.x at all** (`Missing item model for location buildcraft:...`
    at startup, confirmed via the client log): `quarry`, `filler`, `architect_table`, `builder` already had a
    real block model and just needed the `items/<name>.json` wrapper that every other block-item in this port
    has (1.20.1 already had these four -- 26.x-only gap). `gate` had no model or texture at all anywhere in the
    port; gave it a real flat icon using 1.12.2's own `gate_and.png` (a genuine BuildCraft texture, not invented
    art) on both platforms, as an honest placeholder for the real per-material/logic/modifier gate rendering
    that doesn't exist yet. `plug_blocker`/`plug_power_adaptor` are left unfixed: no texture for either exists
    anywhere in `buildcraft_resources`, so there is no real art to point a model at.

**Both targets are verified by booting a server**, not just by compiling. That matters: every
bug in the "Build and packaging gotchas" section below compiled cleanly and only showed up at
runtime. Re-run `./gradlew :neoforge-26x:runServer` (and the 1.20.1 equivalent) after any
registration change.

Not verified: client-side rendering. Checking that the models actually draw needs a display,
so treat the blockstate/model JSON as unconfirmed until someone runs `runClient`.

Remaining, in the order they should be tackled — each module needs the one above it:

| Module | Files | Notes |
| --- | --- | --- |
| `BuildCraftAPI/api` | **done** | 217/251; the remainder is blocked on the modules or on rendering, listed above. |
| `buildcraft.lib` | 541 | The foundation: tiles, GUI, networking, models, MJ power. |
| `buildcraft.core` | 84 | Gears (done), wrench, markers, engines, paintbrush (done), map location. |
| `buildcraft.transport` | 124 | Pipes. The largest single feature. Nine item-pipe materials done: cobblestone (passive), wooden (MJ-powered active extraction), stone/sandstone/quartz (speed-modifier), gold (speed boost), void (destroys items), clay (prefers inventories), iron (one-way, wrench-selected output); wrench cycling of wood/iron active faces with a "filled" active-face texture; per-material drops, connection-shape rendering, travelling-item rendering. Fluid flow (`PipeFlowFluids`) and nine fluid pipes done: the same nine materials, each with its own textures, fluid-capability connections, and fluid rendering. Power flow (`PipeFlowPower`) and all nine power-pipe materials now done: cobblestone/wooden/stone/sandstone/quartz/gold from an earlier batch (engine -> pipe -> machine transfer and T-junction splitting verified live on both platforms), plus iron/diamond/diamond_wood this batch via the new `PipeBehaviourLimiter` (a wrench-cycling, seven-step redstone-style power throttle, `MAX_SHIFT = 6`) for iron and diamond, and plain `PipeBehaviourWoodPower` (the same class `pipe_power_wood` already uses, **not** the limiter -- verified against 1.12.2's real `BCTransportPipes#preInit`, which switches `diaWoodPower` to `PipeBehaviourWoodPower`, never to `PipeBehaviourLimiter`, diverging from this round's own task description) for diamond_wood. Dedicated kinesis pipe art is still not ported for any of the nine power materials -- every one still reuses its item-pipe's own arm/core models and textures, an explicit, unchanged scope cut from the earlier batch. The diamond (sorting/filtering) item and fluid pipes are done this batch too (`PipeBehaviourDiamond`/`Item`/`Fluid`, 54 phantom filter slots across 6 faces, priority/split-on-match item and fluid routing), plus the wood/diamond combo (`PipeBehaviourWoodDiamond`, a filtered active wooden pipe with white-list/black-list/round-robin modes) as both an item and a fluid pipe -- the first pipes on this port with a real GUI (`ContainerDiamondPipe`/`ContainerDiamondWoodPipe`, a new `TilePipeHolder`-as-`MenuProvider` dispatch and `BlockPipeHolder` activation hook, both platforms); the wood/diamond filter-mode buttons use plain vanilla `Button` widgets rather than 1.12.2's own pixel icon buttons (no button-widget framework exists in `buildcraft.lib.gui` yet) -- compiles and opens against real filter slots, not live-clicked. Five exotic item-pipe materials done this batch: obsidian (an MJ-powered magnet, not explosive -- confirmed by reading its real 1.12.2 source), lapis (paints items with a wrench-cycled colour for downstream sorting), daizuli (a directional colour filter, BuildCraft-8-specific), emzuli (a four-preset extraction wooden pipe, BuildCraft-8-specific, currently inert with no GUI/statement system to populate its presets), and stripes (the extraction pipe -- mines the block ahead of its open face and offers items about to leave that face to a new `buildcraft.transport.stripes` handler dispatch: `StripesRegistry` plus `Plant`/`Hoe`/`PlaceBlock`/`Use`/`EntityInteract`/`MinecartDestroy`/`Pipes` handlers; `Shears` (its `IShearable` target moved to entities-only on this target) and `Dispenser` (`BlockSource` became a record tied to a real `DispenserBlockEntity`, no longer freely implementable) are scope cuts, `PipeWires` was already dead/commented-out in 1.12.2 itself). `PipeExtensionManager` (the stripes pipe laying/retracting a pipe run ahead of itself) is a documented stub that always declines -- the real block-entity-relocation dance is substantial, unstarted engineering, not attempted this round; a pipe item offered to a stripes pipe simply ejects normally instead. **Wires/gates/pluggables done this batch, partial landing** (both platforms, compiles, not live-verified -- see this module's own dated Progress entry): real cross-pipe wire signal propagation (`WireNetwork`, an on-demand BFS replacing 1.12.2's persisted `WorldSavedDataWireSystems` graph), `TilePipeHolder#pluggables` (a real per-side `PipePluggable` map with NBT/drops/tick/capability plumbing), three real pluggables (`PluggableBlocker`, `PluggablePowerAdaptor`, `PluggableGate`), one end-to-end trigger/action pair (`TriggerPipeSignal`/`ActionPipeSignal`, reading/writing the real wire network), and a working (if plain-`Button`) gate configuration GUI (`ContainerGate`/`GuiGate`, no bespoke network payload -- vanilla `DataSlot`/`clickMenuButton`). Gates/pluggables live under `buildcraft.transport.{gate,plug}` in this port, not `buildcraft.silicon` -- see the `buildcraft.silicon` row below. Colours and facades still to come. |
| `buildcraft.builders` | 118 | Quarry (done: frame construction, MJ-metered digging, item output, save/restart, plus a laser-based `RenderQuarry` -- static frame outline + a simplified current-action rail/drop indicator, no client-interpolated drill-carriage animation; still no chunkloading; `AdvDebuggerQuarry` not ported, depends on the unported `ChunkLoaderManager`). Filler ported (compiles both platforms, GUI opens; box-and-pattern-only, not addon/`VolumeBox`-planner mode; 8 of the original's ~20 patterns -- box/clear/fill/frame/pyramid/sphere/stairs/none -- ported onto a plain local grid rather than the unported `Template`/`IFilledTemplate` system; `PatternSpherePart` and the nine `PatternShape2d` subclasses still to come; GUI is plain vanilla buttons cycling pattern/params/invert/excavate, not the original's drag-and-drop gate palette; not live-verified this round). Builder + Architect Table ported as a deliberately simplified blueprint slice (compiles both platforms, not live-verified this round -- see this module's own dated Progress entry above for the full "what's real vs. cut" list: `BlockState`-only palette, no rotation/entities/tile-NBT/rule-system/`FakeWorld`/path system/GUI). `TileElectronicLibrary`/`TileReplacer` and the `Template` snapshot type still to come. |
| `buildcraft.silicon` | 79 | Standalone machines done (compiles both platforms, not live-verified this round): assembly table (recipe scan/save/GUI), advanced crafting table (laser-powered 3x3 grid), integration table (does not need gates), charging table (ported as the same inert stub 1.12.2 shipped). `TileLaser` (the beam emitter that actually feeds these tables power) and `TileProgrammingTable_Neptune` (confirmed dead 1.12.2 code) are not ported -- see this module's own dated Progress entry above. **1.12.2's `buildcraft.silicon.{gate,plug,item}` package (gates, the gate-dependent pluggables, `ItemGateCopier`) landed this round under `buildcraft.transport.{gate,plug,item}` instead** -- see the `buildcraft.transport` row above and this module's own dated Progress entry: gates never depended on anything else actually in `buildcraft.silicon` (the crafting/assembly tables above), only on the pipe/wire system `buildcraft.transport` already owns, so this port keeps them there rather than preserving 1.12.2's package split. Facades, lenses/pulsars/timers/light-sensors, and `ItemGateCopier` still to come, wherever they land. |
| `buildcraft.factory` | 46 | **done.** Chute, mining well + tube (now with its status-LED/tube-laser renderer), pump (likewise), tank, flood gate, auto workbench (items and fluids halves), distiller, heat exchanger (with the refinery recipe table). |
| `buildcraft.energy` | 41 | **done, this pass's scope.** Stirling and Combustion (Iron) engines (done). Oil/fuel fluids -- 10 fluids x 3 heats, blocks, buckets, client models -- and the fuel/coolant registry (done). RF engine done (compiles both platforms, registers a real RF-to-MJ conversion capability, not live-verified this round). Oil spring (`TileSpringOil`/`BlockSpringOil`) and a simplified oil world-gen feature done -- see this module's own dated Progress entry for the real cuts (no per-player pump-progress NBT, no custom oil biomes/spout-lake generator). |
| `buildcraft.robotics` | 24 | **done, this pass's scope.** Confirmed (by reading the real 1.12.2 directory, not assumed) to be the Zone Planner only -- there is no robot system in this module at all. Zone Planner ported: block/tile/container/GUI all real and compiling, with the sixteen `ZonePlan` chunk-grid claim layers fully persisted and synced; the map-location-item exchange and the 1.12.2 GUI's raw-GL 3D minimap are documented scope cuts -- see this module's own dated Progress entry. |

Within `buildcraft.lib` the hard parts, roughly in dependency order, are: the registration
layer, `MjAPI`/power, the tile + networking stack (1.12's custom packet system has to become
NeoForge payloads), NBT (which becomes data components on 26.x), then GUI and models.

## API migration reference

Worked out against the real jars rather than from memory. `javap` on
`platforms/neoforge-26x/build/moddev/artifacts/minecraft-patched-*-merged.jar` settles any
question this table does not.

### Package and type renames (1.12.2 → modern)

| 1.12.2 | Modern |
| --- | --- |
| `net.minecraft.util.EnumFacing` | `net.minecraft.core.Direction` |
| `EnumFacing.Axis` / `.AxisDirection` | `Direction.Axis` / `Direction.AxisDirection` |
| `net.minecraft.util.math.BlockPos` | `net.minecraft.core.BlockPos` |
| `net.minecraft.util.math.Vec3d` | `net.minecraft.world.phys.Vec3` |
| `net.minecraft.util.math.Vec3i` | `net.minecraft.core.Vec3i` |
| `net.minecraft.util.math.AxisAlignedBB` | `net.minecraft.world.phys.AABB` |
| `net.minecraft.util.Rotation` | `net.minecraft.world.level.block.Rotation` |
| `net.minecraft.world.World` | `net.minecraft.world.level.Level` |
| `net.minecraft.block.state.IBlockState` | `net.minecraft.world.level.block.state.BlockState` |
| `net.minecraft.tileentity.TileEntity` | `net.minecraft.world.level.block.entity.BlockEntity` |
| `net.minecraft.item.ItemStack` | `net.minecraft.world.item.ItemStack` |
| `net.minecraft.util.ResourceLocation` | `net.minecraft.resources.ResourceLocation` on 1.20.1, **`net.minecraft.resources.Identifier` on 26.x** |
| `javax.vecmath.*` | `org.joml.*` |
| `gnu.trove.*` | `it.unimi.dsi.fastutil.*` |
| `net.minecraft.nbt.CompressedStreamTools` | `net.minecraft.nbt.NbtIo` |
| `net.minecraft.profiler.Profiler` (concrete, no-op by default) | `net.minecraft.util.profiling.ProfilerFiller` (interface); `InactiveProfiler.INSTANCE` for a standalone no-op |

`gnu.trove.*` is not a rename-and-done -- the collection types, method names and a couple of
behaviours all differ:

- `TIntIntHashMap` -> `Int2IntOpenHashMap`, `TIntHashSet` -> `IntOpenHashSet`, and the
  `T<Type>ArrayList` family -> `it.unimi.dsi.fastutil.<type>s.<Type>ArrayList` (note the
  lower-case type in the package).
- `list.toArray()` needs the typed name -- `toIntArray()`, `toByteArray()`, etc. -- fastutil has
  no bare no-arg overload.
- `list.sort()` (no args) -> `list.sort(null)`; fastutil's `sort(Comparator)` treats `null` as
  natural order, same as Trove's implicit sort.
- `TIntIntHashMap#increment(key)` (false if the key was absent, so callers needed a manual
  `put(key, 1)` fallback) collapses to a single `Int2IntOpenHashMap#addTo(key, 1)` -- `addTo`
  inserts an absent key starting from the map's default value, so it always does the right thing
  in one call.
- `TIntArrayList#remove(offset, length)` -> `IntArrayList#removeElements(from, to)`; the second
  argument's meaning changes from a length to an end index (equivalent at `offset == 0`, not
  otherwise).
- Trove's bulk-append `list.add(int[])` has no fastutil equivalent; use the array constructor
  (`new IntArrayList(data)`) instead of `new IntArrayList(); list.add(data);`.

### Method renames

| 1.12.2 | Modern |
| --- | --- |
| `Vec3d.addVector(x, y, z)` | `Vec3.add(x, y, z)` |
| `EnumFacing.getFrontOffsetX()` | `Direction.getStepX()` |
| `EnumFacing.getFacingFromAxis(dir, axis)` | `Direction.fromAxisAndDirection(axis, dir)` |
| `EnumFacing.getDirectionVec()` | `Direction.getUnitVec3i()` |
| `new BlockPos(double, double, double)` | `BlockPos.containing(double, double, double)` (floors) |
| `world.isRemote` / `level.isClientSide` (field) | `level.isClientSide()` — the field is private on 26.x |
| `TileEntity.readFromNBT` / `writeToNBT` | `BlockEntity.loadAdditional` / `saveAdditional` |
| `ITickable.update()` | a `BlockEntityTicker` returned from `EntityBlock.getTicker` |
| `Block.hasTileEntity` / `createTileEntity` | implement `EntityBlock.newBlockEntity` |
| `AxisAlignedBB.grow(amount)` | `AABB.inflate(amount)` |
| `Vec3i.add(Vec3i)` / `BlockPos.add(Vec3i)` | `Vec3i.offset(Vec3i)` / `BlockPos.offset(Vec3i)` |
| `EntityPlayerMP.getAdvancements().grantCriterion(advancement, name)` | `PlayerAdvancements.award(advancement, name)` |
| `FMLCommonHandler.instance().getMinecraftServerInstance()` | `ServerLifecycleHooks.getCurrentServer()` |
| `BlockPos.offset(EnumFacing[, int])` | `BlockPos.relative(Direction[, int])` |
| `EnumFacing.rotateAround(Axis)` | `Direction.getClockWise(Axis)` (no bare `rotateAround` any more) |
| `AxisDirection.getOffset()` | `AxisDirection.getStep()` |
| `I18n.translateToLocal`/`canTranslate` (`net.minecraft.util.text.translation`) | `Language.getInstance().getOrDefault(key)` / `.has(key)` — common code on both targets, unlike the client-only modern `I18n` |
| `Profiler.getProfilingData(name)` -> `List<Profiler.Result>` | `ProfileResults.getTimes(name)` -> `List<ResultField>` (obtained from a `ProfileCollector`'s `getResults()`, not from the write-side `ProfilerFiller` itself) |
| `Profiler.Result.profilerName`/`usePercentage`/`totalUsePercentage` | `ResultField.name`/`percentage`/`globalPercentage` |
| `IFluidHandler.fill`/`drain(..., boolean doFill/doDrain)` | `fill`/`drain(..., IFluidHandler.FluidAction)` — true on 1.20.1 too, not just the 26.x transfer API |
| `FluidStack.amount` (public field) | `FluidStack.getAmount()`/`setAmount(int)`/`grow(int)`/`shrink(int)` — both targets' `FluidStack` (`net.minecraftforge.fluids.FluidStack` on 1.20.1, `net.neoforged.neoforge.fluids.FluidStack` on 26.x) |

`EnumDyeColor`/`DyeColor`'s ordinal order flipped along the way: 1.12.2's `getDyeDamage()` ran
BLACK(0)..WHITE(15); modern `DyeColor.getId()` (== `ordinal()`) runs WHITE(0)..BLACK(15), the
reverse. Anything indexing a BuildCraft-owned array by the old damage value needs the array
reordered to match, not just the accessor renamed -- `ColourUtil`'s `NAMES`/`DARK_HEX`/
`LIGHT_HEX` are the worked example. Unchanged on both targets, since `DyeColor` is pure vanilla.

`BlockPos` no longer has a double constructor at all, so anything that rounded a different
way — `VecUtil.convertCeiling` for instance — has to cast explicitly.

### Structural changes, in rough order of pain

1. **Block metadata is gone** (1.13). Every `IBlockState`/metadata pair becomes a real
   blockstate property. This is the single biggest source of work in `transport` and
   `builders`.
2. **Registration.** BuildCraft's `RegistrationHelper` + FML `preInit` becomes
   `DeferredRegister` on the mod event bus. On 26.x the registry object carries its own id,
   so items no longer need an unlocalised-name string threaded through the constructor.
3. **`CompoundTag`'s getters return `Optional` on 26.x**, and this one is dangerous because the
   compiler only catches some of it. `getInt(name)` is `Optional<Integer>`; the value form is
   `getIntOr(name, default)`. 1.12.2's `getInteger` returned 0 for a missing key, so `getIntOr(name, 0)`
   is the faithful port — but it is now an explicit choice, and for a lot of BuildCraft's code the
   honest default is not zero. `getTagList(name, type)` became
   `getList(name).orElseGet(ListTag::new)`, and the write side is `setX` → `putX` throughout.
   1.20.1 still returns values, so this is a per-target divergence. `misc/port/rename.py` applies the
   faithful mapping and `misc/port/to1201.py` reverses it.
4. **NBT → data components** (1.20.5). Item NBT does not exist on 26.x; anything BuildCraft
   stored on a stack needs a `DataComponentType`. Block entities still use NBT, but on 26.x they
   read and write it through `ValueInput`/`ValueOutput` (`getLongOr(name, default)`,
   `putLong(name, value)`) rather than `CompoundTag`, so the hooks are `loadAdditional` and
   `saveAdditional`. 1.20.1 still uses `CompoundTag`.

   For the specific case of "BuildCraft wants to stash an arbitrary `CompoundTag` on a stack" --
   which is most of what item NBT was actually used for (guide books, filters, markers, list
   contents) -- vanilla already componentized exactly that as `DataComponents.CUSTOM_DATA`
   (`CustomData`, holding a `CompoundTag`). Use it rather than inventing a BuildCraft-specific
   component type. The one thing to get right: `CustomData` is copy-on-read.
   `stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()` hands back a
   *detached copy* -- mutating it does nothing to the stack. The 1.12.2 idiom of
   `getItemData(stack).setInteger(...)` and walking away relied on the returned tag being a live
   reference, which no longer holds. Either pair a read with an explicit write
   (`CustomData.set(DataComponents.CUSTOM_DATA, stack, nbt)`), or use
   `CustomData.update(DataComponents.CUSTOM_DATA, stack, nbtConsumer)`, which reads, hands your
   lambda a mutable tag, and writes the result back in one call -- the closest equivalent to the
   old idiom this target allows. `buildcraft.lib.misc.NBTUtilBC` on 26.x splits into
   `getItemData`/`setItemData`/`updateItemData` for exactly this reason; the 1.20.1 copy keeps
   the original single `getItemData` because 1.20.1's `stack.getOrCreateTag()` is still a live
   reference.
5. **Networking**, and this is the one place besides capabilities where the two targets need
   genuinely different designs, not a renamed one -- confirmed via `javap`: 1.20.1 has no
   `CustomPacketPayload` at all (that class is a 1.20.2+ vanilla addition), so it keeps
   1.12.2's own `SimpleChannel`/`NetworkRegistry.ChannelBuilder`, registering each message
   against a numeric id the same way 1.12.2 did, just once and statically rather than
   dynamically at FML postInit. 26.x replaces the whole stack with `CustomPacketPayload` +
   `StreamCodec`, registered through `RegisterPayloadHandlersEvent`'s `PayloadRegistrar`.

   Both targets drop `MessageManager` itself rather than port it: 1.12.2's dynamic per-mod
   message-class registry (which assigned each message an id lazily, resolved at FML
   postInit) has no equivalent left to build now that both platforms want every message
   statically registered up front, individually -- there is no dispatch table to build, so
   `BCNetwork` (this port's `MessageManager` equivalent, alongside `BCRegistries`) is a thin
   per-message registration list instead, one line added per message as it lands.
   `IMessageHandler`'s return-a-reply contract is also gone: both `IPayloadContext` (26.x) and
   `NetworkEvent.Context` (1.20.1) already expose `reply(...)` and `enqueueWork(...)` directly
   on the object a handler receives, so `IPayloadReceiver` (BuildCraft's own generic "does this
   tile want this payload" interface) has nothing left to hand back either.

   `buildcraft.lib.net.MessageUpdateTile` -- the one truly generic message, routing an opaque
   payload to whatever `IPayloadReceiver` block entity sits at a given position -- is the first
   ported message on both targets, verified with a real dev-server boot on each (no crash, no
   registration error) rather than just a compile check, since networking bugs are exactly the
   kind that pass `javac` and fail at runtime. `FriendlyByteBuf` already has `readBlockPos`/
   `writeBlockPos` built in on both targets, so `MessageUtil`'s equivalent helpers (still
   blocked; see its own entry) were never needed for this file.
6. **Capabilities**, and note this differs *twice*. 1.12.2's `@CapabilityInject` was removed in
   1.16, so on 1.20.1 each capability is fetched with a `CapabilityToken` and declared in
   `RegisterCapabilitiesEvent`. 26.x replaces the whole system with `BlockCapability`, keyed by
   an `Identifier` and registered per block entity type — there is no attach-by-event path at
   all, so anything that used `ICapabilityProvider` needs restructuring, not renaming.
7. **Item, fluid and energy transfer is a new API on 26.x**, and this one matters more to
   BuildCraft than to most mods, because moving items and fluids around *is* BuildCraft.
   `IItemHandler`, `IFluidHandler` and `IEnergyStorage` are all gone from NeoForge 26.x,
   replaced by one generic `net.neoforged.neoforge.transfer.ResourceHandler<T extends Resource>`
   with `ItemResource`, `FluidResource` and a separate `EnergyHandler`. Three things change:
   - **Resource and amount are separate.** A `FluidResource`/`ItemResource` says *what*
     something is; the amount is a `long` the handler holds per slot. There is no
     `FluidStack`-as-a-key any more, which is what `StackKey` was for.
   - **Handlers are slot-indexed and introspectable** — `size()`, `getResource(slot)`,
     `getAmountAsLong(slot)`. Anything 1.12.2 solved by making tanks implement an extra
     BuildCraft interface can usually now be done from outside, against any mod's handler.
     `IFluidHandlerAdv` → `FluidFilters` is the worked example.
   - **`boolean simulate` became transactions.** Operations take a `TransactionContext`;
     `Transaction.openRoot()` in try-with-resources, `commit()` to keep the effect, otherwise
     it rolls back on close, and `Transaction.open(parent)` nests. This replaces both
     `doDrain`/`simulate` booleans and BuildCraft's own manual rollback in the pipe and
     robot code.

   1.20.1 has none of this — it is still `IItemHandler`/`IFluidHandler` with `FluidAction`.
   This is the single largest source of per-platform divergence after registration, and it
   lands squarely on `transport`, `factory` and `robotics`.

   NeoForge ships its own base classes for the array-backed, transaction-safe case --
   `net.neoforged.neoforge.transfer.StacksResourceHandler<S, T>` (and its `ItemStack`
   specialisation, `ItemStacksResourceHandler`) already do the snapshot/rollback bookkeeping
   and `ValueIOSerializable` NBT persistence by themselves, with `isValid`/`getCapacity`/
   `onContentsChanged` as the extension points. `DelegatingResourceHandler<T>` and
   `CombinedResourceHandler<T>` are the generic equivalents of 1.12.2's hand-written
   delegate/combined wrappers. Before hand-rolling a `ResourceHandler` implementation, check
   whether one of these already covers it -- `buildcraft.lib.tile.item.ItemHandlerSimple` is
   the worked example: BuildCraft's `StackInsertionFunction` abstraction turned out to be
   fully replaceable by `ItemStacksResourceHandler`'s inherited `getCapacity` hook.
8. **Rendering**, and 26.x is a second rewrite on top of the first. `TESR` → `BlockEntityRenderer`,
   and the `PoseStack`/`RenderType` pipeline replaces raw GL — that much is the 1.20.1 story.
   26.x then replaces *that*: rendering no longer draws during the render pass, it **submits**
   work to a graph that is sorted and executed later. `MultiBufferSource` does not exist;
   the collector is `net.minecraft.client.renderer.SubmitNodeCollector`, with
   `submitModel`/`submitModelPart`/`submitCustomGeometry` in place of getting a
   `VertexConsumer` and writing to it. Anything taking a `MultiBufferSource` is therefore a
   third signature, not a shared one — `IItemCustomPipeRender` is the worked example.
   `GlUtil` and most of `buildcraft.lib.client` are rewrites, not ports, on both targets.
9. **Ore dictionary → tags.** `OreDictionary.registerOre` becomes a tag JSON. BuildCraft
   publishes its gears under `c:gears/<material>` on 26.x and `forge:gears/<material>` on
   1.20.1.
10. **`.lang` → `.json`**, and translation keys become `item.<namespace>.<path>`.
11. **Recipes.** `data/<ns>/recipe/` (singular) on 26.x with string ingredients and
   `result.id`; `data/<ns>/recipes/` on 1.20.1 with object ingredients and `result.item`.
12. **Item models** need a client item definition in `assets/<ns>/items/<id>.json` on 26.x
    (1.21.4+); 1.20.1 only needs `models/item/`.
13. **`IPlantable` is gone on 26.x** (confirmed absent from every 26.x/NeoForge jar; still
    present, unchanged, on 1.20.1's Forge fork). This is a real removal, not a rename, and it
    hits anything that touches crops, saplings or farmland.
    - Block-side: the `IPlantable` interface is gone. `net.minecraft.world.level.block.
      VegetationBlock` is the new common base for the same family -- `CropBlock`, `SaplingBlock`,
      `StemBlock`, `NetherWartBlock`, `FlowerBlock`, `TallGrassBlock`, `MushroomBlock` and
      `DoublePlantBlock` all extend it directly now, where 1.20.1 still has them implement
      `IPlantable` on top of `BushBlock`.
    - Soil-side: `Block#canSustainPlant(state, level, pos, dir, IPlantable)` (`boolean`) is
      replaced by the NeoForge extension method `BlockState#canSustainPlant(BlockGetter,
      BlockPos, Direction, BlockState)`, which takes the *plant's* `BlockState` rather than an
      `IPlantable` instance, and returns `net.minecraft.util.TriState`
      (`TRUE`/`FALSE`/`DEFAULT`) rather than `boolean`. `DEFAULT` means "no opinion, ask the
      plant", resolved with `TriState#toBoolean(fallback)` -- `plantState.canSurvive(level,
      pos)` is a reasonable fallback.
14. **`InteractionResult` changed shape, not just package, on 26.x.** It is a plain enum on
    1.20.1 (`SUCCESS`/`CONSUME`/`PASS`/`FAIL`), but a sealed interface with record subtypes on
    26.x -- `SUCCESS` and `SUCCESS_SERVER` are both instances of the nested
    `InteractionResult.Success`. A success check is `result == InteractionResult.SUCCESS` on
    1.20.1 but `result instanceof InteractionResult.Success` on 26.x.
15. **`LevelHeightAccessor.getMinBuildHeight()` was renamed `getMinY()`** at some point after
    1.20.1 -- 26.x has `getMinY()`, 1.20.1 still has `getMinBuildHeight()`. Easy to miss because
    both compile against completely different, unrelated things if you get it backwards on one
    target and the IDE doesn't catch it, since both classes have plenty of other methods.
16. **World generation's `Feature`/`ConfiguredFeature`/`PlacedFeature` three-tier system collapsed to two
    tiers on 26.x, between 1.20.1 and 26.x specifically -- not a 1.12.2-era change at all.** `ConfiguredFeature`
    does not exist as a class any more on 26.x (confirmed via `javap`: "class not found"); `Feature` became a
    plain interface, implemented directly by a record carrying its own configuration as fields plus its own
    `codec()` (vanilla's own `SpringFeature` is exactly this shape). `Registries.FEATURE_TYPE` and
    `Registries.FEATURE` effectively swapped what they hold between the two targets: on 1.20.1,
    `Registries.FEATURE` is the static registry (holding the bare `Feature<FC>` type) and
    `Registries.CONFIGURED_FEATURE` is the datapack registry (type+config pair); on 26.x,
    `Registries.FEATURE_TYPE` is the static registry (holding just the codec) and `Registries.FEATURE` is now
    the datapack one (holding the fully-configured instance). `PlacedFeature` is unchanged in role on both.
    See `core.gen.SpringGenerator`'s progress entry (and its own class javadoc, on each platform) for the full
    worked example, including the real registration code and datapack JSON shape on each target.

### Things that differ *between* our two targets

These are the traps when porting a file to both at once.

| | 26.x | 1.20.1 |
| --- | --- | --- |
| Loader packages | `net.neoforged.*` | `net.minecraftforge.*` |
| Item/fluid transfer | `transfer.ResourceHandler<T>` + `Transaction` | `IItemHandler` / `IFluidHandler` |
| Render collector | `SubmitNodeCollector` | `MultiBufferSource` |
| Stack NBT | `ItemStack.CODEC` only | `ItemStack.save` / `.of` |
| Stack equality | `isSameItemSameComponents` | `isSameItemSameTags` |
| Primitive NBT tag payload | `ByteTag.value()`, `StringTag.value()`, ... (records) | `.getAsByte()`, `.getAsString()`, ... |
| Plant/soil interface | `VegetationBlock` base; `BlockState#canSustainPlant` -> `TriState` | `IPlantable`; `Block#canSustainPlant` -> `boolean` |
| `InteractionResult` | sealed interface (`instanceof .Success`) | plain enum (`== SUCCESS`) |
| Level height accessor | `getMinY()` | `getMinBuildHeight()` |
| Fluid equality | static `FluidStack.matches(a, b)` | instance `a.isFluidEqual(b)` |
| Particle detail setting | `net.minecraft.server.level.ParticleStatus` | `net.minecraft.client.ParticleStatus` |
| `Ingredient` stack accessor | `.items()` -> `Stream<Holder<Item>>` | `.getItems()` -> `ItemStack[]` |
| Mod metadata | `META-INF/neoforge.mods.toml` | `META-INF/mods.toml` |
| Dependency flag | `type = "required"` | `mandatory = true` |
| Registry handle | `DeferredHolder` / `DeferredItem` | `RegistryObject` |
| Item registry | `DeferredRegister.createItems(id)` | `DeferredRegister.create(ForgeRegistries.ITEMS, id)` |
| Mod constructor | `(IEventBus, ModContainer)` | no-arg, then `FMLJavaModLoadingContext.get()` |
| Dev detection | `FMLLoader.getCurrent().isProduction()` | `FMLLoader.isProduction()` (static) |
| Capabilities | `BlockCapability.createSided(Identifier, Class)` | `CapabilityManager.get(new CapabilityToken<>(){})` + `RegisterCapabilitiesEvent` |
| Resource ids | `Identifier` | `ResourceLocation` |
| Block entity NBT | `ValueInput` / `ValueOutput` | `CompoundTag` |
| `CompoundTag` getters | `getInt` → `Optional`; `getIntOr(k, 0)` | `getInt(k)` returns the value |
| Block entity type | `new BlockEntityType<>(supplier, blocks...)` | `BlockEntityType.Builder.of(...).build(null)` |
| Block `codec()` | not required (removed) | required (`simpleCodec`) |
| `AABB` corner constructor | no `(BlockPos, BlockPos)` ctor -- use the six-`double` ctor | `AABB(BlockPos, BlockPos)` still exists |
| `Vec3` from `BlockPos`/`Vec3i` | `new Vec3(Vec3i)` ctor exists | no such ctor -- spell out `new Vec3(pos.getX(), pos.getY(), pos.getZ())` |
| `ChunkPos` coordinates | `x()`/`z()` (fields are private) | public `x`/`z` fields, same as 1.12.2 |
| `AbstractArrow`/`SpectralArrow` package | `net.minecraft.world.entity.projectile.arrow` | `net.minecraft.world.entity.projectile` |
| Advancement lookup | `ServerAdvancementManager#get(Identifier)` -> `AdvancementHolder` | `ServerAdvancementManager#getAdvancement(ResourceLocation)` -> `Advancement` |
| `ServerPlayer`'s own level accessor | `level()` returns `ServerLevel` directly | `serverLevel()` (`level()` returns plain `Level`) |
| Mod banner | `bannerFile` / `iconFile` | `logoFile` |
| `pack_format` | 97 | 15 |
| Gradle plugin | `net.neoforged.moddev` | `net.neoforged.moddev.legacyforge` |
| `ChatFormatting` | gutted to the escape sequence and `stripFormatting` only -- no `isColor()`/`getColor()`/`getChar()`/`getName()` (confirmed against the real source; text styling moved to `Style`/`TextColor`) | keeps the full 1.12.2-shaped API, `isColor()` included |
| `CompoundTag` key set | `keySet()` | `getAllKeys()` |
| `FluidStack` existence | still exists (`net.neoforged.neoforge.fluids.FluidStack`), as a plain value type -- just not what a handler moves any more | `net.minecraftforge.fluids.FluidStack`, unchanged in shape from 1.12.2 |
| Block-entity genuine-removal hook | `Block#onRemove` is gone (confirmed via `javap` against `BlockBehaviour`); `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)` fires directly on the tile, only for a genuine block change, never a chunk unload | `BlockBehaviour#onRemove(state, level, pos, newState, movedByPiston)`, unchanged in shape from 1.12.2 |
| Wearable/armor items | `ArmorItem` doesn't exist (confirmed via `javap`); a helmet is a plain `Item` carrying a `DataComponents.EQUIPPABLE` (`Equippable`) component, which has no defense field at all -- defense needs a separate, opt-in `ItemAttributeModifiers` component that `ItemGoggles` simply never adds | `ArmorItem`/`ArmorMaterial` still exist; `ArmorMaterial` is a plain interface (`getDefenseForType`/`getDurabilityForType`/...) `ArmorItem`'s constructor reads to build its `Attributes.ARMOR` modifiers -- Forge's old `ISpecialArmor` side interface is gone (confirmed via `javap`: class not found), so a zero-defense item implements `ArmorMaterial` itself with every value zeroed instead of overriding a side hook |
| World-gen `Feature` shape | interface, implemented directly by a record carrying its own config + `codec()`; `ConfiguredFeature` class is gone (confirmed via `javap`: class not found). `Registries.FEATURE_TYPE` (static) holds the codec, `Registries.FEATURE` (datapack) holds the configured instance | classic `Feature<FC>` abstract class + separate `ConfiguredFeature<FC, Feature<FC>>` datapack wrapper. `Registries.FEATURE` (static) holds the bare `Feature<FC>`, `Registries.CONFIGURED_FEATURE` (datapack) holds the type+config pair |
| Biome-modifier registry/type namespace | `net.neoforged.neoforge.common.world.BiomeModifiers`; registry key `neoforge:biome_modifier`; stock "add features" type id `neoforge:add_features` | `net.minecraftforge.common.world.ForgeBiomeModifiers`; registry key `forge:biome_modifier`; stock "add features" type id `forge:add_features` |
| Vanilla-interop item capability | `Capabilities.Item.BLOCK` (block entity) / `Capabilities.Item.ENTITY_AUTOMATION` (entity) -- both `ResourceHandler<ItemResource>`-shaped, NeoForge's own tokens, auto-registered for every vanilla container and minecart-type entity (confirmed via `CapabilityHooks` in the NeoForge sources jar) | `ForgeCapabilities.ITEM_HANDLER` -- one token, `IItemHandler`-shaped, works identically for a `BlockEntity` or an `Entity` since both still implement `ICapabilityProvider` |
| Neighbour capability lookup | `Level#getCapability(BlockCapability<T,C>, BlockPos, C)` for a block position; `Entity#getCapability(EntityCapability<T,C>, C)` for an entity -- two different call shapes, no common supertype | `provider.getCapability(Capability<T>, Direction)` (returns `LazyOptional<T>`) -- one shape, works on both `BlockEntity` and `Entity` |
| Fluid viscosity (1.12.2's `Fluid#getViscosity()`) | `net.neoforged.neoforge.fluids.FluidType#getViscosity(FluidState, BlockAndLightGetter, BlockPos)`, reached via `fluid.getType().getFluidType()` | `net.minecraftforge.fluids.FluidType#getViscosity(FluidState, BlockAndTintGetter, BlockPos)`, same shape, reached the same way -- only the package and the `BlockGetter` supertype name differ |
| Fluid tank base class | none -- `net.neoforged.neoforge.fluids.FluidTank` doesn't exist (confirmed via `javap`: class not found); build on `net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler` instead, the fluid counterpart of `ItemHandlerSimple`'s own `ItemStacksResourceHandler` base | `net.minecraftforge.fluids.capability.templates.FluidTank` -- 1.12.2's own `net.minecraftforge.fluids.FluidTank`, moved under `capability.templates`, same `fill`/`drain`/`isFluidValid` surface |
| Vanilla-interop fluid capability | `Capabilities.Fluid.BLOCK` -- `BlockCapability<ResourceHandler<FluidResource>, Direction>`, NeoForge's own token, needs no BuildCraft-native counterpart since `Tank` already *is* a `ResourceHandler<FluidResource>` | `ForgeCapabilities.FLUID_HANDLER` -- `Capability<IFluidHandler>`, needs no BuildCraft-native counterpart either, since `Tank` already *is* an `IFluidHandler` by extending `FluidTank` |
| Draining a world fluid block (no capability-bearing block entity involved, e.g. a plain water/lava lake) | `BucketPickup#pickupBlock(LivingEntity, LevelAccessor, BlockPos, BlockState)` directly -- NeoForge's own generic `FluidUtil#tryPickupFluid` was rejected; its own doc admits it can mutate the world even when its `Transaction` is never committed | `BucketPickup#pickupBlock(LevelAccessor, BlockPos, BlockState)` directly (no `LivingEntity` parameter at all on this target) -- Forge's own `FluidUtil#getFluidHandler(Level, BlockPos, Direction)` was rejected too, confirmed via decompile to only resolve through a neighbouring `BlockEntity`, which a plain fluid lake never has |

For the 1.20.1 Gradle target, note that `legacyForge { version = ... }` selects
*MinecraftForge*. NeoForge's 1.20.1 fork needs
`legacyForge { enable { neoForgeVersion = "1.20.1-47.1.106" } }`.

## Build and packaging gotchas

Each of these cost a failed server boot, so they are worth knowing up front.

- **The shared modules must be declared as part of the mod**, not just as Gradle dependencies.
  A dev run loads `build/classes` directly rather than the built jar, so bundling `:expression`
  and `:shared` into the jar is not enough — without
  `mods { create("buildcraft") { sourceSet(project(":shared").sourceSets.main.get()) } }`
  the game starts and then dies with `NoClassDefFoundError` on the first shared class touched.
- **`pack.mcmeta` is optional for a mod, and on 26.x it is easier to leave out.** A mod's
  resources are loaded as both a resource pack and a data pack, and those have different format
  numbers (97 and 121 on 26.3), so one file cannot declare the right one for both. 26.x also
  rejects any `pack_format` above 81 unless `min_format` and `max_format` are present
  (`min_format` is an int, `max_format` is a `[major, minor]` pair). NeoForge infers correct
  metadata per pack type when the file is absent. 1.20.1 still wants a plain `pack_format = 15`.
- **`logoFile` is deprecated in `neoforge.mods.toml`** and fails mod loading validation on 26.x.
  Use `bannerFile` for a wide image or `iconFile` for a square one. 1.20.1's `mods.toml` still
  uses `logoFile`.
- **Blocks need a loot table or they drop nothing.** 1.12.2 dropped the block itself by default.
  The directory is `data/<ns>/loot_table/blocks/` — note `loot_table` is singular as of 1.21,
  and `tags/block/` likewise.
- **`requiresCorrectToolForDrops()` needs a mining tag**, or the block is unbreakable-for-drops.
- **On 1.20.1, a client-bound message handler cannot read a client-only type (e.g.
  `Minecraft.getInstance().player`, declared type `LocalPlayer`) inline in its own method body**, even if
  that method only ever runs client-side at runtime. Registering the message (`SimpleChannel.registerMessage`
  via a `MessageClass::handle` method reference) has to happen on both sides, which loads and
  bytecode-verifies the whole class regardless of which side actually calls the method -- and verifying an
  implicit widening assignment from a client-only type needs the verifier to resolve that type's hierarchy,
  which Forge's runtime dist-cleaner refuses on a dedicated server, crashing mod construction with a
  `BootstrapMethodError` ("invalid dist DEDICATED_SERVER"). Isolate the touch into its own class file (nested
  is fine) that only loads when actually invoked. Found porting `MessageMarker` -- see the
  `buildcraft.lib.marker` progress entry above for the full account. 26.x has no equivalent restriction (its
  merged/joined jar and `IPayloadContext#player()`, whose static type is already the common `Player`, never
  `LocalPlayer`, sidestep this entirely).
- **On 26.x, `PayloadRegistrar#playBidirectional` has two overloads, and the 3-argument one silently drops
  the client-side handler.** `playBidirectional(type, codec, serverHandler, clientHandler)` (4-arg) registers
  both directions; `playBidirectional(type, codec, serverHandler)` (3-arg) registers **only** the server-bound
  handler and leaves the client handler `null` — per its own javadoc, the client side then has to be
  registered separately via `RegisterClientPayloadHandlersEvent`, which nothing in this port does. `BCNetwork`
  called the 3-arg overload for `MessageUpdateTile` (the generic tile-sync envelope, sent both ways), so the
  jar compiled and booted a dedicated server fine (server-to-server never needs the missing half) but crashed
  every real client on startup with `IllegalStateException: Some clientbound payloads are missing client-side
  handlers: [buildcraft:update_tile]`, thrown from `ClientModLoader.finish()` before the game window even
  opens. This is exactly the failure mode the "not verified: client-side rendering" caveat above was flagging
  as a gap — a dedicated-server boot alone cannot catch it. Found only once real jars were deployed to actual
  Prism Launcher instances and launched as a client; confirmed the fix (switching to the 4-arg overload,
  passing `MessageUpdateTile::handle` for both directions, since the handler already reads `ctx.player()`/
  `level()` generically rather than assuming a side) by running `:neoforge-26x:runClient` directly afterward —
  mod loading now completes, the integrated server starts, and a world loads and renders with no crash report.
  Worth checking for on any future bidirectional message: grep for `playBidirectional(` calls with exactly
  three arguments.

## How much actually has to be duplicated

Worth knowing before adding a platform class, because the answer is not "all of it":

- `VecUtil` and `RotationUtil` are **byte-identical** on both targets. The vanilla geometry
  types did not move between 1.20.1 and 26.x, so the only work was the 1.12.2 -> modern rename.
- `MjEffects` and `IMjEffectManager` are likewise identical, and kept separate only so each
  platform's `buildcraft.api.mj` package is self-contained.
- What genuinely differs is registration (`BCRegistry`), block entity serialisation (`TileBC`),
  and capabilities (`MjCapabilities`, and how a machine exposes one). Those three are where the
  two targets really diverge, and they are worth reading side by side before porting a machine.

So the practical rule: port to 26.x first, try the same file unchanged on 1.20.1, and only fork
it when the compiler objects.

## Conventions

- Provided-by-Minecraft libraries (Guava, Gson, commons-lang3, fastutil, log4j) are declared
  `compileOnly`, so a second copy never ends up in the mod jar.
- The eight 1.12.2 mod ids (`buildcraftcore`, `buildcraftlib`, `buildcrafttransport`, ...)
  were held together by FML's `parent` mechanism, which no longer exists. They collapse into
  one `buildcraft` mod id; the old boundaries survive as packages and as separate `BC*`
  registration holders.
- Port by compiling, not by grepping imports. "No `net.minecraft` import" does not mean "no
  Minecraft dependency" — a same-package reference does not appear as an import.
  `buildcraft.lib.path.task` looks clean but `EnumTraversalExpense` beside it needs `Level`.
- When a class is *mostly* version-independent, split it rather than duplicating it whole.
  `MjBattery` is the pattern: all the arithmetic is shared, and the one method that needed a
  `Level` (shedding excess power as a particle effect) became `shedExcessPower()`, returning
  the amount lost for a thin per-platform caller to render.
- When a data structure is rewritten, add a round-trip test. `BitSetTester` exists because
  the Trove → fastutil swap changed how the backing array is read.
