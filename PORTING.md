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
- Both platforms — mod entrypoint, the five gears, the creative tab, `MjCapabilities`,
  `IMjEffectManager`/`MjEffects`. `VecUtil` and `RotationUtil` are 26.x only so far.

Remaining, in the order they should be tackled — each module needs the one above it:

| Module | Files | Notes |
| --- | --- | --- |
| `BuildCraftAPI/api` | 251 | Needed by everything. Port alongside `lib`. |
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
   stored on a stack needs a `DataComponentType`. Block entities still use NBT.
4. **Networking.** The 1.12 `IMessage`/`SimpleNetworkWrapper` stack becomes
   `CustomPacketPayload` with a `StreamCodec` and explicit registration.
5. **Capabilities**, and note this differs *twice*. 1.12.2's `@CapabilityInject` was removed in
   1.16, so on 1.20.1 each capability is fetched with a `CapabilityToken` and declared in
   `RegisterCapabilitiesEvent`. 26.x replaces the whole system with `BlockCapability`, keyed by
   an `Identifier` and registered per block entity type — there is no attach-by-event path at
   all, so anything that used `ICapabilityProvider` needs restructuring, not renaming.
6. **Rendering.** `TESR` → `BlockEntityRenderer`, and the whole `PoseStack`/`RenderType`
   pipeline replaces raw GL. `GlUtil` and most of `buildcraft.lib.client` are rewrites, not
   ports.
7. **Ore dictionary → tags.** `OreDictionary.registerOre` becomes a tag JSON. BuildCraft
   publishes its gears under `c:gears/<material>` on 26.x and `forge:gears/<material>` on
   1.20.1.
8. **`.lang` → `.json`**, and translation keys become `item.<namespace>.<path>`.
9. **Recipes.** `data/<ns>/recipe/` (singular) on 26.x with string ingredients and
   `result.id`; `data/<ns>/recipes/` on 1.20.1 with object ingredients and `result.item`.
10. **Item models** need a client item definition in `assets/<ns>/items/<id>.json` on 26.x
    (1.21.4+); 1.20.1 only needs `models/item/`.

### Things that differ *between* our two targets

These are the traps when porting a file to both at once.

| | 26.x | 1.20.1 |
| --- | --- | --- |
| Loader packages | `net.neoforged.*` | `net.minecraftforge.*` |
| Mod metadata | `META-INF/neoforge.mods.toml` | `META-INF/mods.toml` |
| Dependency flag | `type = "required"` | `mandatory = true` |
| Registry handle | `DeferredHolder` / `DeferredItem` | `RegistryObject` |
| Item registry | `DeferredRegister.createItems(id)` | `DeferredRegister.create(ForgeRegistries.ITEMS, id)` |
| Mod constructor | `(IEventBus, ModContainer)` | no-arg, then `FMLJavaModLoadingContext.get()` |
| Dev detection | `FMLLoader.getCurrent().isProduction()` | `FMLLoader.isProduction()` (static) |
| Capabilities | `BlockCapability.createSided(Identifier, Class)` | `CapabilityManager.get(new CapabilityToken<>(){})` + `RegisterCapabilitiesEvent` |
| Resource ids | `Identifier` | `ResourceLocation` |
| `pack_format` | 97 | 15 |
| Gradle plugin | `net.neoforged.moddev` | `net.neoforged.moddev.legacyforge` |

For the 1.20.1 Gradle target, note that `legacyForge { version = ... }` selects
*MinecraftForge*. NeoForge's 1.20.1 fork needs
`legacyForge { enable { neoForgeVersion = "1.20.1-47.1.106" } }`.

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
