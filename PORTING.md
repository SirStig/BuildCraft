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
- `buildcraft.api.core` — all 28 files. Seven are Minecraft-free and live in `:shared`;
  of the fifteen platform ones, eleven are byte-identical on both targets.
- `buildcraft.api.tiles`, `buildcraft.api.blocks`, `buildcraft.api.power` — all three
  packages. `CapabilitiesHelper` is deliberately dropped (see below).
- `buildcraft.api.enums` (7 of 10) and `buildcraft.api.properties` — every file
  byte-identical on both targets.

Deliberately not ported, with reasons:

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

**Both targets are verified by booting a server**, not just by compiling. That matters: every
bug in the "Build and packaging gotchas" section below compiled cleanly and only showed up at
runtime. Re-run `./gradlew :neoforge-26x:runServer` (and the 1.20.1 equivalent) after any
registration change.

Not verified: client-side rendering. Checking that the models actually draw needs a display,
so treat the blockstate/model JSON as unconfirmed until someone runs `runClient`.

Remaining, in the order they should be tackled — each module needs the one above it:

| Module | Files | Notes |
| --- | --- | --- |
| `BuildCraftAPI/api` | ~210 left of 251 | Needed by everything. Port alongside `lib`. The big remaining packages are `transport` (54), `statements` (26), `robots` (13) and `recipes` (10). |
| `buildcraft.lib` | 541 | The foundation: tiles, GUI, networking, models, MJ power. |
| `buildcraft.core` | 85 | Gears (done), wrench, markers, engines, map location. |
| `buildcraft.transport` | 124 | Pipes. The largest single feature. |
| `buildcraft.builders` | 121 | Quarry, builder, architect, filler, schematics. |
| `buildcraft.silicon` | 79 | Laser, assembly table, gates/wires. |
| `buildcraft.factory` | 49 | Pump, mining well, tank, autoworkbench. |
| `buildcraft.energy` | 41 | Combustion/stirling engines, oil, fuel. |
| `buildcraft.robotics` | 24 | Robots, zone planner. |

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

`BlockPos` no longer has a double constructor at all, so anything that rounded a different
way — `VecUtil.convertCeiling` for instance — has to cast explicitly.

### Structural changes, in rough order of pain

1. **Block metadata is gone** (1.13). Every `IBlockState`/metadata pair becomes a real
   blockstate property. This is the single biggest source of work in `transport` and
   `builders`.
2. **Registration.** BuildCraft's `RegistrationHelper` + FML `preInit` becomes
   `DeferredRegister` on the mod event bus. On 26.x the registry object carries its own id,
   so items no longer need an unlocalised-name string threaded through the constructor.
3. **NBT → data components** (1.20.5). Item NBT does not exist on 26.x; anything BuildCraft
   stored on a stack needs a `DataComponentType`. Block entities still use NBT, but on 26.x they
   read and write it through `ValueInput`/`ValueOutput` (`getLongOr(name, default)`,
   `putLong(name, value)`) rather than `CompoundTag`, so the hooks are `loadAdditional` and
   `saveAdditional`. 1.20.1 still uses `CompoundTag`.
4. **Networking.** The 1.12 `IMessage`/`SimpleNetworkWrapper` stack becomes
   `CustomPacketPayload` with a `StreamCodec` and explicit registration.
5. **Capabilities**, and note this differs *twice*. 1.12.2's `@CapabilityInject` was removed in
   1.16, so on 1.20.1 each capability is fetched with a `CapabilityToken` and declared in
   `RegisterCapabilitiesEvent`. 26.x replaces the whole system with `BlockCapability`, keyed by
   an `Identifier` and registered per block entity type — there is no attach-by-event path at
   all, so anything that used `ICapabilityProvider` needs restructuring, not renaming.
6. **Item, fluid and energy transfer is a new API on 26.x**, and this one matters more to
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
7. **Rendering**, and 26.x is a second rewrite on top of the first. `TESR` → `BlockEntityRenderer`,
   and the `PoseStack`/`RenderType` pipeline replaces raw GL — that much is the 1.20.1 story.
   26.x then replaces *that*: rendering no longer draws during the render pass, it **submits**
   work to a graph that is sorted and executed later. `MultiBufferSource` does not exist;
   the collector is `net.minecraft.client.renderer.SubmitNodeCollector`, with
   `submitModel`/`submitModelPart`/`submitCustomGeometry` in place of getting a
   `VertexConsumer` and writing to it. Anything taking a `MultiBufferSource` is therefore a
   third signature, not a shared one — `IItemCustomPipeRender` is the worked example.
   `GlUtil` and most of `buildcraft.lib.client` are rewrites, not ports, on both targets.
8. **Ore dictionary → tags.** `OreDictionary.registerOre` becomes a tag JSON. BuildCraft
   publishes its gears under `c:gears/<material>` on 26.x and `forge:gears/<material>` on
   1.20.1.
9. **`.lang` → `.json`**, and translation keys become `item.<namespace>.<path>`.
10. **Recipes.** `data/<ns>/recipe/` (singular) on 26.x with string ingredients and
   `result.id`; `data/<ns>/recipes/` on 1.20.1 with object ingredients and `result.item`.
11. **Item models** need a client item definition in `assets/<ns>/items/<id>.json` on 26.x
    (1.21.4+); 1.20.1 only needs `models/item/`.

### Things that differ *between* our two targets

These are the traps when porting a file to both at once.

| | 26.x | 1.20.1 |
| --- | --- | --- |
| Loader packages | `net.neoforged.*` | `net.minecraftforge.*` |
| Item/fluid transfer | `transfer.ResourceHandler<T>` + `Transaction` | `IItemHandler` / `IFluidHandler` |
| Render collector | `SubmitNodeCollector` | `MultiBufferSource` |
| Stack NBT | `ItemStack.CODEC` only | `ItemStack.save` / `.of` |
| Stack equality | `isSameItemSameComponents` | `isSameItemSameTags` |
| Mod metadata | `META-INF/neoforge.mods.toml` | `META-INF/mods.toml` |
| Dependency flag | `type = "required"` | `mandatory = true` |
| Registry handle | `DeferredHolder` / `DeferredItem` | `RegistryObject` |
| Item registry | `DeferredRegister.createItems(id)` | `DeferredRegister.create(ForgeRegistries.ITEMS, id)` |
| Mod constructor | `(IEventBus, ModContainer)` | no-arg, then `FMLJavaModLoadingContext.get()` |
| Dev detection | `FMLLoader.getCurrent().isProduction()` | `FMLLoader.isProduction()` (static) |
| Capabilities | `BlockCapability.createSided(Identifier, Class)` | `CapabilityManager.get(new CapabilityToken<>(){})` + `RegisterCapabilitiesEvent` |
| Resource ids | `Identifier` | `ResourceLocation` |
| Block entity NBT | `ValueInput` / `ValueOutput` | `CompoundTag` |
| Block entity type | `new BlockEntityType<>(supplier, blocks...)` | `BlockEntityType.Builder.of(...).build(null)` |
| Block `codec()` | not required (removed) | required (`simpleCodec`) |
| Mod banner | `bannerFile` / `iconFile` | `logoFile` |
| `pack_format` | 97 | 15 |
| Gradle plugin | `net.neoforged.moddev` | `net.neoforged.moddev.legacyforge` |

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
